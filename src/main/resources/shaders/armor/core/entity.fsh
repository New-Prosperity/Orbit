#version 330

#define FSH

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:matf.glsl>


#moj_import <emissive_utils.glsl>
#moj_import <mods/armor/armorparts.glsl>
#moj_import <mods/parts.glsl>
#moj_import <map_decode.glsl>

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 normal;
in vec4 lightMapColor;
in vec4 tintColor;
in vec2 uv;
in vec4 cem_color;
flat in float emissive;
in float fogAlpha;
flat in int bodypart;
flat in ivec4 cems;
flat in float cem_size;
flat in int isGui;
flat in ivec2 RelativeCords;
flat in int markforremove;
flat in int isTrim;
flat in int armorType;
flat in int isEnchantedArmor;
in vec4 overlayColor;

out vec4 fragColor;

#moj_import <cem/frag_funcs.glsl>

#define def_speed 0.22/120

vec4 relativeUv(float x, float y, float xlength, float ylength, ivec2 cord) {
    return vec4(x + cord.x * 64.0, y + cord.y * 32.0, xlength, ylength);
}

void main() {
    if(markforremove == 1) discard;
    gl_FragDepth = gl_FragCoord.z;

    if (isEncodedMap(Sampler0)) {
        fragColor = decodeMapPixel(Sampler0, texCoord0);
        return;
    }

    vec4 faceVertexColor = vertexColor;

#ifdef DISSOLVE
    if (faceVertexColor.a < texture(DissolveMaskSampler, texCoord0).a) {
        discard;
    }
    faceVertexColor.a = 1.0;
#endif

    vec2 texSize = textureSize(Sampler0, 0);
    bool isHead = (texCoord0 * texSize).y <= 16;
    vec4 color = texture(Sampler0, texCoord0);
    #ifndef NO_OVERLAY
        vec4 oldColor = color;

        color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
        if (color.rgb == vec3(0.0) && oldColor.rgb != vec3(0.0)) {
            color = oldColor;
        }
    #endif
    float dynamicEmissive = emissive;

    if (cem > 1) {
        if (isGui == 1) discard;
        #define MINUS_Z
        #moj_import <cem/frag_main_setup.glsl>
        modelSize /= res.y;
        modelSize *= cem_size;

        mat3 rotMat = PIY25;
        if (bodypart != -1) {
            for (int cemId = 0; cemId < 2; cemId++) {
                int cemi = cems[cemId];
                if (cemi == -1){
                    continue;
                }
                switch (cemi) {
                    #moj_import <mods/armor/armor.glsl>
                }

            }
        } else {
            discard;
        }

        if (minT == MAX_DEPTH) discard;
        writeDepth(dir * minT);
        float opacity = ceil(color.a * 255);
        if (dynamicEmissive == 0 && opacity != 128) color *= cem_light;

        #ifndef NO_OVERLAY
            color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
        #endif

        if (isEnchantedArmor == 1) {
            float t = GameTime * 1000.0;
            vec2 glintUV = gl_FragCoord.xy * 0.015;

            float g1 = sin((glintUV.x + glintUV.y) * 1.5 + t) * 0.5 + 0.5;
            float g2 = sin((glintUV.x - glintUV.y) * 2.0 - t * 0.6) * 0.5 + 0.5;
            float glint = pow(g1, 2.0) * 0.3 + pow(g2, 2.5) * 0.2;

            color.rgb += glint * vec3(0.45, 0.25, 0.8);
        }
    }

    float opacity = ceil(color.a * 255);
    if (cem < 1) {
        #ifdef ALPHA_CUTOUT
            if (color.a < ALPHA_CUTOUT) {
                discard;
            }
        #endif

        if (opacity == 254) {
            float animationTime = GameTime * 1000;
            color = mix(color * faceVertexColor, color, pow(sin(animationTime + texCoord0.x * 1.1), 2));
        } else if (opacity==128){

            color *= faceVertexColor;

        }else {

            color *= faceVertexColor;
            #ifndef EMISSIVE
                color *= lightMapColor;
            #endif
        }
    }
    if (dynamicEmissive == 0) color *= ColorModulator;

    if (dynamicEmissive == 1) {
        fragColor = color;
    } else {
        fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
    }
}
