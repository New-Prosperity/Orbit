package me.nebula.orbit.commands

import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.utils.commandbuilder.CommandBuilderDsl
import me.nebula.orbit.utils.commandbuilder.variantArgument

internal fun CommandBuilderDsl.installVariantSubcommands() {
    subCommand("variant") {
        subCommand("list") {
            onPlayerExecute {
                val mode = Orbit.mode as? GameMode
                val pool = mode?.variantPool()
                if (pool == null) {
                    replyMM("<red>No variant pool available for this game mode.")
                    return@onPlayerExecute
                }
                val variants = pool.all()
                if (variants.isEmpty()) {
                    replyMM("<red>No variants registered.")
                    return@onPlayerExecute
                }
                replyMM("<gray>Available variants (${variants.size}):")
                val activeId = mode.activeVariant?.id
                for (v in variants) {
                    val marker = if (v.id == activeId) "<green>● " else "<gray>  "
                    val weight = "<dark_gray>w=${v.weight}"
                    val group = v.conflictGroup?.let { " <dark_gray>[$it]" } ?: ""
                    replyMM("$marker<white>${v.id}$group $weight")
                }
            }
        }

        subCommand("show") {
            onPlayerExecute {
                val active = (Orbit.mode as? GameMode)?.activeVariant
                if (active == null) {
                    replyMM("<red>No variant is currently active.")
                    return@onPlayerExecute
                }
                replyMM("<gray>Active variant: <green>${active.id}")
                replyMM("<gray>Name key: <white>${active.nameKey}")
                replyMM("<gray>Components: <white>${active.components.joinToString { it::class.simpleName ?: "?" }}")
            }
        }

        subCommand("force") {
            variantArgument("variantId")
            onPlayerExecute {
                val mode = Orbit.mode as? GameMode
                val pool = mode?.variantPool()
                if (mode == null || pool == null) {
                    replyMM("<red>No variant pool available for this game mode.")
                    return@onPlayerExecute
                }
                val id = argOrNull("variantId")
                if (id == null) {
                    replyMM("<red>Usage: /orbit variant force <variantId>")
                    return@onPlayerExecute
                }
                val variant = pool.resolve(id)
                if (variant == null) {
                    replyMM("<red>Unknown variant: <white>$id<red>. Use /orbit variant list.")
                    return@onPlayerExecute
                }
                mode.variantController.install(variant)
                replyMM("<green>Variant installed: <white>${variant.id}")
            }
        }

        subCommand("reroll") {
            onPlayerExecute {
                val mode = Orbit.mode as? GameMode
                val pool = mode?.variantPool()
                if (mode == null || pool == null) {
                    replyMM("<red>No variant pool available for this game mode.")
                    return@onPlayerExecute
                }
                val variant = pool.selectRandom()
                if (variant == null) {
                    replyMM("<red>No random variant available in pool.")
                    return@onPlayerExecute
                }
                mode.variantController.install(variant)
                replyMM("<green>Variant re-rolled: <white>${variant.id}")
            }
        }

        onPlayerExecute {
            replyMM("<gray>Usage:")
            replyMM("<white> /orbit variant list <dark_gray>- List all variants in this game mode's pool")
            replyMM("<white> /orbit variant show <dark_gray>- Show active variant")
            replyMM("<white> /orbit variant force <id> <dark_gray>- Force a specific variant")
            replyMM("<white> /orbit variant reroll <dark_gray>- Pick a random variant from the pool")
        }
    }
}
