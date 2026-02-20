package me.nebula.orbit.utils.modelengine.animation

import me.nebula.orbit.utils.modelengine.blueprint.AnimationBlueprint
import me.nebula.orbit.utils.modelengine.blueprint.LoopMode
import me.nebula.orbit.utils.modelengine.model.ActiveModel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class PriorityHandler : AnimationHandler {

    private val priorityCounter = AtomicInteger(0)
    private val playingAnimations = ConcurrentHashMap<String, PlayingAnimation>()

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
        if (existing != null && !existing.stopping) return

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

        val boneProperties = mutableMapOf<String, AnimationProperty>()
        val bonePriorities = mutableMapOf<String, Int>()

        val sorted = playingAnimations.entries.sortedBy { it.value.priority }
        val toRemove = mutableListOf<String>()

        for ((name, anim) in sorted) {
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

            anim.interpolators.forEach { (boneName, interps) ->
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

        toRemove.forEach { playingAnimations.remove(it) }

        boneProperties.forEach { (boneName, _) ->
            model.bones[boneName]?.resetAnimation()
        }

        boneProperties.forEach { (boneName, prop) ->
            val bone = model.bones[boneName] ?: return@forEach
            bone.animatedPosition = prop.position
            bone.animatedRotation = prop.rotation
            bone.animatedScale = prop.scale
        }
    }
}
