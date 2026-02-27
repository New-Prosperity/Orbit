package me.nebula.orbit.utils.customcontent.armor

import net.minestom.server.coordinate.Vec

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
            }

            if (armorsWithPart.isNotEmpty()) {
                sb.appendLine("    }")
            }

            sb.appendLine("    break;")
            sb.appendLine("}")
        }
    }

    private fun generateAddBox(cube: ArmorCube, texW: Int, texH: Int, colorId: Int): String {
        val pos = formatVec3(cube.center)
        val size = formatVec3(cube.halfSize)

        val cellOffsetU = colorId * CELL_WIDTH
        val dFace = cube.uvFaces["south"] ?: EMPTY_UV
        val uFace = cube.uvFaces["north"] ?: EMPTY_UV
        val nFace = cube.uvFaces["up"] ?: EMPTY_UV
        val eFace = cube.uvFaces["east"] ?: EMPTY_UV
        val sFace = cube.uvFaces["down"] ?: EMPTY_UV
        val wFace = cube.uvFaces["west"] ?: EMPTY_UV

        val dSide = formatUv(dFace, texW, texH, cellOffsetU)
        val uSide = formatUv(uFace, texW, texH, cellOffsetU)
        val nSide = formatUv(nFace, texW, texH, cellOffsetU)
        val eSide = formatUv(eFace, texW, texH, cellOffsetU)
        val sSide = formatUv(sFace, texW, texH, cellOffsetU)
        val wSide = formatUv(wFace, texW, texH, cellOffsetU)

        val dRot = uFace.rotation
        val uRot = dFace.rotation
        val nRot = nFace.rotation
        val eRot = wFace.rotation
        val sRot = sFace.rotation
        val wRot = eFace.rotation

        return when {
            !cube.hasRotation -> {
                "ADD_BOX_WITH_ROTATION($pos, $size, $dSide, $uSide, $nSide, $eSide, $sSide, $wSide, $dRot, $uRot, $nRot, $eRot, $sRot, $wRot);"
            }
            cube.rotationLevels.size == 1 -> {
                val level = cube.rotationLevels[0]
                val rotation = buildRotationMatrix(level.components)
                val pivot = formatVec3(level.pivot)
                "ADD_BOX_WITH_ROTATION_ROTATE($pos, $size, $rotation, $pivot, $dSide, $uSide, $nSide, $eSide, $sSide, $wSide, $dRot, $uRot, $nRot, $eRot, $sRot, $wRot);"
            }
            else -> generateMultiLevelRotation(
                cube, pos, size,
                dSide, uSide, nSide, eSide, sSide, wSide,
                dRot, uRot, nRot, eRot, sRot, wRot,
            )
        }
    }

    private fun generateMultiLevelRotation(
        cube: ArmorCube,
        pos: String,
        size: String,
        dSide: String, uSide: String, nSide: String,
        eSide: String, sSide: String, wSide: String,
        dRot: Int, uRot: Int, nRot: Int,
        eRot: Int, sRot: Int, wRot: Int,
    ): String = buildString {
        val levels = cube.rotationLevels
        appendLine("{")
        for ((i, level) in levels.withIndex()) {
            appendLine("mat3 _R$i = ${buildRotationMatrix(level.components)};")
        }
        val composedR = levels.indices.reversed().joinToString(" * ") { "_R$it" }
        appendLine("mat3 _Rc = $composedR;")
        appendLine("vec3 _p = _R0 * (-center + ($pos + ${formatVec3(levels[0].pivot)}) * modelSize);")
        for (i in 1 until levels.size) {
            appendLine("_p = _R$i * (_p - ${formatVec3(levels[i - 1].pivot)} * modelSize + ${formatVec3(levels[i].pivot)} * modelSize);")
        }
        appendLine("_p = _p - ${formatVec3(levels.last().pivot)} * modelSize;")
        appendLine("color = sBoxWithRotation(_p, _Rc * dirTBN, $size * modelSize, TBN * inverse(_Rc), color, minT, $dSide, $uSide, $nSide, $eSide, $sSide, $wSide, $dRot, $uRot, $nRot, $eRot, $sRot, $wRot, false);")
        append("}")
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

    private fun formatUv(uv: ArmorCubeUv, texW: Int, texH: Int, cellOffsetU: Float): String {
        val scaleU = CELL_WIDTH / texW.toFloat()
        val scaleV = CELL_HEIGHT / texH.toFloat()
        return "vec4(%.6f, %.6f, %.6f, %.6f)".format(
            uv.u * scaleU + cellOffsetU,
            uv.v * scaleV,
            uv.width * scaleU,
            uv.height * scaleV,
        )
    }

    private const val CELL_WIDTH = 64f
    private const val CELL_HEIGHT = 32f
    private val AXIS_NAMES = arrayOf("X", "Y", "Z")
    private val EMPTY_UV = ArmorCubeUv(0f, 0f, 0f, 0f)
}
