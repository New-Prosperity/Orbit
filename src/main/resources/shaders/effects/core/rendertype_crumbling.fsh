#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

#define PARALLAX_DEPTH 0.4
#define PARALLAX_STEPS 12

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec2 texCoord2;
in vec3 viewDir;
in vec3 surfaceNormal;

out vec4 fragColor;

void main() {
    vec3 normal = normalize(surfaceNormal);
    vec3 view = normalize(viewDir);

    vec3 tangent;
    if (abs(normal.y) > 0.9) {
        tangent = normalize(cross(normal, vec3(0, 0, 1)));
    } else {
        tangent = normalize(cross(normal, vec3(0, 1, 0)));
    }
    vec3 bitangent = cross(normal, tangent);

    vec3 viewTangent = vec3(dot(view, tangent), dot(view, bitangent), dot(view, normal));

    vec2 parallaxOffset = viewTangent.xy / max(viewTangent.z, 0.1) * PARALLAX_DEPTH;
    vec2 uv = texCoord0;

    float stepSize = 1.0 / float(PARALLAX_STEPS);
    float depth = 0.0;
    vec2 stepOffset = parallaxOffset * stepSize;

    for (int i = 0; i < PARALLAX_STEPS; i++) {
        vec4 sample_ = texture(Sampler0, uv);
        if (sample_.a > 0.1) break;
        uv += stepOffset;
        depth += stepSize;
    }

    vec4 color = texture(Sampler0, uv) * vertexColor;

    if (depth > 0.0 && color.a > 0.1) {
        color.rgb *= 1.0 - depth * 0.3;
    }

    if (color.a < 0.1) {
        discard;
    }
    color = color * ColorModulator;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
