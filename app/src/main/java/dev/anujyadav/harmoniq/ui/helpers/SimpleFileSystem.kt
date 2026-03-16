package dev.anujyadav.harmoniq.ui.helpers

import dev.anujyadav.harmoniq.utils.SimpleFileSystem
import dev.anujyadav.harmoniq.utils.SimplePath

fun SimpleFileSystem.Folder.navigateToFolder(path: SimplePath): SimpleFileSystem.Folder? {
    var folder: SimpleFileSystem.Folder? = this
    path.parts.forEach { x ->
        folder = folder?.let {
            val child = it.children[x]
            child as? SimpleFileSystem.Folder
        }
    }
    return folder
}
