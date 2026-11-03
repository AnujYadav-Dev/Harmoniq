package dev.anujyadav.harmoniq.services.globalsearch

// real harmoniq-server v0.2.0 responses, captured from a local server run
object Fixtures {
    fun read(name: String): String {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("globalsearch/$name")) {
            "missing fixture $name"
        }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
