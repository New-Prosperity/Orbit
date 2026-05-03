package me.nebula.orbit.utils.customcontent

import me.nebula.ether.utils.resource.resourceManager
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

object StandalonePackBuilder {

    @JvmStatic
    fun main(args: Array<String>) {
        val resourcesArg = args.firstNotNullOfOrNull { arg ->
            arg.removePrefix("--resources=").takeIf { arg.startsWith("--resources=") }
        }
        val outputArg = args.firstNotNullOfOrNull { arg ->
            arg.removePrefix("--output=").takeIf { arg.startsWith("--output=") }
        }

        val resourcesDir = resourcesArg?.let { Paths.get(it) }
            ?: Paths.get("Orbit", "data").toAbsolutePath().normalize()
        val outputPath = outputArg?.let { Paths.get(it) }
            ?: Paths.get("Orbit", "build", "standalone-pack", "pack.zip").toAbsolutePath().normalize()

        println("[StandalonePackBuilder] Resources: $resourcesDir")
        println("[StandalonePackBuilder] Output:    $outputPath")

        val resources = resourceManager {
            dataDirectory = resourcesDir
        }

        CustomContentRegistry.loadResourcesOnly(resources)
        val result = CustomContentRegistry.mergePack(forceRegenerate = true)

        outputPath.parent?.createDirectories()
        outputPath.writeBytes(result.packBytes)

        println("[StandalonePackBuilder] Wrote ${result.packBytes.size / 1024}KB pack (sha1=${result.sha1})")
        println("[StandalonePackBuilder] Drop into .minecraft/resourcepacks/ and F3+T to reload.")
    }
}
