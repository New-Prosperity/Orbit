package me.nebula.orbit.utils.maploader

import me.nebula.ether.utils.logging.logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object MapLoader {

    private val logger = logger("MapLoader")
    private val mountDir = Path.of("maps")
    private val workDir = Path.of("worlds")

    fun resolve(vararg parts: String): Path {
        val source = parts.fold(mountDir) { path, part -> path.resolve(part) }
        require(Files.isDirectory(source)) { "World '${parts.joinToString("/")}' not found on mount at $source" }
        val name = parts.last()
        val copy = workDir.resolve(name)
        if (Files.isDirectory(copy)) {
            logger.info { "Working copy '$name' already exists, deleting..." }
            copy.toFile().deleteRecursively()
        }
        logger.info { "Cloning world '${parts.joinToString("/")}' → $copy" }
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
