package me.nebula.orbit.utils.modelengine.generator

import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.blueprint.*
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Vec
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

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
        val atlas = AtlasManager.stitch(model.textures, cropTo = model.resolution)
        val atlasBytes = AtlasManager.toBytes(atlas.image)
        val boneModels = mutableMapOf<String, GeneratedBoneModel>()
        val blueprintBones = mutableMapOf<String, BlueprintBone>()
        val rootBoneNames = mutableListOf<String>()
        val flatGroups by lazy { model.groups.flatMap { flattenGroups(it) } }

        fun processGroup(group: BbGroup, parentName: String?, parentOrigin: Vec) {
            val childNames = group.children.filterIsInstance<BbGroupChild.SubGroup>()
                .filter { it.group.visibility }
                .map { it.group.name }
            val elements = group.children.filterIsInstance<BbGroupChild.ElementRef>()
                .mapNotNull { ref -> model.elements.find { it.uuid == ref.uuid } }
                .filter { it.visibility }

            val (boneElements, boneModelScale) = buildBoneElements(elements, group, atlas, model.resolution)

            val modelItem = if (boneElements.isNotEmpty()) {
                val lowerModelName = model.name.lowercase()
                val lowerBoneName = group.name.lowercase()
                val boneKey = "me_${lowerModelName}_${lowerBoneName}"
                val texturePath = "minecraft:me_${lowerModelName}_atlas"
                boneModels[boneKey] = GeneratedBoneModel(
                    textures = listOf(texturePath),
                    elements = boneElements,
                )
                val itemModelValue = "minecraft:$boneKey"
                ItemStack.of(Material.PAPER).with(DataComponents.ITEM_MODEL, itemModelValue)
            } else null

            val convertedEuler = Vec(-group.rotation.x(), group.rotation.y(), -group.rotation.z())

            blueprintBones[group.name] = BlueprintBone(
                name = group.name,
                parentName = parentName,
                childNames = childNames,
                offset = BbConverter.boneOffset(group.origin, parentOrigin),
                rotation = BbConverter.boneRotation(group.rotation),
                rotationEuler = convertedEuler,
                scale = Vec(1.0, 1.0, 1.0),
                modelItem = modelItem,
                behaviors = emptyMap(),
                visible = group.visibility,
                modelScale = boneModelScale,
            )

            if (parentName == null) rootBoneNames += group.name

            group.children.filterIsInstance<BbGroupChild.SubGroup>()
                .filter { it.group.visibility }
                .forEach { processGroup(it.group, group.name, group.origin) }
        }

        model.groups.filter { it.visibility }.forEach { processGroup(it, null, Vec.ZERO) }

        val animations = model.animations.associate { anim ->
            anim.name to convertAnimation(anim, flatGroups)
        }

        val blueprint = ModelBlueprint(
            name = model.name,
            bones = blueprintBones,
            rootBoneNames = rootBoneNames,
            animations = animations,
        )

        val textureBytes = mapOf("me_${model.name.lowercase()}_atlas.png" to atlasBytes)
        return RawGenerationResult(blueprint, boneModels, textureBytes)
    }

    private const val MAX_ELEMENT_EXTENT = 24f

    data class BoneElementResult(val elements: List<GeneratedElement>, val modelScale: Float)

    fun buildBoneElements(
        elements: List<BbElement>,
        group: BbGroup,
        atlas: AtlasResult,
        resolution: BbResolution,
        centerOffset: Float = 8f,
    ): BoneElementResult {
        if (elements.isEmpty()) return BoneElementResult(emptyList(), 1f)

        var maxAbs = 0f
        elements.forEach { element ->
            val inf = element.inflate.toDouble()
            val rel = element.from.sub(inf, inf, inf).sub(group.origin)
            val relTo = element.to.add(inf, inf, inf).sub(group.origin)
            maxAbs = maxOf(maxAbs,
                kotlin.math.abs(rel.x().toFloat()), kotlin.math.abs(rel.y().toFloat()), kotlin.math.abs(rel.z().toFloat()),
                kotlin.math.abs(relTo.x().toFloat()), kotlin.math.abs(relTo.y().toFloat()), kotlin.math.abs(relTo.z().toFloat()),
            )
        }

        val modelScale = if (maxAbs > MAX_ELEMENT_EXTENT) maxAbs / MAX_ELEMENT_EXTENT else 1f
        val inv = 1f / modelScale

        val maxU = resolution.width.toFloat()
        val maxV = resolution.height.toFloat()

        val generated = elements.map { element ->
            val inf = element.inflate.toDouble()
            val inflatedFrom = element.from.sub(inf, inf, inf)
            val inflatedTo = element.to.add(inf, inf, inf)
            val (from, to) = BbConverter.elementCoords(inflatedFrom, inflatedTo, group.origin, inv, centerOffset)

            val rotation = if (element.rotation != Vec.ZERO) {
                val origin = BbConverter.rotationOrigin(element.origin, group.origin, inv, centerOffset)
                val (axis, angle) = dominantAxis(element.rotation)
                GeneratedRotation(angle, axis, origin)
            } else null

            val faces = element.faces
                .filter { (_, face) ->
                    face.texture >= 0 &&
                        kotlin.math.abs(face.uv[2] - face.uv[0]) > 0.001f &&
                        kotlin.math.abs(face.uv[3] - face.uv[1]) > 0.001f &&
                        face.uv[0] in 0f..maxU && face.uv[1] in 0f..maxV &&
                        face.uv[2] in 0f..maxU && face.uv[3] in 0f..maxV
                }
                .mapValues { (_, face) ->
                    val uv = bbUvToMcUv(face, atlas, resolution)
                    GeneratedFace(uv, 0, face.rotation)
                }

            GeneratedElement(from, to, rotation, faces)
        }

        return BoneElementResult(generated, modelScale)
    }

    private fun bbUvToMcUv(face: BbFace, atlas: AtlasResult, resolution: BbResolution): FloatArray {
        if (atlas.entries.isEmpty()) {
            val sx = 16f / resolution.width
            val sy = 16f / resolution.height
            return floatArrayOf(face.uv[0] * sx, face.uv[1] * sy, face.uv[2] * sx, face.uv[3] * sy)
        }

        val entry = AtlasManager.entryByOriginalIndex(atlas.entries, face.texture) ?: run {
            val sx = 16f / resolution.width
            val sy = 16f / resolution.height
            return floatArrayOf(face.uv[0] * sx, face.uv[1] * sy, face.uv[2] * sx, face.uv[3] * sy)
        }

        val imgScaleX = entry.image.width.toFloat() / resolution.width
        val imgScaleY = entry.image.height.toFloat() / resolution.height
        val sx = 16f / atlas.width
        val sy = 16f / atlas.height
        return floatArrayOf(
            (face.uv[0] * imgScaleX + entry.offsetX) * sx,
            (face.uv[1] * imgScaleY + entry.offsetY) * sy,
            (face.uv[2] * imgScaleX + entry.offsetX) * sx,
            (face.uv[3] * imgScaleY + entry.offsetY) * sy,
        )
    }

    fun buildFlatModel(model: BlockbenchModel): Pair<GeneratedBoneModel, ByteArray> {
        val atlas = AtlasManager.stitch(model.textures)
        val atlasBytes = AtlasManager.toBytes(atlas.image)
        val allElements = mutableListOf<GeneratedElement>()

        fun collectElements(group: BbGroup) {
            val elements = group.children.filterIsInstance<BbGroupChild.ElementRef>()
                .mapNotNull { ref -> model.elements.find { it.uuid == ref.uuid } }
                .filter { it.visibility }
            allElements += buildBoneElements(elements, group, atlas, model.resolution).elements
            group.children.filterIsInstance<BbGroupChild.SubGroup>()
                .filter { it.group.visibility }
                .forEach { collectElements(it.group) }
        }

        model.groups.filter { it.visibility }.forEach { collectElements(it) }

        val texturePath = "customcontent/${model.name.lowercase()}_atlas"
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
