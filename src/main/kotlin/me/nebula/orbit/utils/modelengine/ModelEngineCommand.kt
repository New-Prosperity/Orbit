package me.nebula.orbit.utils.modelengine

import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.chat.mm
import me.nebula.orbit.utils.commandbuilder.blueprintArgument
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.cooldown.EntityNamedCooldown
import me.nebula.orbit.utils.entitybuilder.Behavior
import me.nebula.orbit.utils.entitybuilder.MemoryKeys
import me.nebula.orbit.utils.entitybuilder.SmartEntity
import me.nebula.orbit.utils.entitybuilder.smartTags
import me.nebula.orbit.utils.entitybuilder.file.BehaviorFileRegistry
import me.nebula.orbit.utils.modelengine.generator.ModelGenerator
import me.nebula.orbit.utils.modelengine.generator.ModelIdRegistry
import me.nebula.orbit.utils.modelengine.model.StandaloneModelOwner
import me.nebula.orbit.utils.modelengine.model.standAloneModel
import me.nebula.orbit.utils.scheduler.delay
import me.nebula.orbit.utils.statue.PlayerModelGenerator
import me.nebula.orbit.utils.statue.PlayerSkinPack
import net.minestom.server.command.builder.Command
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.concurrent.ConcurrentHashMap
import me.nebula.gravity.translation.Keys
import me.nebula.ether.utils.translation.asTranslationKey

private val spawnedModels: MutableSet<StandaloneModelOwner> = ConcurrentHashMap.newKeySet()
private val spawnedSmartEntities: MutableSet<SmartEntity> = ConcurrentHashMap.newKeySet()

