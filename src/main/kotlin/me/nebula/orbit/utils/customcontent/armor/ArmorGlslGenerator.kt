package me.nebula.orbit.utils.customcontent.armor

import net.minestom.server.coordinate.Vec
import kotlin.math.cos
import kotlin.math.sin

object ArmorGlslGenerator {

    fun generateArmorGlsl(armors: List<RegisteredArmor>): String {
        if (armors.isEmpty()) return ""

        val allStasisValues = armors.flatMap { armor ->
            armor.parsed.pieces.map { it.part.stasis }
        }.distinct().sorted()

        return buildString {
            appendLine("#ifdef VSH")
            generateVshSection(this, armors, allStasisValues)
            appendLine("#endif")
            appendLine()
            appendLine("#ifdef FSH")
            generateFshSection(this, armors, allStasisValues)
            appendLine("#endif")
        }
    }

    fun generateArmorcordsGlsl(armors: List<RegisteredArmor>): String = buildString {
        for (armor in armors) {
            appendLine("COLOR_ARMOR(${armor.colorR},${armor.colorG},${armor.colorB})")
            appendLine("cords = vec2(${armor.colorId}, 0);")
            appendLine("COLOR_ARMOR(${armor.colorR or 1},${armor.colorG},${armor.colorB})")
            appendLine("cords = vec2(${armor.colorId}, 0);")
        }
    }

    private fun generateVshSection(
        sb: StringBuilder,
        armors: List<RegisteredArmor>,
        allStasisValues: List<Int>,
    ) {
        val stasisNames = allStasisValues.joinToString(" || ") { "cube == $it" }
        sb.appendLine("if ($stasisNames) {")
        sb.appendLine("    cems = addCem(cems, cube);")
        sb.appendLine("    removeAll = 1;")
        sb.appendLine("    armorType = int(RVC_0) + int(RVC_1) * 256 + int(RVC_2) * 65536;")
        sb.appendLine("}")
    }

    private fun generateFshSection(
        sb: StringBuilder,
        armors: List<RegisteredArmor>,
        allStasisValues: List<Int>,
    ) {
        for (stasis in allStasisValues) {
            sb.appendLine("case $stasis: {")

            val armorsWithPart = armors.filter { armor ->
                armor.parsed.pieces.any { it.part.stasis == stasis }
            }

            for ((index, armor) in armorsWithPart.withIndex()) {
                val piece = armor.parsed.pieces.first { it.part.stasis == stasis }
                val condition = if (index == 0) "if" else "} else if"
                val armorTypeValue = armor.colorR + armor.colorG * 256 + armor.colorB * 65536
                sb.appendLine("    $condition (armorType == $armorTypeValue) {")

                val texIndex = if (piece.part.layer == 2) 1 else 0
                val tex = armor.parsed.textures.getOrElse(texIndex) { armor.parsed.textures.first() }

                val rotations = mutableMapOf<String, String>()
                for (cube in piece.cubes) {
                    val key = rotationKey(cube.rotationLevels)
                    if (key !in rotations) {
                        val name = "rot${rotations.size}"
                        rotations[key] = name
                        sb.appendLine("        mat3 $name = ${precomputeRotation(cube.rotationLevels, piece.part.signX)};")
                    }
                }

                for (cube in piece.cubes) {
                    val rotName = rotations.getValue(rotationKey(cube.rotationLevels))
                    sb.appendLine("        ${generateCemBox(cube, tex.width, tex.height, armor.colorId, rotName, piece.part, piece.part.isLeft)}")
                }

                val hasEmissive = piece.cubes.any { it.emissive > 0f }
                if (hasEmissive) {
                    sb.appendLine("        dynamicEmissive = 1;")
                }
            }

            if (armorsWithPart.isNotEmpty()) {
                sb.appendLine("    }")
            }

            sb.appendLine("    break;")
            sb.appendLine("}")
        }
    }

    private fun generateCemBox(cube: ArmorCube, texW: Int, texH: Int, colorId: Int, rotName: String, part: ArmorPart, isLeft: Boolean): String {
        val cellOffsetU = colorId * CELL_WIDTH

        var up = formatUv(cube.uvFaces["up"] ?: EMPTY_UV, texW, texH, cellOffsetU)
        var down = formatUv(cube.uvFaces["down"] ?: EMPTY_UV, texW, texH, cellOffsetU)

        if (part.signZ > 0) {
            up = formatUvFlipV(cube.uvFaces["up"] ?: EMPTY_UV, texW, texH, cellOffsetU)
            down = formatUvFlipV(cube.uvFaces["down"] ?: EMPTY_UV, texW, texH, cellOffsetU)
        }
        var north = formatUv(cube.uvFaces["north"] ?: EMPTY_UV, texW, texH, cellOffsetU)
        var east = formatUv(cube.uvFaces["east"] ?: EMPTY_UV, texW, texH, cellOffsetU)
        var south = formatUv(cube.uvFaces["south"] ?: EMPTY_UV, texW, texH, cellOffsetU)
        var west = formatUv(cube.uvFaces["west"] ?: EMPTY_UV, texW, texH, cellOffsetU)

        if (part.signX < 0) {
            val tmp = east; east = west; west = tmp
        }
        if (part.signX > 0) {
            val tmp = north; north = south; south = tmp
        }


        val bakedCenter = if (cube.hasRotation && cube.rotationLevels.size == 1) {
            val level = cube.rotationLevels[0]
            val bbOffset = cube.bbPivotOffset
            val bakeSign = -part.signX
            val rotatedOffset = applyBbRotation(bbOffset, level.components, bakeSign)
            val bbPivRel = level.bbPivotRel
            val newCx = bbPivRel.x() + rotatedOffset.x()
            val newCy = bbPivRel.y() + rotatedOffset.y()
            val newCz = bbPivRel.z() + rotatedOffset.z()
            part.convertCenter(newCx, newCy, newCz)
        } else {
            cube.center
        }

        val pos = formatVec3(bakedCenter)
        val size = formatVec3Pix(cube.halfSize)
        val emissive = if (cube.emissive > 0f) "true" else "false"

        return "CEM_BOX($pos, $size, $rotName, vec3(0), $up, $down, $north, $east, $south, $west, $emissive);"
    }

