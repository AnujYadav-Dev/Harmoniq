package dev.anujyadav.harmoniq.utils

import java.util.concurrent.ConcurrentHashMap

sealed class SimpleFileSystem(val parent: Folder?, val name: String) {
    val fullPath
        get(): SimplePath {
            val parts = mutableListOf(name)
            var currentParent = parent
            while (currentParent != null) {
                parts.add(0, currentParent.name)
                currentParent = currentParent.parent
            }
            return SimplePath(parts)
        }

    class File(
        parent: Folder? = null,
        name: String,
        var data: Any? = null,
    ) : SimpleFileSystem(parent, name)

    class Folder(
        parent: Folder? = null,
        name: String = "root",
        var children: ConcurrentHashMap<String, SimpleFileSystem> = ConcurrentHashMap(),
    ) : SimpleFileSystem(parent, name) {
        val isEmpty get() = children.isEmpty()
        val childFoldersCount get() = children.values.count { it is Folder }

        /**
         * The file at [path], created along with its parent folders when missing.
         * Safe to call concurrently, scans add sibling files in parallel.
         */
        fun ensureChildFile(path: SimplePath): File {
            var folder = this
            for (x in path.parts.dropLast(1)) {
                val current = folder
                folder = current.children.getOrPut(x) { Folder(current, x) } as? Folder
                    ?: throw Exception("Child '$x' is not a folder")
            }
            val name = path.parts.last()
            val current = folder
            return current.children.getOrPut(name) { File(current, name) } as? File
                ?: throw Exception("Child '$name' is not a file")
        }

        /**
         * Removes the file at [path] if [predicate] allows it, along with the folders
         * this leaves empty. Does nothing when there is no such file.
         */
        fun removeChildFile(path: SimplePath, predicate: (File) -> Boolean = { true }) {
            var folder = this
            for (x in path.parts.dropLast(1)) {
                folder = folder.children[x] as? Folder ?: return
            }
            val name = path.parts.last()
            val file = folder.children[name] as? File ?: return
            if (!predicate(file)) {
                return
            }
            folder.children.remove(name)
            while (folder.isEmpty) {
                val parent = folder.parent ?: return
                parent.children.remove(folder.name)
                folder = parent
            }
        }
    }
}
