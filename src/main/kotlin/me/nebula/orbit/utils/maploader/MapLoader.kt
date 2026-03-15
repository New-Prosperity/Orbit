package me.nebula.orbit.utils.maploader

import me.nebula.ether.utils.logging.logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object MapLoader {

    private val logger = logger("MapLoader")
    private val mountDir = Path.of("/maps")
    private val workDir = Path.of("data/worlds")
    private var counter = 0

    fun resolve(name: String): Path {
        val source = mountDir.resolve(name)
        require(Files.isDirectory(source)) { "World '$name' not found on mount at $source" }
        val copy = workDir.resolve("$name-${counter++}")
        logger.info { "Cloning world '$name' → $copy" }
        copyDirectory(source, copy)
        return copy
    }

    private fun copyDirectory(source: Path, target: Path) {
        Files.walk(source).use { stream ->
            stream.forEach { src ->
                val dest = target.resolve(source.relativize(src))
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest)
                } else {
                    dest.parent?.let { Files.createDirectories(it) }
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }
}