    private fun precomputeRotation(levels: List<ArmorRotationLevel>, signX: Double = -1.0): String {
        if (levels.isEmpty()) return "mat3(1.0)"
        var m = IDENTITY
        val zSign = if (signX > 0) -1.0 else 1.0
        for (level in levels.reversed()) {
            for (comp in level.components) {
                val angle = if (comp.axis == ArmorRotationComponent.AXIS_Z) zSign * comp.radians else comp.radians
                m = multiply(m, rotationMatrix(angle, comp.axis))
            }
        }
        return formatMat3(m)
    }

    private fun applyBbRotation(offset: Vec, components: List<ArmorRotationComponent>, signFactor: Double = -1.0): Vec {
        var p = offset
        for (comp in components) {
            p = rotateStandard(p, signFactor * comp.radians, comp.axis)
        }
        return p
    }

    private fun rotateStandard(p: Vec, angle: Double, axis: Int): Vec {
        val c = cos(angle)
        val s = sin(angle)
        return when (axis) {
            ArmorRotationComponent.AXIS_X -> Vec(p.x(), p.y() * c - p.z() * s, p.y() * s + p.z() * c)
            ArmorRotationComponent.AXIS_Y -> Vec(p.x() * c + p.z() * s, p.y(), -p.x() * s + p.z() * c)
            ArmorRotationComponent.AXIS_Z -> Vec(p.x() * c - p.y() * s, p.x() * s + p.y() * c, p.z())
            else -> p
        }
    }

    private fun rotationKey(levels: List<ArmorRotationLevel>): String =
        levels.joinToString("|") { level ->
            level.components.joinToString(",") { "${it.radians}:${it.axis}" }
        }

    private fun rotationMatrix(radians: Double, axis: Int): Array<DoubleArray> {
        val c = cos(radians)
        val s = sin(radians)
        return when (axis) {
            ArmorRotationComponent.AXIS_X -> arrayOf(
                doubleArrayOf(1.0, 0.0, 0.0),
                doubleArrayOf(0.0, c, s),
                doubleArrayOf(0.0, -s, c),
            )
            ArmorRotationComponent.AXIS_Y -> arrayOf(
                doubleArrayOf(c, 0.0, -s),
                doubleArrayOf(0.0, 1.0, 0.0),
                doubleArrayOf(s, 0.0, c),
            )
            ArmorRotationComponent.AXIS_Z -> arrayOf(
                doubleArrayOf(c, s, 0.0),
                doubleArrayOf(-s, c, 0.0),
                doubleArrayOf(0.0, 0.0, 1.0),
            )
            else -> IDENTITY
        }
    }

    private fun multiply(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        val r = Array(3) { DoubleArray(3) }
        for (i in 0..2) for (j in 0..2) for (k in 0..2)
            r[i][j] += a[i][k] * b[k][j]
        return r
    }

    private fun formatMat3(m: Array<DoubleArray>): String =
        "mat3(%.6f, %.6f, %.6f, %.6f, %.6f, %.6f, %.6f, %.6f, %.6f)".format(
            m[0][0], m[1][0], m[2][0],
            m[0][1], m[1][1], m[2][1],
            m[0][2], m[1][2], m[2][2],
        )

    private fun formatVec3(v: Vec): String =
        "vec3(%.4f, %.4f, %.4f)".format(v.x(), v.y(), v.z())

    private fun formatVec3Pix(v: Vec): String =
        "vec3(%.4f, %.4f, %.4f)".format(v.x(), v.z(), v.y())

    private fun formatUvFlipV(
        uv: ArmorCubeUv,
        texW: Int,
        texH: Int,
        cellOffsetU: Float,
    ): String {
        val scaleU = CELL_WIDTH / texW.toFloat()
        val scaleV = CELL_HEIGHT / texH.toFloat()
        val u = uv.u * scaleU + cellOffsetU
        val v = (uv.v + uv.height) * scaleV
        val w = uv.width * scaleU
        val h = -uv.height * scaleV
        return "vec4(%.6f, %.6f, %.6f, %.6f)".format(u, v, w, h)
    }

    private fun formatUv(
        uv: ArmorCubeUv,
        texW: Int,
        texH: Int,
        cellOffsetU: Float,
    ): String {
        val scaleU = CELL_WIDTH / texW.toFloat()
        val scaleV = CELL_HEIGHT / texH.toFloat()
        val u = uv.u * scaleU + cellOffsetU
        val v = uv.v * scaleV
        val w = uv.width * scaleU
        val h = uv.height * scaleV
        return "vec4(%.6f, %.6f, %.6f, %.6f)".format(u, v, w, h)
    }

    private const val CELL_WIDTH = 64f
    private const val CELL_HEIGHT = 32f
    private val EMPTY_UV = ArmorCubeUv(0f, 0f, 0f, 0f)

    private val IDENTITY = arrayOf(
        doubleArrayOf(1.0, 0.0, 0.0),
        doubleArrayOf(0.0, 1.0, 0.0),
        doubleArrayOf(0.0, 0.0, 1.0),
    )
}
