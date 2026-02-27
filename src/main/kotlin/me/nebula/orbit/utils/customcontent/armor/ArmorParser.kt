package me.nebula.orbit.utils.customcontent.armor

import me.nebula.orbit.utils.modelengine.generator.BbElement
import me.nebula.orbit.utils.modelengine.generator.BbGroup
import me.nebula.orbit.utils.modelengine.generator.BbGroupChild
import me.nebula.orbit.utils.modelengine.generator.BlockbenchModel
import net.minestom.server.coordinate.Vec
import kotlin.math.cos
import kotlin.math.sin

object ArmorParser {

    fun parse(model: BlockbenchModel): ParsedArmor {
        val elementsByUuid = model.elements.associateBy { it.uuid }
        val pieces = mutableListOf<ParsedArmorPiece>()

        for (group in model.groups) {
            collectPieces(group, elementsByUuid, pieces)
        }

        val merged = mergePieces(pieces)

        return ParsedArmor(
            id = model.name,
            pieces = merged,
            textures = model.textures.map { ArmorTexture(it.width, it.height, it.source) },
        )
    }

    private fun collectPieces(
        group: BbGroup,
        elementsByUuid: Map<String, BbElement>,
        output: MutableList<ParsedArmorPiece>,
    ) {
        val part = ArmorPart.fromBoneName(group.name)
        if (part != null) {
            val cubes = collectCubes(group, elementsByUuid, group.origin, emptyList(), part.isLeft, output)
            splitByTextureLayer(part, cubes, output)
            return
        }

        for (child in group.children) {
            when (child) {
                is BbGroupChild.SubGroup -> collectPieces(child.group, elementsByUuid, output)
                is BbGroupChild.ElementRef -> {}
            }
        }
    }

    private fun splitByTextureLayer(
        part: ArmorPart,
        cubes: List<ArmorCube>,
        output: MutableList<ParsedArmorPiece>,
    ) {
        if (cubes.isEmpty()) return

        val expectedTexIndex = if (part.layer == 2) 1 else 0
        val matching = cubes.filter { it.textureIndex == expectedTexIndex }
        val mismatched = cubes.filter { it.textureIndex != expectedTexIndex }

        if (matching.isNotEmpty()) {
            output.add(ParsedArmorPiece(part, matching))
        }

        if (mismatched.isNotEmpty()) {
            val altPart = part.alternateLayerPart()
            if (altPart != null) {
                output.add(ParsedArmorPiece(altPart, mismatched))
            }
        }
    }

    private fun mergePieces(pieces: List<ParsedArmorPiece>): List<ParsedArmorPiece> =
        pieces.groupBy { it.part }
            .map { (part, grouped) -> ParsedArmorPiece(part, grouped.flatMap { it.cubes }) }

    private data class GroupTransform(
        val origin: Vec,
        val rotation: Vec,
    )

    private fun collectCubes(
        group: BbGroup,
        elementsByUuid: Map<String, BbElement>,
        boneOrigin: Vec,
        parentTransforms: List<GroupTransform>,
        isLeft: Boolean,
        piecesOutput: MutableList<ParsedArmorPiece>,
    ): List<ArmorCube> {
        val cubes = mutableListOf<ArmorCube>()

        for (child in group.children) {
            when (child) {
                is BbGroupChild.ElementRef -> {
                    val element = elementsByUuid[child.uuid] ?: continue
                    if (!element.visibility) continue
                    cubes.add(convertElement(element, boneOrigin, parentTransforms, isLeft))
                }
                is BbGroupChild.SubGroup -> {
                    val subPart = ArmorPart.fromBoneName(child.group.name)
                    if (subPart != null) {
                        val subCubes = collectCubes(child.group, elementsByUuid, child.group.origin, emptyList(), subPart.isLeft, piecesOutput)
                        splitByTextureLayer(subPart, subCubes, piecesOutput)
                    } else {
                        val subRot = child.group.rotation
                        val newTransforms = if (subRot.x() != 0.0 || subRot.y() != 0.0 || subRot.z() != 0.0) {
                            parentTransforms + GroupTransform(child.group.origin, subRot)
                        } else {
                            parentTransforms
                        }
                        cubes.addAll(collectCubes(child.group, elementsByUuid, boneOrigin, newTransforms, isLeft, piecesOutput))
                    }
                }
            }
        }

        return cubes
    }

