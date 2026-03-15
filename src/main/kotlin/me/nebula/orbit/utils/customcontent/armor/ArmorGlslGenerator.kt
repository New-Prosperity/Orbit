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

                for (cube in piece.cubes) {
                    sb.appendLine("        ${generateAddBox(cube, tex.width, tex.height, armor.colorId)}")
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

    private fun generateAddBox(cube: ArmorCube, texW: Int, texH: Int, colorId: Int): String {
        val cellOffsetU = colorId * CELL_WIDTH

        val dSide = formatUv(cube.uvFaces["up"] ?: EMPTY_UV, texW, texH, cellOffsetU)
        val uSide = formatUv(cube.uvFaces["down"] ?: EMPTY_UV, texW, texH, cellOffsetU)
        val nSide = formatUv(cube.uvFaces["north"] ?: EMPTY_UV, texW, texH, cellOffsetU)
        val eSide = formatUv(cube.uvFaces["east"] ?: EMPTY_UV, texW, texH, cellOffsetU)
        val sSide = formatUv(cube.uvFaces["south"] ?: EMPTY_UV, texW, texH, cellOffsetU)
        val wSide = formatUv(cube.uvFaces["west"] ?: EMPTY_UV, texW, texH, cellOffsetU)

        val center = if (cube.hasRotation) bakeRotatedCenter(cube.center, cube.rotationLevels) else cube.center
        val pos = formatVec3(center)
        val size = formatVec3Pix(cube.halfSize)

        val rotation = if (cube.hasRotation) {
            val matrices = cube.rotationLevels.reversed().map { buildRotationMatrix(it.components) }
            "PIX * ${matrices.joinToString(" * ")}"
        } else {
            "PIX"
        }

        return "ADD_BOX_EXT_WITH_ROTATION_ROTATE($pos, $size, $rotation, vec3(0, 0, 0), $dSide, $uSide, $nSide, $eSide, $sSide, $wSide, 0, 0, 0, 0, 0, 0);"
    }

    private fun bakeRotatedCenter(center: Vec, levels: List<ArmorRotationLevel>): Vec {
        var pos = center
        for (level in levels) {
            val offset = pos.sub(level.pivot)
            pos = level.pivot.add(applyForwardRotation(offset, level.components))
        }
        return pos
    }

    private fun applyForwardRotation(point: Vec, components: List<ArmorRotationComponent>): Vec {
        var p = point
        for (comp in components) {
            p = rotateAroundAxis(p, -comp.radians, comp.axis)
        }
        return p
    }

    private fun rotateAroundAxis(p: Vec, angle: Double, axis: Int): Vec {
        val c = cos(angle)
        val s = sin(angle)
        return when (axis) {
            ArmorRotationComponent.AXIS_X -> Vec(p.x(), p.y() * c - p.z() * s, p.y() * s + p.z() * c)
            ArmorRotationComponent.AXIS_Y -> Vec(p.x() * c + p.z() * s, p.y(), -p.x() * s + p.z() * c)
            ArmorRotationComponent.AXIS_Z -> Vec(p.x() * c - p.y() * s, p.x() * s + p.y() * c, p.z())
            else -> p
        }
    }

    private fun buildRotationMatrix(components: List<ArmorRotationComponent>): String {
        if (components.isEmpty()) return "Rotate3(0, X)"

        val parts = components.map { comp ->
            "Rotate3(%.6f, ${AXIS_NAMES[comp.axis]})".format(comp.radians)
        }

        return when {
            parts.size == 1 -> parts[0]
            else -> parts.joinToString(" * ")
        }
    }

    private fun formatVec3(v: Vec): String =
        "vec3(%.4f, %.4f, %.4f)".format(v.x(), v.y(), v.z())

    private fun formatVec3Pix(v: Vec): String =
        "vec3(%.4f, %.4f, %.4f)".format(v.x(), v.z(), v.y())

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
    private val AXIS_NAMES = arrayOf("X", "Y", "Z")
    private val EMPTY_UV = ArmorCubeUv(0f, 0f, 0f, 0f)
}
