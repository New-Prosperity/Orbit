package me.nebula.orbit.utils.modelengine.generator

import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.customcontent.pack.ObfuscationCodec
import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.blueprint.AnimationBlueprint
import me.nebula.orbit.utils.modelengine.blueprint.BlueprintBone
import me.nebula.orbit.utils.modelengine.blueprint.BoneBehaviorType
import me.nebula.orbit.utils.modelengine.blueprint.BoneKeyframes
import me.nebula.orbit.utils.modelengine.blueprint.InterpolationType
import me.nebula.orbit.utils.modelengine.blueprint.Keyframe
import me.nebula.orbit.utils.modelengine.blueprint.LoopMode
import me.nebula.orbit.utils.modelengine.blueprint.ModelBlueprint
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Vec
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import kotlin.math.abs

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

        val hitboxDimensions = findHitboxDimensions(model)

        fun registerBoneModel(boneName: String, elements: List<GeneratedElement>): ItemStack? {
            if (elements.isEmpty()) return null
            val lowerModelName = model.name.lowercase()
            val boneKey = ObfuscationCodec.obfuscate("me_${lowerModelName}_${boneName.lowercase()}")
            val atlasKey = ObfuscationCodec.obfuscate("me_${lowerModelName}_atlas")
            val texturePath = "minecraft:x/$atlasKey"
            boneModels[boneKey] = GeneratedBoneModel(
                textures = listOf(texturePath),
                elements = elements,
                display = model.display,
            )
            return ItemStack.of(Material.PAPER).with(DataComponents.ITEM_MODEL, "minecraft:$boneKey")
        }

        fun processGroup(group: BbGroup, parentName: String?, parentOrigin: Vec) {
            val isHitboxGroup = isHitboxBoneName(group.name)
            val isSeatGroup = isSeatBoneName(group.name)
            val parentIsSeat = parentName != null && isSeatBoneName(parentName)
            val isSeatScopedHitbox = isHitboxGroup && parentIsSeat
            val skipRendering = isHitboxGroup || isSeatScopedHitbox
            val childNames = group.children.filterIsInstance<BbGroupChild.SubGroup>()
                .filter { it.group.visibility }
                .map { it.group.name }
            val elements = if (skipRendering) emptyList() else group.children.filterIsInstance<BbGroupChild.ElementRef>()
                .mapNotNull { ref -> model.elements.find { it.uuid == ref.uuid } }
                .filter { it.visibility }

            val (boneElements, boneModelScale) = buildBoneElements(elements, group, atlas, model.resolution)
            val modelItem = if (skipRendering) null else registerBoneModel(group.name, boneElements)

            val convertedEuler = Vec(-group.rotation.x(), group.rotation.y(), -group.rotation.z())

            val behaviors: Map<BoneBehaviorType, Map<String, Any>> = if (isSeatGroup) {
                val (sw, sh) = seatHitboxDimensions(model, group)
                mapOf(BoneBehaviorType.MOUNT to mapOf("width" to sw, "height" to sh))
            } else {
                emptyMap()
            }

            blueprintBones[group.name] = BlueprintBone(
                name = group.name,
                parentName = parentName,
                childNames = childNames,
                offset = BbConverter.boneOffset(group.origin, parentOrigin),
                rotation = BbConverter.boneRotation(group.rotation),
                rotationEuler = convertedEuler,
                scale = Vec(1.0, 1.0, 1.0),
                modelItem = modelItem,
                behaviors = behaviors,
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

        val modelHasIllegal = model.elements.any { elem ->
            if (elem.rotation == Vec.ZERO) return@any false
            val axes = floatArrayOf(elem.rotation.x().toFloat(), elem.rotation.y().toFloat(), elem.rotation.z().toFloat())
            val nonZero = axes.count { abs(it) > 0.001f }
            if (nonZero > 1) return@any true
            val angle = axes.first { abs(it) > 0.001f }
            VALID_ANGLES.none { abs(it - angle) < 0.01f }
        }

        val blueprint = ModelBlueprint(
            name = model.name,
            bones = blueprintBones,
            rootBoneNames = rootBoneNames,
            animations = animations,
            hitboxWidth = hitboxDimensions?.first ?: 1f,
            hitboxHeight = hitboxDimensions?.second ?: 2f,
            hasIllegalRotations = modelHasIllegal,
        )

        val atlasKey = ObfuscationCodec.obfuscate("me_${model.name.lowercase()}_atlas")
        val textureBytes = mapOf("x/$atlasKey.png" to atlasBytes)
        return RawGenerationResult(blueprint, boneModels, textureBytes)
    }

    private val VALID_ANGLES = floatArrayOf(-45f, -22.5f, 0f, 22.5f, 45f)
    private const val MAX_ELEMENT_EXTENT = 24f

    private fun isHitboxBoneName(name: String): Boolean = name.lowercase() == "hitbox"

    private fun isSeatBoneName(name: String): Boolean {
        val lower = name.lowercase()
        return lower == "seat" || lower.startsWith("seat_")
    }

    private fun findHitboxDimensions(model: BlockbenchModel): Pair<Float, Float>? {
        val hitboxGroup = findHitboxGroup(model.groups) ?: return null
        val elements = hitboxGroup.children.filterIsInstance<BbGroupChild.ElementRef>()
            .mapNotNull { ref -> model.elements.find { it.uuid == ref.uuid } }
        if (elements.isEmpty()) return null

        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        var minZ = Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE
        for (e in elements) {
            minX = minOf(minX, e.from.x(), e.to.x()); maxX = maxOf(maxX, e.from.x(), e.to.x())
            minY = minOf(minY, e.from.y(), e.to.y()); maxY = maxOf(maxY, e.from.y(), e.to.y())
            minZ = minOf(minZ, e.from.z(), e.to.z()); maxZ = maxOf(maxZ, e.from.z(), e.to.z())
        }
        val widthX = (maxX - minX) / PIXELS_PER_BLOCK
        val widthZ = (maxZ - minZ) / PIXELS_PER_BLOCK
        val height = (maxY - minY) / PIXELS_PER_BLOCK
        val width = maxOf(widthX, widthZ).toFloat()
        return width to height.toFloat()
    }

    private fun findHitboxGroup(groups: List<BbGroup>): BbGroup? {
        for (g in groups) {
            if (isHitboxBoneName(g.name)) return g
            if (isSeatBoneName(g.name)) continue
            val sub = findHitboxGroup(g.children.filterIsInstance<BbGroupChild.SubGroup>().map { it.group })
            if (sub != null) return sub
        }
        return null
    }

    private fun seatHitboxDimensions(model: BlockbenchModel, seatGroup: BbGroup): Pair<Float, Float> {
        val nestedHitbox = seatGroup.children.filterIsInstance<BbGroupChild.SubGroup>()
            .firstOrNull { isHitboxBoneName(it.group.name) }?.group
        val source = nestedHitbox ?: seatGroup
        val elements = source.children.filterIsInstance<BbGroupChild.ElementRef>()
            .mapNotNull { ref -> model.elements.find { it.uuid == ref.uuid } }
        if (elements.isEmpty()) return DEFAULT_SEAT_WIDTH to DEFAULT_SEAT_HEIGHT

        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        var minZ = Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE
        for (e in elements) {
            minX = minOf(minX, e.from.x(), e.to.x()); maxX = maxOf(maxX, e.from.x(), e.to.x())
            minY = minOf(minY, e.from.y(), e.to.y()); maxY = maxOf(maxY, e.from.y(), e.to.y())
            minZ = minOf(minZ, e.from.z(), e.to.z()); maxZ = maxOf(maxZ, e.from.z(), e.to.z())
        }
        val widthX = (maxX - minX) / PIXELS_PER_BLOCK
        val widthZ = (maxZ - minZ) / PIXELS_PER_BLOCK
        val height = (maxY - minY) / PIXELS_PER_BLOCK
        val width = maxOf(widthX, widthZ).toFloat().coerceAtLeast(MIN_SEAT_DIM)
        return width to height.toFloat().coerceAtLeast(MIN_SEAT_DIM)
    }

    private const val PIXELS_PER_BLOCK = 16.0
    private const val DEFAULT_SEAT_WIDTH = 0.6f
    private const val DEFAULT_SEAT_HEIGHT = 0.6f
    private const val MIN_SEAT_DIM = 0.1f

    data class BoneElementResult(val elements: List<GeneratedElement>, val modelScale: Float)

    fun buildBoneElements(
        elements: List<BbElement>,
        group: BbGroup,
        atlas: AtlasResult,
        resolution: BbResolution,
        centerOffset: Float = 8f,
    ): BoneElementResult = buildBoneElements(elements, group.origin, atlas, resolution, centerOffset)

    fun buildBoneElements(
        elements: List<BbElement>,
        boneOrigin: Vec,
        atlas: AtlasResult,
        resolution: BbResolution,
        centerOffset: Float = 8f,
    ): BoneElementResult {
        if (elements.isEmpty()) return BoneElementResult(emptyList(), 1f)

        var maxAbs = 0f
        elements.forEach { element ->
            val inf = element.inflate.toDouble()
            val rel = element.from.sub(inf, inf, inf).sub(boneOrigin)
            val relTo = element.to.add(inf, inf, inf).sub(boneOrigin)
            maxAbs = maxOf(maxAbs,
                abs(rel.x().toFloat()), abs(rel.y().toFloat()), abs(rel.z().toFloat()),
                abs(relTo.x().toFloat()), abs(relTo.y().toFloat()), abs(relTo.z().toFloat()),
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
            val (from, to) = BbConverter.elementCoords(inflatedFrom, inflatedTo, boneOrigin, inv, centerOffset)

            val rotation = if (element.rotation != Vec.ZERO) {
                val origin = BbConverter.rotationOrigin(element.origin, boneOrigin, inv, centerOffset)
                val rx = element.rotation.x().toFloat()
                val ry = element.rotation.y().toFloat()
                val rz = element.rotation.z().toFloat()
                val nonZeroCount = (if (abs(rx) > 0.001f) 1 else 0) +
                    (if (abs(ry) > 0.001f) 1 else 0) +
                    (if (abs(rz) > 0.001f) 1 else 0)
                if (nonZeroCount > 1) {
                    GeneratedRotation(0f, "x", origin, euler = floatArrayOf(rx, ry, rz))
                } else when {
                    abs(rx) > 0.001f -> GeneratedRotation(rx, "x", origin)
                    abs(ry) > 0.001f -> GeneratedRotation(ry, "y", origin)
                    else -> GeneratedRotation(rz, "z", origin)
                }
            } else null

            val faces = element.faces
                .filter { (_, face) ->
                    face.texture >= 0 &&
                        abs(face.uv[2] - face.uv[0]) > 0.001f &&
                        abs(face.uv[3] - face.uv[1]) > 0.001f &&
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

    fun buildFlatModel(
        model: BlockbenchModel,
        elementFilter: (BbElement) -> Boolean = { true },
    ): Pair<GeneratedBoneModel, ByteArray> {
        val atlas = AtlasManager.stitch(model.textures)
        val atlasBytes = AtlasManager.toBytes(atlas.image)
        val allElements = mutableListOf<GeneratedElement>()

        fun collectElements(group: BbGroup) {
            val elements = group.children.filterIsInstance<BbGroupChild.ElementRef>()
                .mapNotNull { ref -> model.elements.find { it.uuid == ref.uuid } }
                .filter { it.visibility && elementFilter(it) }
            allElements += buildBoneElements(elements, group, atlas, model.resolution).elements
            group.children.filterIsInstance<BbGroupChild.SubGroup>()
                .filter { it.group.visibility }
                .forEach { collectElements(it.group) }
        }

        model.groups.filter { it.visibility }.forEach { collectElements(it) }

        val obfTexture = ObfuscationCodec.obfuscate("cc_${model.name.lowercase()}_atlas")
        val texturePath = "x/$obfTexture"
        val boneModel = GeneratedBoneModel(
            textures = listOf(texturePath),
            elements = allElements,
            display = model.display,
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