fun modelEngineCommand(resources: ResourceManager): Command = command("me") {
    permission("orbit.modelengine")

    subCommand("list") {
        onPlayerExecute {
            val blueprints = ModelEngine.blueprints()
            if (blueprints.isEmpty()) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.List.Empty))
                return@onPlayerExecute
            }
            player.sendMessage(player.translate(Keys.Orbit.Command.Me.List.Header, "count" to blueprints.size.toString()))
            blueprints.forEach { (name, bp) ->
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.List.Entry,
                    "name" to name,
                    "bones" to bp.bones.size.toString(),
                    "animations" to bp.animations.size.toString(),
                ))
            }
        }
    }

    subCommand("info") {
        blueprintArgument("blueprint")
        onPlayerExecute {
            val name: String? = args.get("blueprint")
            if (name == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Info.Usage))
                return@onPlayerExecute
            }
            val bp = ModelEngine.blueprintOrNull(name)
            if (bp == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.BlueprintNotFound, "name" to name))
                return@onPlayerExecute
            }
            player.sendMessage(player.translate(Keys.Orbit.Command.Me.Info.Header, "name" to bp.name))
            player.sendMessage(player.translate(Keys.Orbit.Command.Me.Info.Bones, "count" to bp.bones.size.toString()))
            bp.traverseDepthFirst { bone, depth ->
                val indent = "  ".repeat(depth + 1)
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Info.BoneEntry,
                    "indent" to indent, "name" to bone.name))
            }
            if (bp.animations.isNotEmpty()) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Info.Animations, "count" to bp.animations.size.toString()))
                bp.animations.forEach { (_, anim) ->
                    player.sendMessage(player.translate(Keys.Orbit.Command.Me.Info.AnimationEntry,
                        "name" to anim.name,
                        "length" to anim.length.toString(),
                        "loop" to anim.loop.toString(),
                    ))
                }
            }
            player.sendMessage(player.translate(Keys.Orbit.Command.Me.Info.Roots, "names" to bp.rootBoneNames.joinToString(", ")))
        }
    }

    subCommand("spawn") {
        stringArrayArgument("args")
        tabComplete { _, partial ->
            ModelEngine.blueprints().keys.filter { it.startsWith(partial, ignoreCase = true) }
        }
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            @Suppress("UNCHECKED_CAST")
            val cmdArgs = args.get("args") as? Array<String>
            val blueprintName = cmdArgs?.getOrNull(0)
            if (blueprintName.isNullOrEmpty()) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Spawn.Usage))
                return@onPlayerExecute
            }

            val behaviorArg = cmdArgs.firstOrNull { it.startsWith("behavior=", ignoreCase = true) }
                ?.substringAfter('=')
            if (!behaviorArg.isNullOrEmpty()) {
                val file = BehaviorFileRegistry.get(behaviorArg)
                if (file == null) {
                    player.sendMessage(mm("<red>Unknown behavior file: $behaviorArg. Known: ${BehaviorFileRegistry.ids().joinToString(", ").ifEmpty { "(none)" }}"))
                    return@onPlayerExecute
                }
                if (ModelEngine.blueprintOrNull(blueprintName) == null) {
                    player.sendMessage(player.translate(Keys.Orbit.Command.Me.BlueprintNotFound, "name" to blueprintName))
                    return@onPlayerExecute
                }
                val entity = file.spawn(instance, player.position) {
                    model(blueprintName)
                }
                entity.isCustomNameVisible = false
                entity.memory.set(MemoryKeys.OWNER, player)
                spawnedSmartEntities += entity
                player.sendMessage(mm("<green>Spawned SmartEntity from behavior <yellow>${file.id}</yellow> (carrier=${file.carrierType.name()}, model=$blueprintName, owner=${player.username})"))
                return@onPlayerExecute
            }

            if (ModelEngine.blueprintOrNull(blueprintName) == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.BlueprintNotFound, "name" to blueprintName))
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
            player.sendMessage(player.translate(key.asTranslationKey(), "name" to blueprintName, "scale" to scale.toString()))
        }
    }

    subCommand("despawn") {
        onPlayerExecute {
            if (spawnedModels.isEmpty() && spawnedSmartEntities.isEmpty()) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Despawn.Empty))
                return@onPlayerExecute
            }
            val count = spawnedModels.size + spawnedSmartEntities.size
            spawnedModels.forEach { it.remove() }
            spawnedModels.clear()
            spawnedSmartEntities.forEach { it.remove() }
            spawnedSmartEntities.clear()
            player.sendMessage(player.translate(Keys.Orbit.Command.Me.Despawn.Success, "count" to count.toString()))
        }
    }

    subCommand("animate") {
        stringArrayArgument("args")
        tabComplete { _, partial ->
            spawnedModels.asSequence()
                .flatMap { it.modeledEntity?.models?.values?.asSequence().orEmpty() }
                .flatMap { it.blueprint.animations.keys.asSequence() }
                .distinct()
                .filter { it.startsWith(partial, ignoreCase = true) }
                .toList()
        }
        onPlayerExecute {
            if (spawnedModels.isEmpty()) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Animate.Empty))
                return@onPlayerExecute
            }
            @Suppress("UNCHECKED_CAST")
            val cmdArgs = args.get("args") as? Array<String>
            val animationName = cmdArgs?.getOrNull(0)
            if (animationName.isNullOrEmpty()) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Animate.Usage))
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
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Animate.NotFound, "name" to animationName))
            } else {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Animate.Success,
                    "name" to animationName, "count" to played.toString()))
            }
        }
    }

    subCommand("reload") {
        wordArgument("name") {
            resources.list("models", "bbmodel")
                .map { it.substringAfterLast('/').substringBeforeLast('.') }
                .filter { it.startsWith(partial, ignoreCase = true) }
        }
        onPlayerExecute {
            val name: String? = args.get("name")
            if (name == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Reload.Usage))
                return@onPlayerExecute
            }
            try {
                val result = ModelGenerator.generateRaw(resources, "$name.bbmodel")
                ModelEngine.registerBlueprint(name, result.blueprint)
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Reload.Success,
                    "name" to name,
                    "bones" to result.blueprint.bones.size.toString(),
                    "animations" to result.blueprint.animations.size.toString(),
                ))
            } catch (e: Exception) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Reload.Failed,
                    "name" to name, "error" to (e.message ?: "")))
            }
        }
    }

    subCommand("entities") {
        onPlayerExecute {
            val entities = ModelEngine.allModeledEntities()
            if (entities.isEmpty()) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Entities.Empty))
                return@onPlayerExecute
            }
            player.sendMessage(player.translate(Keys.Orbit.Command.Me.Entities.Header, "count" to entities.size.toString()))
            entities.forEach { modeled ->
                val pos = modeled.owner.position
                val modelNames = modeled.models.keys.joinToString(", ")
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Entities.Entry,
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
        tabComplete { _, partial ->
            ModelEngine.blueprints().keys.filter { it.startsWith(partial, ignoreCase = true) }
        }
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            @Suppress("UNCHECKED_CAST")
            val cmdArgs = args.get("args") as? Array<String>
            val blueprintName = cmdArgs?.getOrNull(0)
            if (blueprintName.isNullOrEmpty()) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Testreal.Usage))
                return@onPlayerExecute
            }
            val bp = ModelEngine.blueprintOrNull(blueprintName)
            if (bp == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.BlueprintNotFound, "name" to blueprintName))
                return@onPlayerExecute
            }
            val boneName = cmdArgs.getOrNull(1) ?: bp.rootBoneNames.firstOrNull()
            val bone = boneName?.let { bp.bones[it] }
            if (bone == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Testreal.BoneNotFound,
                    "available" to bp.bones.keys.joinToString(", ")))
                return@onPlayerExecute
            }
            val item = bone.modelItem
            if (item == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Testreal.NoModelItem, "name" to boneName))
                return@onPlayerExecute
            }
            val itemModel = item.get(DataComponents.ITEM_MODEL)
            player.sendMessage(player.translate(Keys.Orbit.Command.Me.Testreal.SpawnLogModel, "value" to itemModel.toString()))
            player.sendMessage(player.translate(Keys.Orbit.Command.Me.Testreal.SpawnLogStack, "value" to item.toString()))
            player.sendMessage(player.translate(Keys.Orbit.Command.Me.Testreal.SpawnLogComponents, "value" to item.get(DataComponents.ITEM_MODEL).toString()))

            val entity = Entity(EntityType.ITEM_DISPLAY)
            val meta = entity.entityMeta as ItemDisplayMeta
            meta.setNotifyAboutChanges(false)
            meta.setItemStack(item)
            meta.setScale(Vec(1.0, 1.0, 1.0))
            meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.FIXED)
            meta.setNotifyAboutChanges(true)
            entity.setInstance(instance, player.position.add(0.0, 2.0, 0.0))

            player.sendMessage(player.translate(Keys.Orbit.Command.Me.Testreal.Spawned))
        }
    }

    subCommand("testcube") {
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            val item = ItemStack.of(Material.PAPER).with(DataComponents.ITEM_MODEL, "minecraft:me_debug_cube")
            player.sendMessage(player.translate(Keys.Orbit.Command.Me.Testcube.Spawning))
            player.sendMessage(player.translate(Keys.Orbit.Command.Me.Testcube.Expected))

            val entity = Entity(EntityType.ITEM_DISPLAY)
            val meta = entity.entityMeta as ItemDisplayMeta
            meta.setNotifyAboutChanges(false)
            meta.setItemStack(item)
            meta.setScale(Vec(1.0, 1.0, 1.0))
            meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.FIXED)
            meta.setNotifyAboutChanges(true)
            entity.setInstance(instance, player.position.add(0.0, 2.0, 0.0))

            player.sendMessage(player.translate(Keys.Orbit.Command.Me.Testcube.Spawned))
            player.sendMessage(player.translate(Keys.Orbit.Command.Me.Testcube.DiagnosticYes))
            player.sendMessage(player.translate(Keys.Orbit.Command.Me.Testcube.DiagnosticNo))
        }
    }

    subCommand("ids") {
        onPlayerExecute {
            val all = ModelIdRegistry.all()
            player.sendMessage(player.translate(Keys.Orbit.Command.Me.Ids.Header))
            player.sendMessage(player.translate(Keys.Orbit.Command.Me.Ids.Total, "count" to all.size.toString()))
            if (all.isNotEmpty()) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Me.Ids.SampleHeader))
                all.entries.take(10).forEach { (key, id) ->
                    player.sendMessage(player.translate(Keys.Orbit.Command.Me.Ids.SampleEntry,
                        "key" to key, "id" to id.toString()))
                }
                if (all.size > 10) {
                    player.sendMessage(player.translate(Keys.Orbit.Command.Me.Ids.More, "count" to (all.size - 10).toString()))
                }
            }
        }
    }

    subCommand("list_behaviors") {
        onPlayerExecute {
            val all = BehaviorFileRegistry.all()
            if (all.isEmpty()) {
                player.sendMessage(mm("<gray>No behavior files registered. Drop .behavior files into <yellow>customcontent/behaviors/</yellow>."))
                return@onPlayerExecute
            }
            player.sendMessage(mm("<dark_aqua>━━━ <aqua>Behavior Files (${all.size})</aqua> <dark_aqua>━━━"))
            all.sortedBy { it.id }.forEach { file ->
                player.sendMessage(mm("  <yellow>${file.id} <gray>carrier=<white>${file.carrierType.name()}<gray> invisible=<white>${file.invisibleCarrier}"))
            }
            player.sendMessage(mm("<gray>Spawn with <yellow>/me spawn <model> behavior=<id>"))
        }
    }

    subCommand("reload_behaviors") {
        onPlayerExecute {
            val result = BehaviorFileRegistry.reloadFrom(resources, "customcontent/behaviors")
            player.sendMessage(mm("<green>Reloaded behavior files: <yellow>${result.loaded.size} loaded<green>, <red>${result.failed.size} failed"))
            result.loaded.sorted().forEach { player.sendMessage(mm("  <yellow>$it")) }
            result.failed.forEach { (id, err) -> player.sendMessage(mm("  <red>$id<gray>: <white>$err")) }
        }
    }

    subCommand("reload_behavior") {
        wordArgument("id") {
            BehaviorFileRegistry.ids().filter { it.startsWith(partial, ignoreCase = true) }
        }
        onPlayerExecute {
            val id: String? = args.get("id")
            if (id.isNullOrEmpty()) {
                player.sendMessage(mm("<red>Usage: /me reload_behavior <id>"))
                return@onPlayerExecute
            }
            BehaviorFileRegistry.reloadOne(resources, "customcontent/behaviors", id)
                .onSuccess { file ->
                    player.sendMessage(mm("<green>Reloaded behavior <yellow>${file.id}</yellow> (carrier=${file.carrierType.name()})"))
                }
                .onFailure { err ->
                    player.sendMessage(mm("<red>Failed to reload <yellow>$id</yellow><gray>: <white>${err.message}"))
                }
        }
    }

    subCommand("debug") {
        stringArrayArgument("args")
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            @Suppress("UNCHECKED_CAST")
            val cmdArgs = args.get("args") as? Array<String>
            val radius = cmdArgs?.getOrNull(0)?.toDoubleOrNull() ?: 32.0

            val target = instance.getNearbyEntities(player.position, radius)
                .asSequence()
                .filterIsInstance<SmartEntity>()
                .filter { !it.isRemoved }
                .minByOrNull { it.position.distanceSquared(player.position) }

            if (target == null) {
                player.sendMessage(mm("<red>No SmartEntity found within ${radius.toInt()} blocks"))
                return@onPlayerExecute
            }

            printSmartEntityDebug(player, target)
        }
    }
}

