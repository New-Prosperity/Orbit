package me.nebula.orbit.utils.customcontent.armor

import me.nebula.orbit.utils.modelengine.generator.BbElement
import me.nebula.orbit.utils.modelengine.generator.BbGroup
import me.nebula.orbit.utils.modelengine.generator.BbGroupChild
import me.nebula.orbit.utils.modelengine.generator.BlockbenchModel
import net.minestom.server.coordinate.Vec

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
            val cubes = collectCubes(group, elementsByUuid, group.origin, emptyList(), part, output)
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
                val adjusted = adjustCubesForPart(mismatched, part, altPart)
                output.add(ParsedArmorPiece(altPart, adjusted))
            }
        }
    }

    private fun adjustCubesForPart(
        cubes: List<ArmorCube>,
        from: ArmorPart,
        to: ArmorPart,
    ): List<ArmorCube> {
        val dz = to.cemYOffset - from.cemYOffset
        val dx = from.cemXOffset - to.cemXOffset
        if (dz == 0.0 && dx == 0.0) return cubes
        return cubes.map { cube ->
            cube.copy(
                center = Vec(cube.center.x() + dx, cube.center.y(), cube.center.z() + dz),
                rotationLevels = cube.rotationLevels.map { level ->
                    level.copy(pivot = Vec(level.pivot.x() + dx, level.pivot.y(), level.pivot.z() + dz))
                },
            )
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
        part: ArmorPart,
        piecesOutput: MutableList<ParsedArmorPiece>,
    ): List<ArmorCube> {
        val cubes = mutableListOf<ArmorCube>()

        for (child in group.children) {
            when (child) {
                is BbGroupChild.ElementRef -> {
                    val element = elementsByUuid[child.uuid] ?: continue
                    if (!element.visibility) continue
                    cubes.add(convertElement(element, boneOrigin, parentTransforms, part))
                }
                is BbGroupChild.SubGroup -> {
                    val subPart = ArmorPart.fromBoneName(child.group.name)
                    if (subPart != null) {
                        val subCubes = collectCubes(child.group, elementsByUuid, child.group.origin, emptyList(), subPart, piecesOutput)
                        splitByTextureLayer(subPart, subCubes, piecesOutput)
                    } else {
                        val subRot = child.group.rotation
                        val newTransforms = if (subRot.x() != 0.0 || subRot.y() != 0.0 || subRot.z() != 0.0) {
                            parentTransforms + GroupTransform(child.group.origin, subRot)
                        } else {
                            parentTransforms
                        }
                        cubes.addAll(collectCubes(child.group, elementsByUuid, boneOrigin, newTransforms, part, piecesOutput))
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
        part: ArmorPart,
    ): ArmorCube {
        val from = element.from
        val to = element.to

        val centerBb = Vec(
            (from.x() + to.x()) / 2.0,
            (from.y() + to.y()) / 2.0,
            (from.z() + to.z()) / 2.0,
        )

        val cx = centerBb.x() - boneOrigin.x()
        val cy = centerBb.y() - boneOrigin.y()
        val cz = centerBb.z() - boneOrigin.z()
        val s = if (part.isLeft) -1.0 else 1.0
        val center = Vec(s * cx - part.cemXOffset, -cz, -cy + part.cemYOffset)

        val inflate = element.inflate.toDouble()
        val halfSize = Vec(
            (to.x() - from.x()) / 2.0 + inflate,
            (to.z() - from.z()) / 2.0 + inflate,
            (to.y() - from.y()) / 2.0 + inflate,
        )

        val levels = buildRotationLevels(element, boneOrigin, parentTransforms, part)

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
            emissive = element.lightEmission / 15f,
        )
    }

    private fun bbToTbnPivot(origin: Vec, boneOrigin: Vec, s: Double, part: ArmorPart): Vec {
        val px = origin.x() - boneOrigin.x()
        val py = origin.y() - boneOrigin.y()
        val pz = origin.z() - boneOrigin.z()
        return Vec(s * px - part.cemXOffset, -pz, -py + part.cemYOffset)
    }

    private fun buildRotationLevels(
        element: BbElement,
        boneOrigin: Vec,
        parentTransforms: List<GroupTransform>,
        part: ArmorPart,
    ): List<ArmorRotationLevel> {
        val s = if (part.isLeft) -1.0 else 1.0
        val levels = mutableListOf<ArmorRotationLevel>()

        val elemComponents = mutableListOf<ArmorRotationComponent>()
        addEulerComponents(elemComponents, element.rotation)
        if (elemComponents.isNotEmpty()) {
            levels.add(ArmorRotationLevel(elemComponents, bbToTbnPivot(element.origin, boneOrigin, s, part)))
        }

        for (transform in parentTransforms.reversed()) {
            val groupComponents = mutableListOf<ArmorRotationComponent>()
            addEulerComponents(groupComponents, transform.rotation)
            if (groupComponents.isNotEmpty()) {
                levels.add(ArmorRotationLevel(groupComponents, bbToTbnPivot(transform.origin, boneOrigin, s, part)))
            }
        }

        return levels
    }

    private fun addEulerComponents(
        output: MutableList<ArmorRotationComponent>,
        bbRotation: Vec,
    ) {
        val tbnX = -bbRotation.x()
        val tbnZ = -bbRotation.y()
        val tbnY = -bbRotation.z()

        if (tbnX != 0.0) output.add(ArmorRotationComponent(Math.toRadians(tbnX), ArmorRotationComponent.AXIS_X))
        if (tbnZ != 0.0) output.add(ArmorRotationComponent(Math.toRadians(tbnZ), ArmorRotationComponent.AXIS_Z))
        if (tbnY != 0.0) output.add(ArmorRotationComponent(Math.toRadians(tbnY), ArmorRotationComponent.AXIS_Y))
    }

}
