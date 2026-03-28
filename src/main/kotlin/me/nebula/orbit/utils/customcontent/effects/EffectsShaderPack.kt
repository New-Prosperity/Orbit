package me.nebula.orbit.utils.customcontent.effects

import me.nebula.ether.utils.logging.logger

object EffectsShaderPack {

    private val logger = logger("EffectsShaderPack")

    private const val SHADER_RESOURCE_BASE = "shaders/effects"

    private val STATIC_FILES = mapOf(
        "assets/minecraft/shaders/core/particle.vsh" to "core/particle.vsh",
        "assets/minecraft/shaders/core/particle.fsh" to "core/particle.fsh",
        "assets/minecraft/shaders/core/rendertype_crumbling.vsh" to "core/rendertype_crumbling.vsh",
        "assets/minecraft/shaders/core/rendertype_crumbling.fsh" to "core/rendertype_crumbling.fsh",
    )

    fun generate(): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()

        for ((packPath, resourcePath) in STATIC_FILES) {
            val bytes = readResource("$SHADER_RESOURCE_BASE/$resourcePath")
            if (bytes != null) {
                entries[packPath] = bytes
            } else {
                logger.warn { "Missing effects shader resource: $resourcePath" }
            }
        }

        logger.info { "Generated effects shader pack: ${entries.size} entries" }
        return entries
    }

    private fun readResource(path: String): ByteArray? =
        Thread.currentThread().contextClassLoader
            .getResourceAsStream(path)
            ?.use { it.readAllBytes() }
}
