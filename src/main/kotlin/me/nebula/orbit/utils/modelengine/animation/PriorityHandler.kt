package me.nebula.orbit.utils.modelengine.animation

import me.nebula.orbit.utils.modelengine.blueprint.AnimationBlueprint
import me.nebula.orbit.utils.modelengine.blueprint.LoopMode
import me.nebula.orbit.utils.modelengine.math.eulerToQuat
import me.nebula.orbit.utils.modelengine.model.ActiveModel
import net.minestom.server.coordinate.Vec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class PriorityHandler : AnimationHandler {

    private val priorityCounter = AtomicInteger(0)
    private val playingAnimations = ConcurrentHashMap<String, PlayingAnimation>()

    private val sortedScratch = ArrayList<Map.Entry<String, PlayingAnimation>>(8)
    private val bonePropertiesScratch = HashMap<String, AnimationProperty>(16)
    private val bonePrioritiesScratch = HashMap<String, Int>(16)
    private val toRemoveScratch = ArrayList<String>(4)
    private val priorityComparator = Comparator<Map.Entry<String, PlayingAnimation>> { a, b ->
        a.value.priority.compareTo(b.value.priority)
    }

    var boundModel: ActiveModel? = null

    private class PlayingAnimation(
        val blueprint: AnimationBlueprint,
        val priority: Int,
        val speed: Float,
        val lerpIn: Float,
        val lerpOut: Float,
        val interpolators: Map<String, BoneInterpolators>,
    ) {
        var time: Float = 0f
        var weight: Float = if (lerpIn > 0f) 0f else 1f
        var stopping: Boolean = false
    }

    data class BoneInterpolators(
        val position: KeyframeInterpolator,
        val rotation: KeyframeInterpolator,
        val scale: KeyframeInterpolator,
    )

    override fun play(animationName: String, lerpIn: Float, lerpOut: Float, speed: Float) {
        val existing = playingAnimations[animationName]
        if (existing != null) {
            existing.stopping = false
            return
        }

        val model = boundModel ?: return
        val animBlueprint = model.blueprint.animations[animationName] ?: return

        val interpolators = animBlueprint.boneKeyframes.mapValues { (_, boneKf) ->
            BoneInterpolators(
                position = KeyframeInterpolator(boneKf.position),
                rotation = KeyframeInterpolator(boneKf.rotation),
                scale = KeyframeInterpolator(boneKf.scale),
            )
        }

        playingAnimations[animationName] = PlayingAnimation(
            blueprint = animBlueprint,
            priority = priorityCounter.getAndIncrement(),
            speed = speed,
            lerpIn = lerpIn,
            lerpOut = lerpOut,
            interpolators = interpolators,
        )
    }

    override fun stop(animationName: String) {
        val anim = playingAnimations[animationName] ?: return
        if (anim.lerpOut > 0f) {
            anim.stopping = true
        } else {
            playingAnimations.remove(animationName)
        }
    }

    override fun stopAll() {
        playingAnimations.clear()
    }

    override fun isPlaying(animationName: String): Boolean =
        playingAnimations.containsKey(animationName)

    override fun tick(model: ActiveModel, deltaSeconds: Float) {
        boundModel = model
        if (playingAnimations.isEmpty()) return

        val boneProperties = bonePropertiesScratch
        val bonePriorities = bonePrioritiesScratch
        val toRemove = toRemoveScratch
        val sorted = sortedScratch
        boneProperties.clear()
        bonePriorities.clear()
        toRemove.clear()
        sorted.clear()
        sorted.addAll(playingAnimations.entries)
        sorted.sortWith(priorityComparator)

        for (idx in sorted.indices) {
            val entry = sorted[idx]
            val name = entry.key
            val anim = entry.value
            anim.time += deltaSeconds * anim.speed

            when (anim.blueprint.loop) {
                LoopMode.LOOP -> {
                    if (anim.blueprint.length > 0f) {
                        anim.time %= anim.blueprint.length
                        if (anim.time < 0f) anim.time += anim.blueprint.length
                    }
                }
                LoopMode.ONCE -> {
                    if (anim.time >= anim.blueprint.length) {
                        toRemove += name
                        continue
                    }
                }
                LoopMode.HOLD -> {
                    anim.time = anim.time.coerceAtMost(anim.blueprint.length)
                }
            }

            if (!anim.stopping && anim.lerpIn > 0f && anim.weight < 1f) {
                anim.weight = (anim.weight + deltaSeconds / anim.lerpIn).coerceAtMost(1f)
            }

            if (anim.stopping) {
                anim.weight = if (anim.lerpOut > 0f) {
                    (anim.weight - deltaSeconds / anim.lerpOut).coerceAtLeast(0f)
                } else 0f
                if (anim.weight <= 0f) {
                    toRemove += name
                    continue
                }
            }

            for (interpEntry in anim.interpolators.entries) {
                val boneName = interpEntry.key
                val interps = interpEntry.value
                val prop = AnimationProperty.fromKeyframes(interps.position, interps.rotation, interps.scale, anim.time)
                val existing = boneProperties[boneName]
                val existingPriority = bonePriorities[boneName] ?: -1

                if (existing == null || anim.priority > existingPriority) {
                    boneProperties[boneName] = if (anim.weight < 1f) {
                        AnimationProperty.blend(AnimationProperty.IDENTITY, prop, anim.weight)
                    } else prop
                    bonePriorities[boneName] = anim.priority
                } else if (anim.weight > 0f) {
                    boneProperties[boneName] = AnimationProperty.blend(existing, prop, anim.weight)
                }
            }
        }

        for (i in toRemove.indices) playingAnimations.remove(toRemove[i])

        for (boneName in boneProperties.keys) {
            model.bones[boneName]?.resetAnimation()
        }

        val illegalModel = model.blueprint.hasIllegalRotations
        for (propEntry in boneProperties.entries) {
            val boneName = propEntry.key
            val prop = propEntry.value
            val bone = model.bones[boneName] ?: continue
            bone.animatedPosition = prop.position
            val boneEuler = bone.blueprint.rotationEuler
            val animRot = if (illegalModel) {
                Vec(-prop.rotationEuler.x(), prop.rotationEuler.y(), -prop.rotationEuler.z())
            } else {
                Vec(prop.rotationEuler.x(), -prop.rotationEuler.y(), -prop.rotationEuler.z())
            }
            val combinedEuler = boneEuler.add(animRot)
            bone.localRotation = eulerToQuat(
                combinedEuler.x().toFloat(),
                combinedEuler.y().toFloat(),
                combinedEuler.z().toFloat(),
            )
            bone.animatedScale = prop.scale
        }
    }
}
