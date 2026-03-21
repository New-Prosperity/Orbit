#version 150

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

vec3 particleBoxIntersect(vec3 ro, vec3 rd, vec3 size, out vec3 outNormal) {
    vec3 m = 1.0 / rd;
    vec3 n = m * ro;
    vec3 k = abs(m) * size;
    vec3 t1 = -n - k;
    vec3 t2 = -n + k;
    float tN = max(max(t1.x, t1.y), t1.z);
    float tF = min(min(t2.x, t2.y), t2.z);
    if (tN > tF || tF < 0.0) return vec3(0, 0, -1);
    outNormal = -sign(rd) * step(t1.yzx, t1.xyz) * step(t1.zxy, t1.xyz);
    float t = tN > 0.0 ? tN : tF;
    vec3 pos = (ro + rd * t) / size;
    vec2 tex;
    bvec3 isAxis = bvec3(abs(outNormal.x) > 0.9, abs(outNormal.y) > 0.9, abs(outNormal.z) > 0.9);
    tex = isAxis.x ? pos.zy : (isAxis.y ? pos.xz : pos.xy);
    return vec3(clamp(tex * 0.5 + 0.5, vec2(0), vec2(1)), t);
}

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;

    vec2 atlasSize = vec2(textureSize(Sampler0, 0));
    if (atlasSize.x > 64 && atlasSize.y > 64 && color.a >= 0.1) {
        vec2 center = vec2(0.5);
        vec3 ro = vec3((texCoord0 - center) * 2.0, -1.0);
        vec3 rd = vec3(0, 0, 1);
        vec3 cubeSize = vec3(0.8);

        vec3 normal;
        vec3 hit = particleBoxIntersect(ro, rd, cubeSize, normal);

        if (hit.z > 0.0) {
            vec4 cubeColor = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
            float shade = 0.6 + 0.4 * max(dot(normal, normalize(vec3(0.3, 1.0, 0.5))), 0.0);
            cubeColor.rgb *= shade;
            color = cubeColor;
        }
    }

    if (color.a < 0.1) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
