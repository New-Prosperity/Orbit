package me.nebula.orbit.utils.statue

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.generator.ModelGenerator
import java.nio.file.Files
import java.nio.file.Path

private val logger = logger("PlayerModelGenerator")

private const val BBMODEL_PATH = "models/player_statue.bbmodel"

object PlayerModelGenerator {

    const val BLUEPRINT_NAME = "player_statue"

    fun registerBlueprint() {
        if (isRegistered()) {
            logger.info { "Blueprint '$BLUEPRINT_NAME' already registered, skipping" }
            return
        }

        val resources = Orbit.app.resources
        if (resources.exists(BBMODEL_PATH)) {
            runCatching {
                val raw = ModelGenerator.generateRaw(resources, "player_statue.bbmodel")
                ModelEngine.registerRaw(raw.blueprint.name, raw)
                logger.info { "Loaded player statue blueprint from file" }
                return
            }.onFailure {
                logger.warn { "Failed to load $BBMODEL_PATH, falling back to generation: ${it.message}" }
            }
        }

        runCatching {
            val model = buildPlayerModel()

            runCatching {
                resources.writeText(BBMODEL_PATH, exportBbmodelJson(model))
                logger.info { "Auto-exported $BBMODEL_PATH for designer editing" }
            }.onFailure {
                logger.warn { "Failed to auto-export $BBMODEL_PATH: ${it.message}" }
            }

            val raw = ModelGenerator.generateRaw(model)
            ModelEngine.registerRaw(BLUEPRINT_NAME, raw)
            logger.info { "Registered player statue blueprint (generated)" }
        }.onFailure {
            logger.warn { "Failed to register player statue blueprint: ${it.message}" }
        }
    }

    fun isRegistered(): Boolean = ModelEngine.blueprintOrNull(BLUEPRINT_NAME) != null

    fun exportToFile(outputPath: Path) {
        val model = buildPlayerModel()
        val json = exportBbmodelJson(model)
        Files.createDirectories(outputPath.parent)
        Files.writeString(outputPath, json)
    }
}
