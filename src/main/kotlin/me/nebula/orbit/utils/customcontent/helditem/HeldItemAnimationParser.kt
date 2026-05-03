package me.nebula.orbit.utils.customcontent.helditem

import me.nebula.orbit.utils.modelengine.generator.BbAnimation
import me.nebula.orbit.utils.modelengine.generator.BbElement
import me.nebula.orbit.utils.modelengine.generator.BbGroup
import me.nebula.orbit.utils.modelengine.generator.BbGroupChild
import me.nebula.orbit.utils.modelengine.generator.BbKeyframe
import me.nebula.orbit.utils.modelengine.generator.BlockbenchModel
import net.minestom.server.coordinate.Vec

object HeldItemAnimationParser {

    private const val ROOT_PARENT_ID: Int = -1

    fun parse(mainModel: BlockbenchModel, animModel: BlockbenchModel? = null): ParsedHeldItem {
        val elementsByUuid = mainModel.elements.associateBy { it.uuid }
        val bones = mutableListOf<HeldItemBone>()
        val boneIdByUuid = mutableMapOf<String, Int>()

        fun visit(group: BbGroup, parentId: Int) {
            val id = bones.size
            boneIdByUuid[group.uuid] = id
            val cubes = group.children
                .filterIsInstance<BbGroupChild.ElementRef>()
                .mapNotNull { elementsByUuid[it.uuid] }
                .filter { it.visibility }
                .map { toHeldItemCube(it) }

            bones += HeldItemBone(
                id = id,
                name = group.name,
                parentId = parentId,
                pivot = Vec(group.origin.x(), group.origin.y(), group.origin.z()),
                baseRotation = Vec(group.rotation.x(), group.rotation.y(), group.rotation.z()),
                cubes = cubes,
            )
            group.children.filterIsInstance<BbGroupChild.SubGroup>()
                .filter { it.group.visibility }
                .forEach { visit(it.group, id) }
        }

        mainModel.groups.filter { it.visibility }.forEach { visit(it, ROOT_PARENT_ID) }

        val mainAnimations = mainModel.animations.mapNotNull { anim -> convertAnimation(anim, boneIdByUuid) }

        val boneIdByName = bones.groupBy { it.name }
            .mapValues { (_, v) -> v.first().id }

        val companionAnimations = animModel?.let { resolveCompanionAnimations(it, boneIdByName) } ?: emptyList()

        val textures = mainModel.textures.map {
            HeldItemTexture(it.id, it.name, it.width, it.height, it.source)
        }

        val companionNames = companionAnimations.map { it.name }.toSet()
        val mergedAnimations = mainAnimations.filter { it.name !in companionNames } + companionAnimations

        val displaySlots = mainModel.display.mapValues { (_, slot) ->
            HeldItemDisplaySlot(slot.rotation, slot.translation, slot.scale)
        }

        return ParsedHeldItem(
            id = mainModel.name,
            bones = bones,
            animations = mergedAnimations,
            textures = textures,
            resolutionWidth = mainModel.resolution.width,
            resolutionHeight = mainModel.resolution.height,
            displaySlots = displaySlots,
        )
    }

    private fun resolveCompanionAnimations(
        animModel: BlockbenchModel,
        boneIdByName: Map<String, Int>,
    ): List<HeldItemAnimation> {
        val uuidToName = buildMap {
            fun walk(g: BbGroup) {
                put(g.uuid, g.name)
                g.children.filterIsInstance<BbGroupChild.SubGroup>().forEach { walk(it.group) }
            }
            animModel.groups.forEach { walk(it) }
        }

        return animModel.animations.mapNotNull { anim ->
            val tracks = mutableMapOf<Int, HeldItemBoneTrack>()
            for ((uuid, animator) in anim.animators) {
                val boneName = uuidToName[uuid] ?: animator.name
                val boneId = boneIdByName[boneName] ?: continue
                val pos = mutableListOf<HeldItemKeyframe>()
                val rot = mutableListOf<HeldItemKeyframe>()
                val scl = mutableListOf<HeldItemKeyframe>()
                for (kf in animator.keyframes) {
                    val raw = kf.dataPoints.firstOrNull() ?: Vec.ZERO
                    when (kf.channel.lowercase()) {
                        "position" -> pos += toKeyframe(kf, raw)
                        "rotation" -> rot += toKeyframe(kf, raw)
                        "scale" -> scl += toKeyframe(kf, raw)
                    }
                }
                val track = HeldItemBoneTrack(
                    position = pos.sortedBy { it.time },
                    rotation = rot.sortedBy { it.time },
                    scale = scl.sortedBy { it.time },
                )
                if (!track.isEmpty) tracks[boneId] = track
            }
            if (tracks.isEmpty()) return@mapNotNull null

            HeldItemAnimation(
                name = anim.name,
                trigger = triggerFromName(anim.name),
                length = anim.length,
                loopMode = loopModeFrom(anim.loop),
                tracks = tracks,
            )
        }
    }

