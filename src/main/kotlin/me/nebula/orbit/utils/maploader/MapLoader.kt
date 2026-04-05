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
        val nebulaFile = resolveNebulaFile(*parts)
        if (nebulaFile != null) return nebulaFile

        val source = parts.fold(mountDir) { path, part -> path.resolve(part) }
        require(Files.isDirectory(source)) { "World '${parts.joinToString("/")}' not found (no .nebula file or directory at $source)" }
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

    private fun resolveNebulaFile(vararg parts: String): Path? {
        val nebulaPath = parts.fold(mountDir) { path, part -> path.resolve(part) }
            .resolveSibling("${parts.last()}.nebula")
        if (Files.isRegularFile(nebulaPath)) {
            logger.info { "Resolved .nebula world: $nebulaPath" }
            return nebulaPath
        }
        val directNebula = mountDir.resolve("${parts.joinToString("/")}.nebula")
        if (Files.isRegularFile(directNebula)) {
            logger.info { "Resolved .nebula world: $directNebula" }
            return directNebula
        }
        return null
    }

    fun isNebulaFile(path: Path): Boolean =
        path.toString().endsWith(".nebula") && Files.isRegularFile(path)

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
