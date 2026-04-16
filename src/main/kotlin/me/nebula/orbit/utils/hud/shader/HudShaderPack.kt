package me.nebula.orbit.utils.hud.shader

import me.nebula.orbit.utils.hud.font.HEIGHT_TIERS
import me.nebula.orbit.utils.screen.shader.MapShaderPack

object HudShaderPack {

    fun generate(): Map<String, ByteArray> {
        val mapEntries = MapShaderPack.generate()
        val entries = LinkedHashMap<String, ByteArray>()
        entries["assets/minecraft/shaders/include/map_decode.glsl"] =
            mapEntries.getValue("assets/minecraft/shaders/include/map_decode.glsl")
        entries["assets/minecraft/shaders/core/rendertype_text.vsh"] =
            generateVsh().toByteArray(Charsets.UTF_8)
        entries["assets/minecraft/shaders/core/rendertype_text.fsh"] =
            generateFsh().toByteArray(Charsets.UTF_8)
        return entries
    }

    private fun generateVsh(): String {
        val tierArray = HEIGHT_TIERS.joinToString(", ") { "$it.0" }
        return """
#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
flat out int hudFlag;

const float CELL_WIDTH = 8.0;
const int NUM_TIERS = ${HEIGHT_TIERS.size};
const float TIER_HEIGHTS[NUM_TIERS] = float[NUM_TIERS]($tierArray);

void main() {
    vec3 pos = Position;
    hudFlag = 0;

    bool isGui = ProjMat[2][3] == 0.0;
    vec4 marker = texelFetch(Sampler0, ivec2(0, 0), 0);
    bool isHudAtlas = abs(marker.r - 254.0/255.0) < 0.01 && marker.g < 0.01 && abs(marker.b - 254.0/255.0) < 0.01;
    if (isGui && isHudAtlas) {
        int blueVal = int(Color.b * 255.0 + 0.5);
        if (blueVal >= 128) {
            hudFlag = 1;
            int encoded = blueVal - 128;
            int tierIndex = encoded >> 4;
            int charOffset = encoded & 15;
            float spriteH = (tierIndex < NUM_TIERS) ? TIER_HEIGHTS[tierIndex] : CELL_WIDTH;

            float guiW = 2.0 / ProjMat[0][0];
            float guiH = -2.0 / ProjMat[1][1];
            float baseX = Color.r * guiW;
            float baseY = Color.g * guiH;
            float targetX = baseX + float(charOffset) * CELL_WIDTH;

            int corner = gl_VertexID % 4;
            vec2 cornerOffset;
            if (corner == 0) cornerOffset = vec2(0.0, 0.0);
            else if (corner == 1) cornerOffset = vec2(0.0, spriteH);
            else if (corner == 2) cornerOffset = vec2(CELL_WIDTH, spriteH);
            else cornerOffset = vec2(CELL_WIDTH, 0.0);

            pos = vec3(targetX + cornerOffset.x, baseY + cornerOffset.y, pos.z);
        }
    }

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
""".trimIndent()
    }

    private fun generateFsh(): String = """
#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:map_decode.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
flat in int hudFlag;

out vec4 fragColor;

void main() {
    if (hudFlag == 2) discard;

    if (hudFlag == 1) {
        vec4 texColor = texture(Sampler0, texCoord0);
        if (texColor.a < 0.1) discard;
        fragColor = texColor;
        return;
    }

    ivec2 texSize = textureSize(Sampler0, 0);
    if (texSize == ivec2(128, 128) && isEncodedMap(Sampler0)) {
        fragColor = decodeMapPixel(Sampler0, texCoord0);
        return;
    }

    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) discard;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
""".trimIndent()
}
