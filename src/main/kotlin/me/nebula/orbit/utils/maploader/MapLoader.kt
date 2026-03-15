package me.nebula.orbit.utils.maploader

import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.storage.StorageScope
import me.nebula.gravity.map.MapStore
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

object MapLoader {

    private val logger = logger("MapLoader")
    private val cacheDir = Path.of("data/maps")

    fun load(mapName: String, storage: StorageScope): Path {
        val entry = MapStore.load(mapName)
            ?: error("No map entry found for '$mapName' in MapStore")

        val targetDir = cacheDir.resolve(entry.checksum)

        if (Files.isDirectory(targetDir) && Files.list(targetDir).use { it.findAny().isPresent }) {
            val regionDir = targetDir.resolve("region")
            if (!Files.isDirectory(regionDir)) {
                logger.warn { "Cached map '${entry.name}' is corrupted (no region/ dir), re-downloading..." }
                targetDir.toFile().deleteRecursively()
            } else {
                logger.info { "Map '${entry.name}' found in cache (checksum=${entry.checksum})" }
                return targetDir
            }
        }

        logger.info { "Downloading map '${entry.name}' (path=${entry.minioKey})..." }
        val bytes = storage.download(entry.minioKey)

        Files.createDirectories(targetDir)
        extractZip(bytes, targetDir)

        logger.info { "Map '${entry.name}' extracted to $targetDir (${bytes.size / 1024}KB)" }
        return targetDir
    }

    fun loadRandom(gameMode: String, maps: List<String>, storage: StorageScope): Path {
        require(maps.isNotEmpty()) { "No maps configured for gameMode=$gameMode" }
        val mapName = maps.random()
        logger.info { "Selected random map '$mapName' for $gameMode" }
        return load(mapName, storage)
    }

    private fun extractZip(bytes: ByteArray, targetDir: Path) {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            generateSequence { zis.nextEntry }.forEach { entry ->
                val path = targetDir.resolve(entry.name).normalize()
                require(path.startsWith(targetDir)) { "Zip slip detected: ${entry.name}" }
                if (entry.isDirectory) {
                    Files.createDirectories(path)
                } else {
                    path.parent?.let { Files.createDirectories(it) }
                    Files.copy(zis, path, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }
}
