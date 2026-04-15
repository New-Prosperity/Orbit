package me.nebula.orbit.mode.game.battleroyale

import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.orbit.rules.Rules
import me.nebula.orbit.script.ScriptAction
import me.nebula.orbit.script.ScriptStep
import me.nebula.orbit.script.ScriptTrigger
import me.nebula.orbit.variant.GameComponent
import me.nebula.orbit.variant.GameVariant
import me.nebula.orbit.variant.GameVariantPool
import kotlin.time.Duration.Companion.minutes

object BattleRoyaleVariants {

    val CLASSIC = GameVariant(
        id = "br_classic",
        nameKey = "orbit.variant.br_classic.name".asTranslationKey(),
        descriptionKey = "orbit.variant.br_classic.desc".asTranslationKey(),
        material = "minecraft:minecart",
        components = listOf(
            GameComponent.Spawn("battle_royale_bus"),
            GameComponent.InitialRules(mapOf(
                Rules.PVP_ENABLED to true,
                Rules.DAMAGE_ENABLED to true,
                Rules.ZONE_SHRINKING to true,
            )),
        ),
        weight = 3,
        conflictGroup = "br_variant",
    )

    val BUS_TRUCE = GameVariant(
        id = "br_bus_truce",
        nameKey = "orbit.variant.br_bus_truce.name".asTranslationKey(),
        descriptionKey = "orbit.variant.br_bus_truce.desc".asTranslationKey(),
        material = "minecraft:tnt_minecart",
        components = listOf(
            GameComponent.Spawn("battle_royale_bus"),
            GameComponent.InitialRules(mapOf(
                Rules.PVP_ENABLED to true,
                Rules.DAMAGE_ENABLED to true,
                Rules.ZONE_SHRINKING to true,
            )),
            GameComponent.Script(listOf(
                ScriptStep(
                    id = "pvp_pause",
                    trigger = ScriptTrigger.AtTime(5.minutes),
                    actions = listOf(
                        ScriptAction.SetRule(Rules.PVP_ENABLED, false),
                        ScriptAction.Announce("orbit.variant.truce_begin".asTranslationKey(), sound = "block.note_block.bell"),
                    ),
                ),
                ScriptStep(
                    id = "pvp_resume",
                    trigger = ScriptTrigger.AtTime(25.minutes),
                    actions = listOf(
                        ScriptAction.SetRule(Rules.PVP_ENABLED, true),
                        ScriptAction.Announce("orbit.variant.truce_end".asTranslationKey(), sound = "entity.wither.spawn"),
                    ),
                ),
                ScriptStep(
                    id = "pvp_final_duel",
                    trigger = ScriptTrigger.WhenAliveAtOrBelow(3),
                    actions = listOf(
                        ScriptAction.SetRule(Rules.PVP_ENABLED, true),
                        ScriptAction.Announce("orbit.variant.final_duel".asTranslationKey(), sound = "entity.ender_dragon.death"),
                    ),
                ),
            )),
        ),
        weight = 1,
        conflictGroup = "br_variant",
    )

    val HUNGER_GAMES = GameVariant(
        id = "br_hunger_games",
        nameKey = "orbit.variant.br_hg.name".asTranslationKey(),
        descriptionKey = "orbit.variant.br_hg.desc".asTranslationKey(),
        material = "minecraft:wooden_sword",
        components = listOf(
            GameComponent.Spawn("hunger_games"),
            GameComponent.InitialRules(mapOf(
                Rules.PVP_ENABLED to true,
                Rules.ZONE_SHRINKING to false,
            )),
            GameComponent.Script(listOf(
                ScriptStep(
                    id = "pvp_pause",
                    trigger = ScriptTrigger.AtTime(2.minutes),
                    actions = listOf(
                        ScriptAction.SetRule(Rules.PVP_ENABLED, false),
                        ScriptAction.Announce("orbit.variant.truce_begin".asTranslationKey(), sound = "block.note_block.bell"),
                    ),
                ),
                ScriptStep(
                    id = "pvp_resume_zone",
                    trigger = ScriptTrigger.AtTime(22.minutes),
                    actions = listOf(
                        ScriptAction.SetMany(mapOf(
                            Rules.PVP_ENABLED to true,
                            Rules.ZONE_SHRINKING to true,
                        )),
                        ScriptAction.Announce("orbit.variant.truce_end".asTranslationKey(), sound = "entity.wither.spawn"),
                    ),
                ),
                ScriptStep(
                    id = "pvp_final_duel",
                    trigger = ScriptTrigger.WhenAliveAtOrBelow(3),
                    actions = listOf(
                        ScriptAction.SetRule(Rules.PVP_ENABLED, true),
                        ScriptAction.Announce("orbit.variant.final_duel".asTranslationKey(), sound = "entity.ender_dragon.death"),
                    ),
                ),
            )),
            GameComponent.MutatorFilter(excluded = listOf("low_gravity")),
        ),
        weight = 1,
        conflictGroup = "br_variant",
    )

    val ALL: List<GameVariant> = listOf(CLASSIC, BUS_TRUCE, HUNGER_GAMES)

    val POOL: GameVariantPool = GameVariantPool(ALL)
}
