package me.nebula.orbit.utils.modelengine.generator

import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.blueprint.*
import me.nebula.orbit.utils.modelengine.math.eulerToQuat
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Vec
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.CustomModelData

object ModelGenerator {

    private const val MODELS_DIR = "models"

    fun generate(resources: ResourceManager, path: String): GenerationResult {
        val name = path.substringAfterLast('/').substringBeforeLast('.')
        val model = resources.reader("$MODELS_DIR/$path").use { BlockbenchParser.parse(name, it) }
        return generate(model)
    }

    fun generate(model: BlockbenchModel): GenerationResult {
        val raw = generateRaw(model)
        ModelEngine.registerBlueprint(model.name, raw.blueprint)
        val packBytes = PackWriter.write(
            packName = model.name,
            packDescription = "Model: ${model.name}",
            models = raw.boneModels,
            textureBytes = raw.textureBytes,
        )
        return GenerationResult(raw.blueprint, packBytes, raw.boneModels.size)
    }

    fun generateRaw(resources: ResourceManager, path: String): RawGenerationResult {
        val name = path.substringAfterLast('/').substringBeforeLast('.')
        val model = resources.reader("$MODELS_DIR/$path").use { BlockbenchParser.parse(name, it) }
        return generateRaw(model)
    }

    fun generateRaw(model: BlockbenchModel): RawGenerationResult {
        val atlas = AtlasManager.stitch(model.textures)
        val atlasBytes = AtlasManager.toBytes(atlas.image)
        val boneModels = mutableMapOf<String, GeneratedBoneModel>()
        val blueprintBones = mutableMapOf<String, BlueprintBone>()
        val rootBoneNames = mutableListOf<String>()
        val flatGroups by lazy { model.groups.flatMap { flattenGroups(it) } }

        fun processGroup(group: BbGroup, parentName: String?) {
            val childNames = group.children.filterIsInstance<BbGroupChild.SubGroup>().map { it.group.name }
            val elements = group.children.filterIsInstance<BbGroupChild.ElementRef>()
                .mapNotNull { ref -> model.elements.find { it.uuid == ref.uuid } }

            val boneElements = buildBoneElements(elements, group, atlas)

            val texturePath = "modelengine/${model.name}_atlas"
            boneModels["${model.name}/${group.name}"] = GeneratedBoneModel(
                textures = listOf(texturePath),
                elements = boneElements,
            )

            val customModelDataId = ModelIdRegistry.assignId(model.name, group.name)
            val modelItem = ItemStack.of(Material.PAPER).with(
                DataComponents.CUSTOM_MODEL_DATA,
                CustomModelData(listOf(customModelDataId.toFloat()), emptyList(), emptyList(), emptyList()),
            )

            val offset = Vec(
                group.origin.x() / 16.0,
                group.origin.y() / 16.0,
                group.origin.z() / 16.0,
            )

            blueprintBones[group.name] = BlueprintBone(
                name = group.name,
                parentName = parentName,
                childNames = childNames,
                offset = offset,
                rotation = eulerToQuat(
                    group.rotation.x().toFloat(),
                    group.rotation.y().toFloat(),
                    group.rotation.z().toFloat(),
                ),
                scale = Vec(1.0, 1.0, 1.0),
                modelItem = modelItem,
                behaviors = emptyMap(),
                visible = group.visibility,
            )

            if (parentName == null) rootBoneNames += group.name

            group.children.filterIsInstance<BbGroupChild.SubGroup>().forEach {
                processGroup(it.group, group.name)
            }
        }

        model.groups.forEach { processGroup(it, null) }

        val animations = model.animations.associate { anim ->
            anim.name to convertAnimation(anim, flatGroups)
        }

        val blueprint = ModelBlueprint(
            name = model.name,
            bones = blueprintBones,
            rootBoneNames = rootBoneNames,
            animations = animations,
        )

        val textureBytes = mapOf("${model.name}_atlas.png" to atlasBytes)
        return RawGenerationResult(blueprint, boneModels, textureBytes)
    }

    fun buildBoneElements(
        elements: List<BbElement>,
        group: BbGroup,
        atlas: AtlasResult,
    ): List<GeneratedElement> = elements.map { element ->
        val from = floatArrayOf(
            (element.from.x() - group.origin.x()).toFloat() + 8f,
            (element.from.y() - group.origin.y()).toFloat(),
            (element.from.z() - group.origin.z()).toFloat() + 8f,
        )
        val to = floatArrayOf(
            (element.to.x() - group.origin.x()).toFloat() + 8f,
            (element.to.y() - group.origin.y()).toFloat(),
            (element.to.z() - group.origin.z()).toFloat() + 8f,
        )

        val rotation = if (element.rotation != Vec.ZERO) {
            val origin = floatArrayOf(
                (element.origin.x() - group.origin.x()).toFloat() + 8f,
                (element.origin.y() - group.origin.y()).toFloat(),
                (element.origin.z() - group.origin.z()).toFloat() + 8f,
            )
            val (axis, angle) = dominantAxis(element.rotation)
            GeneratedRotation(angle, axis, origin)
        } else null

        val faces = element.faces.mapValues { (_, face) ->
            val uv = if (atlas.entries.isNotEmpty() && face.texture >= 0) {
                val entry = AtlasManager.entryByOriginalIndex(atlas.entries, face.texture)
                if (entry != null) {
                    val texW = entry.image.width.toFloat()
                    val texH = entry.image.height.toFloat()
                    val scaleX = 16f / atlas.width
                    val scaleY = 16f / atlas.height
                    floatArrayOf(
                        face.uv[0] / texW * entry.image.width * scaleX + entry.offsetX * scaleX,
                        face.uv[1] / texH * entry.image.height * scaleY + entry.offsetY * scaleY,
                        face.uv[2] / texW * entry.image.width * scaleX + entry.offsetX * scaleX,
                        face.uv[3] / texH * entry.image.height * scaleY + entry.offsetY * scaleY,
                    )
                } else face.uv
            } else face.uv
            GeneratedFace(uv, 0, face.rotation)
        }

        GeneratedElement(from, to, rotation, faces)
    }