    private fun convertElement(
        element: BbElement,
        boneOrigin: Vec,
        parentTransforms: List<GroupTransform>,
        isLeft: Boolean,
    ): ArmorCube {
        val from = element.from
        val to = element.to

        var centerBb = Vec(
            (from.x() + to.x()) / 2.0,
            (from.y() + to.y()) / 2.0,
            (from.z() + to.z()) / 2.0,
        )

        for (transform in parentTransforms.asReversed()) {
            centerBb = rotatePointAround(centerBb, transform.origin, transform.rotation)
        }

        val cx = centerBb.x() - boneOrigin.x()
        val cy = centerBb.y() - boneOrigin.y()
        val cz = centerBb.z() - boneOrigin.z()
        val s = if (isLeft) -1.0 else 1.0
        val center = Vec(s * cx, s * -cz, cy)

        val inflate = element.inflate.toDouble()
        val halfSize = Vec(
            (to.x() - from.x()) / 2.0 + inflate,
            (to.z() - from.z()) / 2.0 + inflate,
            (to.y() - from.y()) / 2.0 + inflate,
        )

        val levels = buildRotationLevels(element, boneOrigin, parentTransforms, isLeft)

        val uvFaces = element.faces.mapValues { (_, face) ->
            ArmorCubeUv(
                u = face.uv[0],
                v = face.uv[1],
                width = face.uv[2] - face.uv[0],
                height = face.uv[3] - face.uv[1],
                rotation = face.rotation / 90,
            )
        }

        val textureIndex = element.faces.values.firstOrNull()
            ?.texture?.coerceAtLeast(0) ?: 0

        return ArmorCube(
            center = center,
            halfSize = halfSize,
            rotationLevels = levels,
            uvFaces = uvFaces,
            textureIndex = textureIndex,
        )
    }

    private fun buildRotationLevels(
        element: BbElement,
        boneOrigin: Vec,
        parentTransforms: List<GroupTransform>,
        isLeft: Boolean,
    ): List<ArmorRotationLevel> {
        val levels = mutableListOf<ArmorRotationLevel>()
        val s = if (isLeft) -1.0 else 1.0

        for (transform in parentTransforms) {
            val components = mutableListOf<ArmorRotationComponent>()
            addEulerComponents(components, transform.rotation, isLeft)
            if (components.isNotEmpty()) {
                val gpx = transform.origin.x() - boneOrigin.x()
                val gpy = transform.origin.y() - boneOrigin.y()
                val gpz = transform.origin.z() - boneOrigin.z()
                levels.add(ArmorRotationLevel(components, Vec(s * gpx, s * -gpz, gpy)))
            }
        }

        val elemComponents = mutableListOf<ArmorRotationComponent>()
        addEulerComponents(elemComponents, element.rotation, isLeft)
        if (elemComponents.isNotEmpty()) {
            val epx = element.origin.x() - boneOrigin.x()
            val epy = element.origin.y() - boneOrigin.y()
            val epz = element.origin.z() - boneOrigin.z()
            levels.add(ArmorRotationLevel(elemComponents, Vec(s * epx, s * -epz, epy)))
        }

        return levels
    }

    private fun addEulerComponents(
        output: MutableList<ArmorRotationComponent>,
        bbRotation: Vec,
        isLeft: Boolean,
    ) {
        val s = if (isLeft) -1.0 else 1.0
        val tbnY = s * bbRotation.z()
        val tbnZ = bbRotation.y()
        val tbnX = s * -bbRotation.x()

        if (tbnY != 0.0) output.add(ArmorRotationComponent(Math.toRadians(tbnY), ArmorRotationComponent.AXIS_Y))
        if (tbnZ != 0.0) output.add(ArmorRotationComponent(Math.toRadians(tbnZ), ArmorRotationComponent.AXIS_Z))
        if (tbnX != 0.0) output.add(ArmorRotationComponent(Math.toRadians(tbnX), ArmorRotationComponent.AXIS_X))
    }

    private fun rotatePointAround(point: Vec, origin: Vec, eulerDegrees: Vec): Vec {
        val offset = point.sub(origin)
        val rotated = rotateByEulerZYX(offset, eulerDegrees)
        return rotated.add(origin)
    }

    private fun rotateByEulerZYX(point: Vec, angles: Vec): Vec {
        var p = point
        if (angles.x() != 0.0) p = rotateAroundAxis(p, Math.toRadians(angles.x()), 0)
        if (angles.y() != 0.0) p = rotateAroundAxis(p, Math.toRadians(angles.y()), 1)
        if (angles.z() != 0.0) p = rotateAroundAxis(p, Math.toRadians(angles.z()), 2)
        return p
    }

    private fun rotateAroundAxis(point: Vec, radians: Double, axis: Int): Vec {
        val c = cos(radians)
        val s = sin(radians)
        return when (axis) {
            0 -> Vec(point.x(), point.y() * c - point.z() * s, point.y() * s + point.z() * c)
            1 -> Vec(point.x() * c + point.z() * s, point.y(), -point.x() * s + point.z() * c)
            2 -> Vec(point.x() * c - point.y() * s, point.x() * s + point.y() * c, point.z())
            else -> point
        }
    }
}
