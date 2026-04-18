package me.nebula.orbit.utils.modelengine

import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.modelengine.generator.ModelGenerator
import me.nebula.orbit.utils.modelengine.generator.ModelIdRegistry
import me.nebula.orbit.utils.modelengine.model.StandaloneModelOwner
import me.nebula.orbit.utils.modelengine.model.standAloneModel
import me.nebula.orbit.utils.scheduler.delay
import me.nebula.orbit.utils.statue.PlayerModelGenerator
import me.nebula.orbit.utils.statue.PlayerSkinPack
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
                player.sendMessage(player.translate("orbit.command.me.list.empty"))
                return@onPlayerExecute
            }
            player.sendMessage(player.translate("orbit.command.me.list.header", "count" to blueprints.size.toString()))
            blueprints.forEach { (name, bp) ->
                player.sendMessage(player.translate("orbit.command.me.list.entry",
                    "name" to name,
                    "bones" to bp.bones.size.toString(),
                    "animations" to bp.animations.size.toString(),
                ))
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
                player.sendMessage(player.translate("orbit.command.me.info.usage"))
                return@onPlayerExecute
            }
            val bp = ModelEngine.blueprintOrNull(name)
            if (bp == null) {
                player.sendMessage(player.translate("orbit.command.me.blueprint_not_found", "name" to name))
                return@onPlayerExecute
            }
            player.sendMessage(player.translate("orbit.command.me.info.header", "name" to bp.name))
            player.sendMessage(player.translate("orbit.command.me.info.bones", "count" to bp.bones.size.toString()))
            bp.traverseDepthFirst { bone, depth ->
                val indent = "  ".repeat(depth + 1)
                player.sendMessage(player.translate("orbit.command.me.info.bone_entry",
                    "indent" to indent, "name" to bone.name))
            }
            if (bp.animations.isNotEmpty()) {
                player.sendMessage(player.translate("orbit.command.me.info.animations", "count" to bp.animations.size.toString()))
                bp.animations.forEach { (_, anim) ->
                    player.sendMessage(player.translate("orbit.command.me.info.animation_entry",
                        "name" to anim.name,
                        "length" to anim.length.toString(),
                        "loop" to anim.loop.toString(),
                    ))
                }
            }
            player.sendMessage(player.translate("orbit.command.me.info.roots", "names" to bp.rootBoneNames.joinToString(", ")))
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
                player.sendMessage(player.translate("orbit.command.me.spawn.usage"))
                return@onPlayerExecute
            }
            if (ModelEngine.blueprintOrNull(blueprintName) == null) {
                player.sendMessage(player.translate("orbit.command.me.blueprint_not_found", "name" to blueprintName))
                return@onPlayerExecute
            }
            val scale = cmdArgs.getOrNull(1)?.toFloatOrNull() ?: 1.0f
            val noIdle = cmdArgs.any { it.equals("noidle", ignoreCase = true) }
            val owner = standAloneModel(player.position) {
                model(blueprintName, autoPlayIdle = !noIdle) { scale(scale) }
            }
            if (blueprintName == PlayerModelGenerator.BLUEPRINT_NAME) {
                player.skin?.let { PlayerSkinPack.applyTo(owner, it) }
            }
            owner.show(player)
            spawnedModels += owner
            val key = if (noIdle) "orbit.command.me.spawn.success_noidle" else "orbit.command.me.spawn.success"
            player.sendMessage(player.translate(key, "name" to blueprintName, "scale" to scale.toString()))
        }
    }

    subCommand("despawn") {
        onPlayerExecute {
            if (spawnedModels.isEmpty()) {
                player.sendMessage(player.translate("orbit.command.me.despawn.empty"))
                return@onPlayerExecute
            }
            val count = spawnedModels.size
            spawnedModels.forEach { it.remove() }
            spawnedModels.clear()
            player.sendMessage(player.translate("orbit.command.me.despawn.success", "count" to count.toString()))
        }
    }

    subCommand("animate") {
        stringArrayArgument("args")
        tabComplete { _, input ->
            val tokens = input.split(" ")
            if (tokens.size <= 1) {
                spawnedModels.asSequence()
                    .flatMap { it.modeledEntity?.models?.values?.asSequence().orEmpty() }
                    .flatMap { it.blueprint.animations.keys.asSequence() }
                    .distinct()
                    .filter { it.startsWith(tokens[0], ignoreCase = true) }
                    .toList()
            } else emptyList()
        }
        onPlayerExecute {
            if (spawnedModels.isEmpty()) {
                player.sendMessage(player.translate("orbit.command.me.animate.empty"))
                return@onPlayerExecute
            }
            @Suppress("UNCHECKED_CAST")
            val cmdArgs = args.get("args") as? Array<String>
            val animationName = cmdArgs?.getOrNull(0)
            if (animationName.isNullOrEmpty()) {
                player.sendMessage(player.translate("orbit.command.me.animate.usage"))
                return@onPlayerExecute
            }
            val speed = cmdArgs.getOrNull(1)?.toFloatOrNull() ?: 1f
            val lerpIn = cmdArgs.getOrNull(2)?.toFloatOrNull() ?: 0.2f
            val lerpOut = cmdArgs.getOrNull(3)?.toFloatOrNull() ?: 0.2f
            var played = 0
            spawnedModels.forEach { owner ->
                owner.modeledEntity?.models?.values?.forEach { active ->
                    val anim = active.blueprint.animations[animationName] ?: return@forEach
                    active.stopAllAnimations()
                    active.playAnimation(animationName, lerpIn = lerpIn, lerpOut = lerpOut, speed = speed)
                    played++
                    val durationTicks = (((anim.length / speed) + lerpOut) * 20f).toInt().coerceAtLeast(1)
                    delay(durationTicks) {
                        if (owner.isRemoved) return@delay
                        active.stopAllAnimations()
                        active.blueprint.animations.keys
                            .firstOrNull { "idle" in it.lowercase() }
                            ?.let { active.playAnimation(it) }
                    }
                }
            }
            if (played == 0) {
                player.sendMessage(player.translate("orbit.command.me.animate.not_found", "name" to animationName))
            } else {
                player.sendMessage(player.translate("orbit.command.me.animate.success",
                    "name" to animationName, "count" to played.toString()))
            }
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
                player.sendMessage(player.translate("orbit.command.me.reload.usage"))
                return@onPlayerExecute
            }
            try {
                val result = ModelGenerator.generateRaw(resources, "$name.bbmodel")
                ModelEngine.registerBlueprint(name, result.blueprint)
                player.sendMessage(player.translate("orbit.command.me.reload.success",
                    "name" to name,
                    "bones" to result.blueprint.bones.size.toString(),
                    "animations" to result.blueprint.animations.size.toString(),
                ))
            } catch (e: Exception) {
                player.sendMessage(player.translate("orbit.command.me.reload.failed",
                    "name" to name, "error" to (e.message ?: "")))
            }
        }
    }

    subCommand("entities") {
        onPlayerExecute {
            val entities = ModelEngine.allModeledEntities()
            if (entities.isEmpty()) {
                player.sendMessage(player.translate("orbit.command.me.entities.empty"))
                return@onPlayerExecute
            }
            player.sendMessage(player.translate("orbit.command.me.entities.header", "count" to entities.size.toString()))
            entities.forEach { modeled ->
                val pos = modeled.owner.position
                val modelNames = modeled.models.keys.joinToString(", ")
                player.sendMessage(player.translate("orbit.command.me.entities.entry",
                    "id" to modeled.owner.ownerId.toString(),
                    "x" to pos.blockX().toString(),
                    "y" to pos.blockY().toString(),
                    "z" to pos.blockZ().toString(),
                    "models" to modelNames,
                    "viewers" to modeled.viewers.size.toString(),
                ))
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
            val instance = player.instance ?: return@onPlayerExecute
            @Suppress("UNCHECKED_CAST")
            val cmdArgs = args.get("args") as? Array<String>
            val blueprintName = cmdArgs?.getOrNull(0)
            if (blueprintName.isNullOrEmpty()) {
                player.sendMessage(player.translate("orbit.command.me.testreal.usage"))
                return@onPlayerExecute
            }
            val bp = ModelEngine.blueprintOrNull(blueprintName)
            if (bp == null) {
                player.sendMessage(player.translate("orbit.command.me.blueprint_not_found", "name" to blueprintName))
                return@onPlayerExecute
            }
            val boneName = cmdArgs.getOrNull(1) ?: bp.rootBoneNames.firstOrNull()
            val bone = boneName?.let { bp.bones[it] }
            if (bone == null) {
                player.sendMessage(player.translate("orbit.command.me.testreal.bone_not_found",
                    "available" to bp.bones.keys.joinToString(", ")))
                return@onPlayerExecute
            }
            val item = bone.modelItem
            if (item == null) {
                player.sendMessage(player.translate("orbit.command.me.testreal.no_model_item", "name" to boneName))
                return@onPlayerExecute
            }
            val itemModel = item.get(DataComponents.ITEM_MODEL)
            player.sendMessage(player.translate("orbit.command.me.testreal.spawn_log_model", "value" to itemModel.toString()))
            player.sendMessage(player.translate("orbit.command.me.testreal.spawn_log_stack", "value" to item.toString()))
            player.sendMessage(player.translate("orbit.command.me.testreal.spawn_log_components", "value" to item.get(DataComponents.ITEM_MODEL).toString()))

            val entity = Entity(EntityType.ITEM_DISPLAY)
            val meta = entity.entityMeta as ItemDisplayMeta
            meta.setNotifyAboutChanges(false)
            meta.setItemStack(item)
            meta.setScale(Vec(1.0, 1.0, 1.0))
            meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.FIXED)
            meta.setNotifyAboutChanges(true)
            entity.setInstance(instance, player.position.add(0.0, 2.0, 0.0))

            player.sendMessage(player.translate("orbit.command.me.testreal.spawned"))
        }
    }

    subCommand("testcube") {
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            val item = ItemStack.of(Material.PAPER).with(DataComponents.ITEM_MODEL, "minecraft:me_debug_cube")
            player.sendMessage(player.translate("orbit.command.me.testcube.spawning"))
            player.sendMessage(player.translate("orbit.command.me.testcube.expected"))

            val entity = Entity(EntityType.ITEM_DISPLAY)
            val meta = entity.entityMeta as ItemDisplayMeta
            meta.setNotifyAboutChanges(false)
            meta.setItemStack(item)
            meta.setScale(Vec(1.0, 1.0, 1.0))
            meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.FIXED)
            meta.setNotifyAboutChanges(true)
            entity.setInstance(instance, player.position.add(0.0, 2.0, 0.0))

            player.sendMessage(player.translate("orbit.command.me.testcube.spawned"))
            player.sendMessage(player.translate("orbit.command.me.testcube.diagnostic_yes"))
            player.sendMessage(player.translate("orbit.command.me.testcube.diagnostic_no"))
        }
    }

    subCommand("ids") {
        onPlayerExecute {
            val all = ModelIdRegistry.all()
            player.sendMessage(player.translate("orbit.command.me.ids.header"))
            player.sendMessage(player.translate("orbit.command.me.ids.total", "count" to all.size.toString()))
            if (all.isNotEmpty()) {
                player.sendMessage(player.translate("orbit.command.me.ids.sample_header"))
                all.entries.take(10).forEach { (key, id) ->
                    player.sendMessage(player.translate("orbit.command.me.ids.sample_entry",
                        "key" to key, "id" to id.toString()))
                }
                if (all.size > 10) {
                    player.sendMessage(player.translate("orbit.command.me.ids.more", "count" to (all.size - 10).toString()))
                }
            }
        }
    }
}

