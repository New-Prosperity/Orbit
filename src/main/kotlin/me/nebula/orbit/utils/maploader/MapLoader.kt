package me.nebula.orbit.utils.maploader

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.nebulaworld.NebulaWorldConverter
import me.nebula.orbit.utils.nebulaworld.NebulaWorldReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.readBytes

object MapLoader {

    private val logger = logger("MapLoader")
    internal var mountDir: Path = Path.of("maps")
        private set
    internal var worldsDir: Path = Path.of("worlds")
        private set

    internal fun overrideMountDirForTest(path: Path) { mountDir = path }
    internal fun resetMountDirForTest() { mountDir = Path.of("maps") }
    internal fun overrideWorldsDirForTest(path: Path) { worldsDir = path }
    internal fun resetWorldsDirForTest() { worldsDir = Path.of("worlds") }

    fun resolve(vararg parts: String): Path {
        ingestAnvilIfPresent(*parts)
        return resolveNebulaFile(*parts)
            ?: error("World '${parts.joinToString("/")}' not found — expected a .nebula file under ${worldsDir.toAbsolutePath()}")
    }

    fun isNebulaFile(path: Path): Boolean =
        path.toString().endsWith(".nebula") && Files.isRegularFile(path)

    fun ingestAnvilIfPresent(vararg parts: String): Path? {
        val anvilDir = parts.fold(mountDir) { path, part -> path.resolve(part) }
        if (!Files.isDirectory(anvilDir)) return null
        if (!isAnvilWorld(anvilDir)) return null

        Files.createDirectories(worldsDir)
        val target = worldsDir.resolve("${parts.last()}.nebula")
        if (Files.isRegularFile(target)) {
            runCatching { verifyConverted(target) }.onFailure { ex ->
                throw IllegalArgumentException(
                    "Cannot ingest $anvilDir: a file already exists at $target but is not a valid .nebula — resolve manually",
                    ex,
                )
            }
            logger.info { "Skipping $anvilDir: already converted to $target" }
            return null
        }

        val workingDir = parts.fold(worldsDir) { path, part -> path.resolve(part) }
        if (Files.exists(workingDir)) {
            logger.info { "Clearing stale working copy at $workingDir" }
            workingDir.toFile().deleteRecursively()
        }

        val sourceBytes = directorySizeBytes(anvilDir)
        logger.info { "Ingesting $anvilDir → $workingDir → $target (${sourceBytes / 1024}KB source)" }

        try {
            copyTree(anvilDir, workingDir)
            NebulaWorldConverter.convert(workingDir, target)
            verifyConverted(target)
        } catch (ex: Throwable) {
            Files.deleteIfExists(target)
            workingDir.toFile().deleteRecursively()
            throw IllegalStateException("Failed to ingest $anvilDir; source preserved at $anvilDir, working copy cleared", ex)
        }

        val convertedBytes = Files.size(target)
        val ratio = if (convertedBytes > 0) sourceBytes.toDouble() / convertedBytes.toDouble() else 0.0
        logger.info { "Converted → ${convertedBytes / 1024}KB (%.2fx compression). Source preserved at $anvilDir; removing working copy $workingDir.".format(ratio) }

        workingDir.toFile().deleteRecursively()
        return target
    }

    private fun copyTree(src: Path, dst: Path) {
        Files.createDirectories(dst)
        Files.walk(src).use { stream ->
            for (srcPath in stream) {
                if (srcPath == src) continue
                val rel = src.relativize(srcPath)
                val dstPath = dst.resolve(rel.toString())
                if (Files.isDirectory(srcPath)) {
                    Files.createDirectories(dstPath)
                } else {
                    dstPath.parent?.let { Files.createDirectories(it) }
                    Files.copy(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    fun scanAndIngest(): Int {
        if (!Files.isDirectory(mountDir)) return 0
        var ingested = 0
        Files.newDirectoryStream(mountDir).use { stream ->
            for (entry in stream) {
                if (!Files.isDirectory(entry)) continue
                if (!isAnvilWorld(entry)) continue
                if (ingestAnvilIfPresent(entry.fileName.toString()) != null) ingested++
            }
        }
        if (ingested > 0) logger.info { "Boot scan ingested $ingested Anvil world(s) from ${mountDir.toAbsolutePath()}" }
        return ingested
    }

    private fun resolveNebulaFile(vararg parts: String): Path? {
        val candidates = listOf(
            worldsDir.resolve("${parts.joinToString("/")}.nebula"),
            worldsDir.resolve("${parts.last()}.nebula"),
        )
        for (candidate in candidates) {
            if (Files.isRegularFile(candidate)) {
                logger.info { "Resolved .nebula world: $candidate" }
                return candidate
            }
        }
        return null
    }

    private fun isAnvilWorld(path: Path): Boolean =
        Files.isDirectory(path.resolve("dimensions/minecraft/overworld/region"))

    private fun verifyConverted(path: Path) {
        val bytes = path.readBytes()
        require(bytes.size >= 8) { "Converted file is empty or truncated: $path" }
        NebulaWorldReader.read(bytes)
    }

    private fun directorySizeBytes(dir: Path): Long {
        var total = 0L
        Files.walk(dir).use { stream ->
            stream.forEach { p ->
                if (Files.isRegularFile(p)) total += Files.size(p)
            }
        }
        return total
    }
}
