package me.nebula.orbit.utils.commandbuilder

import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.mode.game.GamePhase
import me.nebula.orbit.utils.gametest.GameTestRunner
import me.nebula.orbit.utils.customcontent.armor.CustomArmorRegistry
import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import me.nebula.orbit.utils.customcontent.furniture.FurnitureRegistry
import me.nebula.orbit.utils.customcontent.item.CustomItemRegistry
import me.nebula.orbit.utils.fakeplayer.BotBehavior
import me.nebula.orbit.utils.gametest.GameTestRegistry
import me.nebula.orbit.utils.gametest.TestBotPresets
import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.statue.StatueManager

private fun matching(candidates: Collection<String>, partial: String): List<String> =
    if (partial.isBlank()) candidates.toList()
    else candidates.filter { it.startsWith(partial, ignoreCase = true) }

fun CommandBuilderDsl.blueprintArgument(name: String = "blueprint") {
    wordArgument(name) { matching(ModelEngine.blueprints().keys, partial) }
}

fun CommandBuilderDsl.animationArgument(name: String = "animation", blueprintArgName: String = "blueprint") {
    wordArgument(name) {
        val blueprintName = priorArg(blueprintArgName)
        val blueprint = blueprintName?.let { ModelEngine.blueprintOrNull(it) }
        matching(blueprint?.animations?.keys ?: emptySet(), partial)
    }
}

fun CommandBuilderDsl.furnitureArgument(name: String = "id") {
    wordArgument(name) { matching(FurnitureRegistry.all().map { it.id }, partial) }
}

fun CommandBuilderDsl.customItemArgument(name: String = "id") {
    wordArgument(name) { matching(CustomItemRegistry.all().map { it.id }, partial) }
}

fun CommandBuilderDsl.customBlockArgument(name: String = "id") {
    wordArgument(name) { matching(CustomBlockRegistry.all().map { it.id }, partial) }
}

fun CommandBuilderDsl.customArmorArgument(name: String = "armor_id") {
    wordArgument(name) { matching(CustomArmorRegistry.all().map { it.id }, partial) }
}

fun CommandBuilderDsl.customContentArgument(name: String = "id") {
    wordArgument(name) {
        val all = buildSet {
            addAll(CustomItemRegistry.all().map { it.id })
            addAll(CustomBlockRegistry.all().map { it.id })
            addAll(CustomArmorRegistry.all().map { it.id })
            addAll(FurnitureRegistry.all().map { it.id })
        }
        matching(all, partial)
    }
}

fun CommandBuilderDsl.statueArgument(name: String = "id") {
    wordArgument(name) { matching(StatueManager.all().map { it.id }, partial) }
}

fun CommandBuilderDsl.statueAnimationArgument(name: String = "animation", statueArgName: String = "id") {
    wordArgument(name) {
        val statueId = priorArg(statueArgName)
        val statue = statueId?.let { id -> StatueManager.all().firstOrNull { it.id == id } }
        val animations = statue?.modelOwner?.modeledEntity?.models?.values?.firstOrNull()
            ?.blueprint?.animations?.keys
            ?: ModelEngine.blueprints().values.flatMap { it.animations.keys }.toSet()
        matching(animations, partial)
    }
}

fun CommandBuilderDsl.modelAnimationArgument(name: String = "animation", blueprintArgName: String = "blueprint") {
    wordArgument(name) {
        val blueprintName = priorArg(blueprintArgName)
        val blueprint = blueprintName?.let { ModelEngine.blueprintOrNull(it) }
        val animations = blueprint?.animations?.keys
            ?: ModelEngine.blueprints().values.flatMap { it.animations.keys }.toSet()
        matching(animations, partial)
    }
}

fun CommandBuilderDsl.variantArgument(name: String = "variantId") {
    wordArgument(name) {
        val pool = (Orbit.mode as? GameMode)?.variantPool() ?: return@wordArgument emptyList()
        matching(pool.all().map { it.id }, partial)
    }
}

fun CommandBuilderDsl.gameModeArgument(name: String = "mode") {
    wordArgument(name) { matching(KNOWN_GAME_MODE_IDS, partial) }
}

fun CommandBuilderDsl.botBehaviorArgument(name: String = "behavior") {
    wordArgument(name) { matching(BOT_BEHAVIOR_IDS, partial) }
}

fun CommandBuilderDsl.testBehaviorArgument(name: String = "behavior") {
    wordArgument(name) { matching(TEST_BEHAVIOR_IDS, partial) }
}

fun CommandBuilderDsl.liveSessionBotArgument(name: String = "target") {
    wordArgument(name) {
        val session = GameTestRunner.getLiveSession(player) ?: return@wordArgument emptyList()
        matching(session.botPlayers.map { it.username }, partial)
    }
}

fun CommandBuilderDsl.liveSessionPlayerArgument(name: String = "target") {
    wordArgument(name) {
        val session = GameTestRunner.getLiveSession(player) ?: return@wordArgument emptyList()
        matching(session.instance.players.map { it.username }, partial)
    }
}

fun CommandBuilderDsl.gamePhaseArgument(name: String = "phase") {
    wordArgument(name) { matching(GamePhase.entries.map { it.name }, partial) }
}

fun CommandBuilderDsl.testBotPresetArgument(name: String = "preset") {
    wordArgument(name) { matching(TestBotPresets.names(), partial) }
}

fun CommandBuilderDsl.testIdArgument(name: String = "testId") {
    wordArgument(name) { matching(GameTestRegistry.ids(), partial) }
}

fun CommandBuilderDsl.testTagArgument(name: String = "tag") {
    wordArgument(name) { matching(GameTestRegistry.tags(), partial) }
}

private val KNOWN_GAME_MODE_IDS: Set<String> = setOf(
    "hub", "battleroyale", "build", "limbo",
)

private val BOT_BEHAVIOR_IDS: Set<String> =
    BotBehavior.entries.map { it.name.lowercase() }.toSet()

private val TEST_BEHAVIOR_IDS: Set<String> =
    me.nebula.orbit.utils.gametest.TestBehavior.entries.map { it.name.lowercase() }.toSet()
