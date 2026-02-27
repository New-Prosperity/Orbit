package me.nebula.orbit.utils.screen.shader

import me.nebula.orbit.utils.screen.encoder.MAGIC_BYTES

object MapShaderPack {

    private val BASE_COLORS = arrayOf(
        intArrayOf(127, 178, 56),
        intArrayOf(247, 233, 163),
        intArrayOf(199, 199, 199),
        intArrayOf(255, 0, 0),
        intArrayOf(160, 160, 255),
        intArrayOf(167, 167, 167),
        intArrayOf(0, 124, 0),
        intArrayOf(255, 255, 255),
        intArrayOf(164, 168, 184),
        intArrayOf(151, 109, 77),
        intArrayOf(112, 112, 112),
        intArrayOf(64, 64, 255),
        intArrayOf(143, 119, 72),
        intArrayOf(255, 252, 245),
        intArrayOf(216, 127, 51),
        intArrayOf(178, 76, 216),
        intArrayOf(102, 153, 216),
        intArrayOf(229, 229, 51),
        intArrayOf(127, 204, 25),
        intArrayOf(242, 127, 165),
        intArrayOf(76, 76, 76),
        intArrayOf(153, 153, 153),
        intArrayOf(76, 127, 153),
        intArrayOf(127, 63, 178),
        intArrayOf(51, 76, 178),
        intArrayOf(102, 76, 51),
        intArrayOf(102, 127, 51),
        intArrayOf(153, 51, 51),
        intArrayOf(25, 25, 25),
        intArrayOf(250, 238, 77),
        intArrayOf(92, 219, 213),
        intArrayOf(74, 128, 255),
    )

    private val SHADE_MULTIPLIERS = intArrayOf(180, 220, 255, 135)

    fun generate(): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        entries["assets/minecraft/shaders/include/map_decode.glsl"] =
            generateMapDecodeGlsl().toByteArray(Charsets.UTF_8)
        entries["assets/minecraft/shaders/core/rendertype_text.fsh"] =
            generateRenderTypeTextFsh().toByteArray(Charsets.UTF_8)
        entries["assets/minecraft/shaders/core/rendertype_text.vsh"] =
            generateRenderTypeTextVsh().toByteArray(Charsets.UTF_8)
        return entries
    }

    fun paletteColor(mapColorId: Int): Triple<Int, Int, Int> {
        val baseIndex = mapColorId / 4 - 1
        val shadeIndex = mapColorId % 4
        require(baseIndex in BASE_COLORS.indices) { "Invalid map color ID: $mapColorId" }
        val base = BASE_COLORS[baseIndex]
        val mult = SHADE_MULTIPLIERS[shadeIndex]
        return Triple(
            base[0] * mult / 255,
            base[1] * mult / 255,
            base[2] * mult / 255,
        )
    }

    private fun generatePaletteGlsl(): String {
        val sb = StringBuilder()
        sb.appendLine("#define MAP_PALETTE_SIZE 128")
        sb.appendLine("const vec3 MAP_PALETTE[MAP_PALETTE_SIZE] = vec3[MAP_PALETTE_SIZE](")
        for (i in 0 until 128) {
            val mapId = i + 4
            val (r, g, b) = paletteColor(mapId)
            val comma = if (i < 127) "," else ""
            sb.appendLine("    vec3(${r.toFloat() / 255f}, ${g.toFloat() / 255f}, ${b.toFloat() / 255f})$comma")
        }
        sb.appendLine(");")
        return sb.toString()
    }

    private fun generateMapDecodeGlsl(): String = buildString {
        appendLine(generatePaletteGlsl())
        appendLine()
        appendLine("const int MAGIC_0 = ${MAGIC_BYTES[0] - 4};")
        appendLine("const int MAGIC_1 = ${MAGIC_BYTES[1] - 4};")
        appendLine("const int MAGIC_2 = ${MAGIC_BYTES[2] - 4};")
        appendLine("const int MAGIC_3 = ${MAGIC_BYTES[3] - 4};")
        appendLine()
        appendLine("""
int reverseMapColor(vec3 rgb) {
    float bestDist = 1.0;
    int bestIdx = 0;
    for (int i = 0; i < MAP_PALETTE_SIZE; i++) {
        vec3 diff = rgb - MAP_PALETTE[i];
        float d = dot(diff, diff);
        if (d < bestDist) {
            bestDist = d;
            bestIdx = i;
        }
    }
    return bestIdx;
}

bool isEncodedMap(sampler2D tex) {
    ivec2 ts = textureSize(tex, 0);
    if (ts != ivec2(128, 128)) return false;
    int b0 = reverseMapColor(texelFetch(tex, ivec2(0, 0), 0).rgb);
    int b1 = reverseMapColor(texelFetch(tex, ivec2(1, 0), 0).rgb);
    int b2 = reverseMapColor(texelFetch(tex, ivec2(0, 1), 0).rgb);
    int b3 = reverseMapColor(texelFetch(tex, ivec2(1, 1), 0).rgb);
    return b0 == MAGIC_0 && b1 == MAGIC_1 && b2 == MAGIC_2 && b3 == MAGIC_3;
}

vec4 decodeMapPixel(sampler2D tex, vec2 uv) {
    ivec2 texel = ivec2(floor(uv * 128.0));
    int cellX = texel.x / 2;
    int cellY = texel.y / 2;
    ivec2 cellBase = ivec2(cellX * 2, cellY * 2);

    int b00 = reverseMapColor(texelFetch(tex, cellBase + ivec2(0, 0), 0).rgb);
    int b10 = reverseMapColor(texelFetch(tex, cellBase + ivec2(1, 0), 0).rgb);
    int b01 = reverseMapColor(texelFetch(tex, cellBase + ivec2(0, 1), 0).rgb);
    int b11 = reverseMapColor(texelFetch(tex, cellBase + ivec2(1, 1), 0).rgb);

    int blue  = b00 | ((b11 & 1) << 7);
    int green = b10 | (((b11 >> 1) & 1) << 7);
    int red   = b01 | (((b11 >> 2) & 1) << 7);

    return vec4(float(red) / 255.0, float(green) / 255.0, float(blue) / 255.0, 1.0);
}
""".trimIndent())
    }

    private fun generateRenderTypeTextFsh(): String = """
#version 150

#moj_import <minecraft:fog.glsl>
#moj_import <map_decode.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    ivec2 texSize = textureSize(Sampler0, 0);
    if (texSize == ivec2(128, 128) && isEncodedMap(Sampler0)) {
        fragColor = decodeMapPixel(Sampler0, texCoord0);
        return;
    }

    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
""".trimIndent()

    private fun generateRenderTypeTextVsh(): String = """
#version 150

#moj_import <minecraft:fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vec3 pos = Position;
    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
""".trimIndent()
}
