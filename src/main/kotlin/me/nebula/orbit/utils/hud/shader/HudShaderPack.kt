package me.nebula.orbit.utils.hud.shader

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

    private fun generateVsh(): String = """
#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
flat out int hudFlag;

const float HUD_CHAR_SIZE = 8.0;
const int HUD_MARKER = 254;
const vec2 HUD_CORNERS[4] = vec2[4](
    vec2(0.0, 0.0),
    vec2(0.0, HUD_CHAR_SIZE),
    vec2(HUD_CHAR_SIZE, HUD_CHAR_SIZE),
    vec2(HUD_CHAR_SIZE, 0.0)
);

void main() {
    vec3 pos = Position;
    hudFlag = 0;

    bool isGui = ProjMat[2][3] == 0.0;
    if (isGui) {
        int blueVal = int(Color.b * 255.0 + 0.5);
        if (blueVal == HUD_MARKER) {
            hudFlag = 1;
            float guiW = 2.0 / ProjMat[0][0];
            float guiH = -2.0 / ProjMat[1][1];
            float targetX = Color.r * guiW;
            float targetY = Color.g * guiH;
            int corner = gl_VertexID % 4;
            pos = vec3(targetX + HUD_CORNERS[corner].x, targetY + HUD_CORNERS[corner].y, pos.z);
        } else if (blueVal == 63 || blueVal == 64) {
            int redVal = int(Color.r * 255.0 + 0.5);
            int greenVal = int(Color.g * 255.0 + 0.5);
            bool grayscale = abs(redVal - blueVal) <= 2 && abs(greenVal - blueVal) <= 2;
            if (!grayscale) {
                hudFlag = 2;
                pos.xy = vec2(-10000.0);
            }
        }
    }

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
""".trimIndent()

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
