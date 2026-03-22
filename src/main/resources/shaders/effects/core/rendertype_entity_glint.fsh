#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
in vec3 viewPos;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }

    vec3 normal = normalize(cross(dFdx(viewPos), dFdy(viewPos)));
    vec3 vDir = normalize(-viewPos);
    vec3 lightDir = normalize(vec3(0.3, 1.0, 0.5));
    vec3 reflected = reflect(-lightDir, normal);

    float specular = pow(max(dot(reflected, vDir), 0.0), 16.0);
    color.rgb += specular * 0.4;
    color.rgb = min(color.rgb, vec3(1.0));

    float fade = (1.0 - total_fog_value(sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd)) * GlintAlpha;
    fragColor = vec4(color.rgb * fade, color.a);
}