    fun buildFlatModel(model: BlockbenchModel): Pair<GeneratedBoneModel, ByteArray> {
        val atlas = AtlasManager.stitch(model.textures)
        val atlasBytes = AtlasManager.toBytes(atlas.image)
        val allElements = mutableListOf<GeneratedElement>()

        fun collectElements(group: BbGroup) {
            val elements = group.children.filterIsInstance<BbGroupChild.ElementRef>()
                .mapNotNull { ref -> model.elements.find { it.uuid == ref.uuid } }
            allElements += buildBoneElements(elements, group, atlas)
            group.children.filterIsInstance<BbGroupChild.SubGroup>().forEach { collectElements(it.group) }
        }

        model.groups.forEach { collectElements(it) }

        val texturePath = "customcontent/${model.name}_atlas"
        val boneModel = GeneratedBoneModel(
            textures = listOf(texturePath),
            elements = allElements,
        )
        return boneModel to atlasBytes
    }

    private fun convertAnimation(anim: BbAnimation, flatGroups: List<BbGroup>): AnimationBlueprint {
        val boneKeyframes = mutableMapOf<String, BoneKeyframes>()

        anim.animators.forEach { (uuid, animator) ->
            val boneName = flatGroups.find { it.uuid == uuid }?.name ?: animator.name
            val posKf = mutableListOf<Keyframe>()
            val rotKf = mutableListOf<Keyframe>()
            val scaleKf = mutableListOf<Keyframe>()

            animator.keyframes.forEach { kf ->
                val value = kf.dataPoints.firstOrNull() ?: Vec.ZERO
                val interp = when (kf.interpolation.lowercase()) {
                    "catmullrom" -> InterpolationType.CATMULLROM
                    "bezier" -> InterpolationType.BEZIER
                    "step" -> InterpolationType.STEP
                    else -> InterpolationType.LINEAR
                }
                val keyframe = Keyframe(kf.time, value, interp, kf.bezierLeftTime, kf.bezierLeftValue, kf.bezierRightTime, kf.bezierRightValue)
                when (kf.channel) {
                    "position" -> posKf += keyframe
                    "rotation" -> rotKf += keyframe
                    "scale" -> scaleKf += keyframe
                }
            }

            boneKeyframes[boneName] = BoneKeyframes(
                position = posKf.sortedBy { it.time },
                rotation = rotKf.sortedBy { it.time },
                scale = scaleKf.sortedBy { it.time },
            )
        }

        val loop = when (anim.loop.lowercase()) {
            "loop" -> LoopMode.LOOP
            "hold" -> LoopMode.HOLD
            else -> LoopMode.ONCE
        }

        return AnimationBlueprint(anim.name, anim.length, loop, boneKeyframes)
    }

    private fun flattenGroups(group: BbGroup): List<BbGroup> = listOf(group) +
        group.children.filterIsInstance<BbGroupChild.SubGroup>().flatMap { flattenGroups(it.group) }

    private fun dominantAxis(rotation: Vec): Pair<String, Float> {
        val ax = kotlin.math.abs(rotation.x())
        val ay = kotlin.math.abs(rotation.y())
        val az = kotlin.math.abs(rotation.z())
        return when {
            ax >= ay && ax >= az -> "x" to snapAngle(rotation.x().toFloat())
            ay >= ax && ay >= az -> "y" to snapAngle(rotation.y().toFloat())
            else -> "z" to snapAngle(rotation.z().toFloat())
        }
    }

    private val validAngles = floatArrayOf(-45f, -22.5f, 0f, 22.5f, 45f)

    private fun snapAngle(angle: Float): Float =
        validAngles.minByOrNull { kotlin.math.abs(it - angle) } ?: 0f
}

data class RawGenerationResult(
    val blueprint: ModelBlueprint,
    val boneModels: Map<String, GeneratedBoneModel>,
    val textureBytes: Map<String, ByteArray>,
)

data class GenerationResult(
    val blueprint: ModelBlueprint,
    val packBytes: ByteArray,
    val boneCount: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GenerationResult) return false
        return blueprint == other.blueprint && packBytes.contentEquals(other.packBytes) && boneCount == other.boneCount
    }

    override fun hashCode(): Int {
        var result = blueprint.hashCode()
        result = 31 * result + packBytes.contentHashCode()
        result = 31 * result + boneCount
        return result
    }
}