private fun printSmartEntityDebug(player: Player, entity: SmartEntity) {
    val group = entity.behaviorGroup
    val maxHp = entity.getAttribute(Attribute.MAX_HEALTH).value.toFloat()
    val pct = if (maxHp > 0) (entity.health / maxHp * 100).toInt() else 0
    val pos = entity.position
    val dist = player.position.distance(pos)

    player.sendMessage(mm("<dark_aqua>━━━ <aqua>SmartEntity Debug</aqua> <dark_aqua>━━━"))
    player.sendMessage(mm("<gray>id=<white>${entity.entityId} <gray>uuid=<white>${entity.uuid.toString().take(8)} <gray>type=<white>${entity.entityType.name()}"))
    val iframesLabel = if (entity.iframesActive) " <red>iframes=<white>${entity.iframesRemaining}t" else ""
    val combatLabel = if (entity.inCombat) " <red>in_combat" else " <gray>peaceful"
    player.sendMessage(mm("<gray>hp=<white>${"%.1f".format(entity.health)}/${"%.1f".format(maxHp)} <gray>(<yellow>$pct%</yellow>) phase=<white>${entity.memory.get(MemoryKeys.PHASE) ?: 0}$iframesLabel$combatLabel"))
    if (entity.regenAmount > 0f) {
        val regenWhen = if (entity.regenInCombat) "always" else "out of combat"
        player.sendMessage(mm("<gray>regen=<white>${entity.regenAmount}/${entity.regenIntervalTicks}t <gray>($regenWhen)"))
    }
    if (entity.effectImmunities.isNotEmpty()) {
        player.sendMessage(mm("<gray>effect_immunities=<white>${entity.effectImmunities.joinToString(", ") { it.name() }}"))
    }
    if (entity.effectResistances.isNotEmpty()) {
        val text = entity.effectResistances.entries.joinToString(", ") { (eff, r) ->
            "${eff.name()}(${"%.0f".format(r.durationMultiplier * 100)}%)"
        }
        player.sendMessage(mm("<gray>effect_resistances=<white>$text"))
    }
    val tags = entity.smartTags()
    if (tags.isNotEmpty()) {
        player.sendMessage(mm("<gray>tags=<white>${tags.joinToString(", ")}"))
    }
    if (entity.isDying) {
        player.sendMessage(mm("<red>DYING (death anim playing)"))
    }
    player.sendMessage(mm("<gray>pos=<white>(${"%.1f".format(pos.x())}, ${"%.1f".format(pos.y())}, ${"%.1f".format(pos.z())}) <gray>dist=<white>${"%.1f".format(dist)}"))

    val activeCore = group.activeCore
    if (activeCore.isNotEmpty()) {
        player.sendMessage(mm("<gold>Running core (${activeCore.size}):"))
        activeCore.forEach { player.sendMessage(mm("  <yellow>${formatBehavior(it, entity)}")) }
    }
    val active = group.active
    if (active.isNotEmpty()) {
        player.sendMessage(mm("<gold>Running (${active.size}):"))
        active.forEach { player.sendMessage(mm("  <yellow>${formatBehavior(it, entity)}")) }
    } else {
        player.sendMessage(mm("<gray>(no normal behavior running)"))
    }

    val cooldowns = EntityNamedCooldown.entriesFor(entity.uuid)
    if (cooldowns.isNotEmpty()) {
        player.sendMessage(mm("<gold>Cooldowns (${cooldowns.size}):"))
        cooldowns.entries.sortedBy { it.key }.forEach { (name, d) ->
            player.sendMessage(mm("  <yellow>$name<gray>: <white>${"%.1f".format(d.toMillis() / 1000.0)}s"))
        }
    }

    val memory = entity.memory.snapshot()
    if (memory.isNotEmpty()) {
        player.sendMessage(mm("<gold>Memory (${memory.size}):"))
        memory.entries.sortedBy { it.key }.forEach { (k, v) ->
            player.sendMessage(mm("  <yellow>$k<gray>: <white>${formatMemoryValue(v)}"))
        }
    }

    if (group.sensors.isNotEmpty()) {
        player.sendMessage(mm("<gold>Sensors (${group.sensors.size}):"))
        group.sensors.forEach {
            player.sendMessage(mm("  <yellow>${it::class.simpleName} <gray>period=<white>${it.period}"))
        }
    }

    if (entity.damageMultipliers.isNotEmpty()) {
        player.sendMessage(mm("<gold>Damage modifiers (${entity.damageMultipliers.size}):"))
        entity.damageMultipliers.entries.sortedBy { it.key.id }.forEach { (e, m) ->
            val label = when {
                m <= 0f -> "<red>immune</red>"
                m < 1f -> "<green>${m}x</green>"
                m > 1f -> "<red>${m}x</red>"
                else -> "${m}x"
            }
            player.sendMessage(mm("  <yellow>${e.id}<gray>: <white>$label"))
        }
    }

    val pack = entity.pack()?.snapshot() ?: emptyMap()
    if (pack.isNotEmpty()) {
        player.sendMessage(mm("<gold>Pack blackboard (${pack.size}):"))
        pack.entries.sortedBy { it.key }.forEach { (k, v) ->
            player.sendMessage(mm("  <yellow>$k<gray>: <white>${formatMemoryValue(v)}"))
        }
    }

    val threats = entity.threatTable?.snapshot() ?: emptyMap()
    if (threats.isNotEmpty()) {
        player.sendMessage(mm("<gold>Threat table (${threats.size}):"))
        threats.entries.sortedByDescending { it.value }.take(8).forEach { (uuid, threat) ->
            player.sendMessage(mm("  <yellow>${uuid.toString().take(8)}<gray>: <white>${"%.1f".format(threat)}"))
        }
    }

    val totalBehaviors = group.coreBehaviors.size + group.behaviors.size
    val running = activeCore.size + active.size
    player.sendMessage(mm("<dark_aqua>━━━ <gray>$running/$totalBehaviors behaviors running, " +
        "${cooldowns.size} cooldowns, ${memory.size} memory, ${group.sensors.size} sensors <dark_aqua>━━━"))
}

private fun formatBehavior(b: Behavior, entity: SmartEntity? = null): String {
    val anim = b.playOnStart?.let { " <gray>anim=<white>$it" } ?: ""
    val core = if (b.core) " <gray>[core]" else ""
    val phase = b.phaseFilter?.let { " <gray>phases=<white>${it.sorted().joinToString(",")}" } ?: ""
    val score = entity?.let { " <gray>score=<white>${"%.1f".format(b.score(it))}" } ?: ""
    return "${b.id}$core <gray>(p=<white>${b.priority}<gray>, w=<white>${b.weight}<gray>)$score$anim$phase"
}

private fun formatMemoryValue(v: Any): String = when (v) {
    is Entity -> "<gray>Entity#<white>${v.entityId}"
    is Point -> "(${"%.1f".format(v.x())}, ${"%.1f".format(v.y())}, ${"%.1f".format(v.z())})"
    else -> v.toString()
}

