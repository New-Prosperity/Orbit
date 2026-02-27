package me.nebula.orbit.utils.modelengine

import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.chat.sendMM
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.modelengine.generator.ModelGenerator
import me.nebula.orbit.utils.modelengine.generator.ModelIdRegistry
import me.nebula.orbit.utils.modelengine.model.StandaloneModelOwner
import me.nebula.orbit.utils.modelengine.model.standAloneModel
import net.minestom.server.command.builder.Command
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.concurrent.ConcurrentHashMap

private val spawnedModels: MutableSet<StandaloneModelOwner> = ConcurrentHashMap.newKeySet()

fun modelEngineCommand(resources: ResourceManager): Command = command("me") {
    permission("orbit.modelengine")

    subCommand("list") {
        onPlayerExecute {
            val blueprints = ModelEngine.blueprints()
            if (blueprints.isEmpty()) {
                player.sendMM("<gray>No blueprints registered.")
                return@onPlayerExecute
            }
            player.sendMM("<gold><bold>Blueprints</bold> <gray>(${blueprints.size})")
            blueprints.forEach { (name, bp) ->
                player.sendMM("<yellow>$name <gray>— ${bp.bones.size} bones, ${bp.animations.size} animations")
            }
        }
    }

    subCommand("info") {
        wordArgument("blueprint")
        tabComplete { _, input ->
            ModelEngine.blueprints().keys.filter { it.startsWith(input, ignoreCase = true) }
        }
        onPlayerExecute {
            val name: String? = args.get("blueprint")
            if (name == null) {
                player.sendMM("<red>Usage: /me info <blueprint>")
                return@onPlayerExecute
            }
            val bp = ModelEngine.blueprintOrNull(name)
            if (bp == null) {
                player.sendMM("<red>Blueprint not found: $name")
                return@onPlayerExecute
            }
            player.sendMM("<gold><bold>${bp.name}</bold>")
            player.sendMM("<gray>Bones <white>(${bp.bones.size}):")
            bp.traverseDepthFirst { bone, depth ->
                val indent = "  ".repeat(depth + 1)
                player.sendMM("<white>$indent${bone.name}")
            }
            if (bp.animations.isNotEmpty()) {
                player.sendMM("<gray>Animations <white>(${bp.animations.size}):")
                bp.animations.forEach { (_, anim) ->
                    player.sendMM("<white>  ${anim.name} <gray>— ${anim.length}s, ${anim.loop}")
                }
            }
            player.sendMM("<gray>Root bones: <white>${bp.rootBoneNames.joinToString(", ")}")
        }
    }

    subCommand("spawn") {
        stringArrayArgument("args")
        tabComplete { _, input ->
            val tokens = input.split(" ")
            if (tokens.size <= 1) {
                ModelEngine.blueprints().keys.filter { it.startsWith(tokens[0], ignoreCase = true) }
            } else emptyList()
        }
        onPlayerExecute {
            @Suppress("UNCHECKED_CAST")
            val cmdArgs = args.get("args") as? Array<String>
            val blueprintName = cmdArgs?.getOrNull(0)
            if (blueprintName.isNullOrEmpty()) {
                player.sendMM("<red>Usage: /me spawn <blueprint> [scale] [noidle]")
                return@onPlayerExecute
            }
            if (ModelEngine.blueprintOrNull(blueprintName) == null) {
                player.sendMM("<red>Blueprint not found: $blueprintName")
                return@onPlayerExecute
            }
            val scale = cmdArgs.getOrNull(1)?.toFloatOrNull() ?: 1.0f
            val noIdle = cmdArgs.any { it.equals("noidle", ignoreCase = true) }
            val owner = standAloneModel(player.position) {
                model(blueprintName, autoPlayIdle = !noIdle) { scale(scale) }
            }
            owner.show(player)
            spawnedModels += owner
            val idleLabel = if (noIdle) ", noidle" else ""
            player.sendMM("<green>Spawned <white>$blueprintName <green>at your position <gray>(scale=$scale$idleLabel)")
        }
    }

    subCommand("despawn") {
        onPlayerExecute {
            if (spawnedModels.isEmpty()) {
                player.sendMM("<gray>No spawned models to remove.")
                return@onPlayerExecute
            }
            val count = spawnedModels.size
            spawnedModels.forEach { it.remove() }
            spawnedModels.clear()
            player.sendMM("<green>Removed <white>$count <green>standalone model(s).")
        }
    }

    subCommand("reload") {
        wordArgument("name")
        tabComplete { _, input ->
            resources.list("models", "bbmodel")
                .map { it.substringAfterLast('/').substringBeforeLast('.') }
                .filter { it.startsWith(input, ignoreCase = true) }
        }
        onPlayerExecute {
            val name: String? = args.get("name")
            if (name == null) {
                player.sendMM("<red>Usage: /me reload <name>")
                return@onPlayerExecute
            }
            try {
                val result = ModelGenerator.generateRaw(resources, "$name.bbmodel")
                ModelEngine.registerBlueprint(name, result.blueprint)
                player.sendMM("<green>Reloaded <white>$name <green>(${result.blueprint.bones.size} bones, ${result.blueprint.animations.size} animations)")
            } catch (e: Exception) {
                player.sendMM("<red>Failed to reload '$name': ${e.message}")
            }
        }
    }

    subCommand("entities") {
        onPlayerExecute {
            val entities = ModelEngine.allModeledEntities()
            if (entities.isEmpty()) {
                player.sendMM("<gray>No modeled entities tracked.")
                return@onPlayerExecute
            }
            player.sendMM("<gold><bold>Modeled Entities</bold> <gray>(${entities.size})")
            entities.forEach { modeled ->
                val pos = modeled.owner.position
                val modelNames = modeled.models.keys.joinToString(", ")
                player.sendMM("<yellow>${modeled.owner.ownerId} <gray>at <white>${pos.blockX()}, ${pos.blockY()}, ${pos.blockZ()} <gray>models=<white>$modelNames <gray>viewers=<white>${modeled.viewers.size}")
            }
        }
    }

    subCommand("testreal") {
        stringArrayArgument("args")
        tabComplete { _, input ->
            val tokens = input.split(" ")
            if (tokens.size <= 1) {
                ModelEngine.blueprints().keys.filter { it.startsWith(tokens[0], ignoreCase = true) }
            } else emptyList()
        }
        onPlayerExecute {
            @Suppress("UNCHECKED_CAST")
            val cmdArgs = args.get("args") as? Array<String>
            val blueprintName = cmdArgs?.getOrNull(0)
            if (blueprintName.isNullOrEmpty()) {
                player.sendMM("<red>Usage: /me testreal <blueprint> [bone]")
                return@onPlayerExecute
            }
            val bp = ModelEngine.blueprintOrNull(blueprintName)
            if (bp == null) {
                player.sendMM("<red>Blueprint not found: $blueprintName")
                return@onPlayerExecute
            }
            val boneName = cmdArgs.getOrNull(1) ?: bp.rootBoneNames.firstOrNull()
            val bone = boneName?.let { bp.bones[it] }
            if (bone == null) {
                player.sendMM("<red>Bone not found. Available: ${bp.bones.keys.joinToString(", ")}")
                return@onPlayerExecute
            }
            val item = bone.modelItem
            if (item == null) {
                player.sendMM("<red>Bone $boneName has no model item")
                return@onPlayerExecute
            }
            val itemModel = item.get(DataComponents.ITEM_MODEL)
            player.sendMM("<gray>Spawning REAL item_display: item_model=<white>$itemModel")
            player.sendMM("<gray>ItemStack: <white>$item")
            player.sendMM("<gray>Components: <white>${item.get(DataComponents.ITEM_MODEL)}")

            val entity = Entity(EntityType.ITEM_DISPLAY)
            val meta = entity.entityMeta as ItemDisplayMeta
            meta.setNotifyAboutChanges(false)
            meta.setItemStack(item)
            meta.setScale(Vec(1.0, 1.0, 1.0))
            meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.FIXED)
            meta.setNotifyAboutChanges(true)
            entity.setInstance(player.instance!!, player.position.add(0.0, 2.0, 0.0))

            player.sendMM("<green>Spawned REAL entity at your position +2Y. Compare with packet-based model.")
        }
    }

    subCommand("testcube") {
        onPlayerExecute {
            val item = ItemStack.of(Material.PAPER).with(DataComponents.ITEM_MODEL, "minecraft:me_debug_cube")
            player.sendMM("<gray>Spawning debug cube: item_model=<white>minecraft:me_debug_cube")
            player.sendMM("<gray>This should render as a solid RED cube if the pack is loaded correctly.")

            val entity = Entity(EntityType.ITEM_DISPLAY)
            val meta = entity.entityMeta as ItemDisplayMeta
            meta.setNotifyAboutChanges(false)
            meta.setItemStack(item)
            meta.setScale(Vec(1.0, 1.0, 1.0))
            meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.FIXED)
            meta.setNotifyAboutChanges(true)
            entity.setInstance(player.instance!!, player.position.add(0.0, 2.0, 0.0))

            player.sendMM("<green>Spawned debug cube at your position +2Y.")
            player.sendMM("<yellow>If you see a red cube: pack works, issue is in model generation.")
            player.sendMM("<yellow>If you see paper/nothing: pack loading or item_model resolution is broken.")
        }
    }

    subCommand("ids") {
        onPlayerExecute {
            val all = ModelIdRegistry.all()
            player.sendMM("<gold><bold>Model ID Registry</bold>")
            player.sendMM("<gray>Total assigned: <white>${all.size}")
            if (all.isNotEmpty()) {
                player.sendMM("<gray>Sample entries:")
                all.entries.take(10).forEach { (key, id) ->
                    player.sendMM("<white>  $key <gray>= <yellow>$id")
                }
                if (all.size > 10) {
                    player.sendMM("<gray>  ... and ${all.size - 10} more")
                }
            }
        }
    }
}