    private fun toHeldItemCube(el: BbElement): HeldItemCube {
        val faces = el.faces.map { (dir, face) ->
            HeldItemCubeFace(
                direction = dir,
                uMin = face.uv[0],
                vMin = face.uv[1],
                uMax = face.uv[2],
                vMax = face.uv[3],
                rotation = face.rotation,
                textureIndex = face.texture.coerceAtLeast(0),
            )
        }
        return HeldItemCube(
            from = el.from,
            to = el.to,
            origin = el.origin,
            rotation = el.rotation,
            inflate = el.inflate,
            faces = faces,
        )
    }

    private fun convertAnimation(anim: BbAnimation, boneIdByUuid: Map<String, Int>): HeldItemAnimation? {
        val tracks = mutableMapOf<Int, HeldItemBoneTrack>()
        for ((uuid, animator) in anim.animators) {
            val boneId = boneIdByUuid[uuid] ?: continue
            val pos = mutableListOf<HeldItemKeyframe>()
            val rot = mutableListOf<HeldItemKeyframe>()
            val scl = mutableListOf<HeldItemKeyframe>()
            for (kf in animator.keyframes) {
                val raw = kf.dataPoints.firstOrNull() ?: Vec.ZERO
                when (kf.channel.lowercase()) {
                    "position" -> pos += toKeyframe(kf, raw)
                    "rotation" -> rot += toKeyframe(kf, raw)
                    "scale" -> scl += toKeyframe(kf, raw)
                }
            }
            val track = HeldItemBoneTrack(
                position = pos.sortedBy { it.time },
                rotation = rot.sortedBy { it.time },
                scale = scl.sortedBy { it.time },
            )
            if (!track.isEmpty) tracks[boneId] = track
        }
        if (tracks.isEmpty()) return null

        return HeldItemAnimation(
            name = anim.name,
            trigger = triggerFromName(anim.name),
            length = anim.length,
            loopMode = loopModeFrom(anim.loop),
            tracks = tracks,
        )
    }

    private fun toKeyframe(kf: BbKeyframe, value: Vec): HeldItemKeyframe = HeldItemKeyframe(
        time = kf.time,
        value = value,
        interpolation = when (kf.interpolation.lowercase()) {
            "step" -> AnimationInterpolation.STEP
            "catmullrom" -> AnimationInterpolation.CATMULLROM
            "bezier" -> AnimationInterpolation.BEZIER
            else -> AnimationInterpolation.LINEAR
        },
        bezierLeftTime = kf.bezierLeftTime,
        bezierLeftValue = kf.bezierLeftValue,
        bezierRightTime = kf.bezierRightTime,
        bezierRightValue = kf.bezierRightValue,
    )

    private fun triggerFromName(name: String): AnimationTrigger {
        val lower = name.lowercase()
        return when {
            lower.startsWith("swing") || lower.startsWith("attack") -> AnimationTrigger.SWING
            lower.startsWith("use") || lower.startsWith("right") -> AnimationTrigger.USE
            else -> AnimationTrigger.IDLE
        }
    }

    private fun loopModeFrom(loop: String): AnimationLoopMode = when (loop.lowercase()) {
        "loop" -> AnimationLoopMode.LOOP
        "hold" -> AnimationLoopMode.HOLD
        else -> AnimationLoopMode.ONCE
    }
}
