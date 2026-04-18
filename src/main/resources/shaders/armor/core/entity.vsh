#version 330

#define VSH

#moj_import <minecraft:globals.glsl>

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

#moj_import <emissive_utils.glsl>
#moj_import <mods/armor/armorparts.glsl>
#moj_import <mods/parts.glsl>

#define INV_TEX_RES_SIX (1.0 / 64)
#define INV_TEX_RES_THREE (1.0 / 32)
#define IS_LEATHER_LAYER texelFetch(Sampler0, ivec2(0.0, 0.0), 0) == vec4(1.0)

#define SPACING 1024.0
#define MAXRANGE (0.5 * SPACING)
#define SKINRES 64
#define FACERES 8

const vec4[] subuvs = vec4[](
vec4(4.0,  0.0,  8.0,  4.0 ),
vec4(8.0,  0.0,  12.0, 4.0 ),
vec4(0.0,  4.0,  4.0,  16.0),
vec4(4.0,  4.0,  8.0,  16.0),
vec4(8.0,  4.0,  12.0, 16.0),
vec4(12.0, 4.0,  16.0, 16.0),
vec4(4.0,  0.0,  7.0,  4.0 ),
vec4(7.0,  0.0,  10.0, 4.0 ),
vec4(0.0,  4.0,  4.0,  16.0),
vec4(4.0,  4.0,  7.0,  16.0),
vec4(7.0,  4.0,  11.0, 16.0),
vec4(11.0, 4.0,  14.0, 16.0),
vec4(4.0,  0.0,  12.0, 4.0 ),
vec4(12.0,  0.0, 20.0, 4.0 ),
vec4(0.0,  4.0,  4.0,  16.0),
vec4(4.0,  4.0,  12.0, 16.0),
vec4(12.0, 4.0,  16.0, 16.0),
vec4(16.0, 4.0,  24.0, 16.0)
);

const vec2[] origins = vec2[](
vec2(40.0, 16.0), vec2(40.0, 32.0),
vec2(32.0, 48.0), vec2(48.0, 48.0),
vec2(40.0, 16.0), vec2(40.0, 32.0),
vec2(32.0, 48.0), vec2(48.0, 48.0),
vec2(16.0, 16.0), vec2(16.0, 32.0),
vec2(0.0,  16.0), vec2(0.0,  32.0),
vec2(16.0, 48.0), vec2(0.0,  48.0)
);

const int[] faceremap = int[](0, 0, 1, 1, 2, 3, 4, 5);


in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1, UV2;
in vec3 Normal;

uniform sampler2D Sampler0,Sampler1, Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor, tintColor, lightMapColor;
out vec2 texCoord0;
out vec2 texCoord1;
out float part;
out vec4 normal;
out vec4 cem_pos1, cem_pos2, cem_pos3, cem_pos4;
out vec3 cem_glPos;
out vec3 cem_uv1, cem_uv2;
out vec4 cem_lightMapColor;

out vec4 overlayColor;

flat out int cem;
flat out int bodypart;
flat out int cem_reverse;
flat out vec4 cem_light;
flat out ivec4 cems;
flat out float cem_size;
flat out ivec2 RelativeCords;
flat out int armorType;
flat out int isGui;
flat out int isUpperArmor;
flat out int markforremove;
flat out int isTrim;
flat out float emissive;
flat out int isLeatherLayer;
flat out int isEnchantedArmor;
out vec4 cem_color;

float getChannel(ivec2 cords, int channel) {
    vec4 color = texelFetch(Sampler0, cords, 0);
    return floor(color[channel] * 255.0);
}

vec4 getCemData(ivec2 cords) {
    return floor(texelFetch(Sampler0, cords, 0) * 255.0);
}


#define COLOR_ARMOR(r,g,b) return true; case ((r<<16)+(g<<8)+b):

int colorId(vec3 c) {
    ivec3 v = ivec3(c*255);
    return (v.r<<16)+(v.g<<8)+v.b;
}
vec2 cords=vec2(0,0);

bool shouldApplyArmor(){
    int vertexColorId=colorId(Color.rgb);
    switch(vertexColorId){
        default:
        
        #moj_import<armorcords.glsl>
        
        return true;
    }
    return false;
}


float getChannel(ivec2 rcords, ivec2 icords, int channel)
{       
    ivec2 cords = ivec2(rcords.x*64 + icords.x, rcords.y*32 + icords.y);
    vec4 color = texelFetch(Sampler0, cords, 0);
    if (channel == 0)
        return floor(color.x * 255);
    if (channel == 1)
        return floor(color.y * 255);
    if (channel == 2)
        return floor(color.z * 255);
    if (channel == 3)
        return floor(color.w * 255);
    return 0;
}

void main() {
    cem_color = vec4(0,1,0,1);
    isTrim = 0;
    isGui = (ProjMat[2][3] == 0.0) ? 1 : 0;
    isEnchantedArmor = int(Color.r * 255.0 + 0.5) & 1;
    vec3 pos = Position;
    part = 0.0;
    texCoord1 = vec2(0.0);

    ivec2 ps_dim = textureSize(Sampler0, 0);
    if (abs(ProjMat[2][3]) > 10e-6 && ps_dim.x == SKINRES && ps_dim.y == SKINRES && FogRenderDistanceEnd > FogRenderDistanceStart) {
        int ps_partId = -int((Position.y - MAXRANGE) / SPACING);
        part = float(ps_partId);
        if (ps_partId != 0) {
            ps_partId -= 1;
            bool ps_slim = ps_partId == 2 || ps_partId == 3;
            int ps_partIdMod = ps_partId % 7;
            int ps_outerLayer = (gl_VertexID / 24) % 2;
            int ps_vertexId = gl_VertexID % 4;
            int ps_faceId = (gl_VertexID % 24) / 4;
            ivec2 ps_faceIdTmp = ivec2(round(UV0 * SKINRES));

            vec2 ps_UVout = origins[2 * ps_partIdMod + ps_outerLayer];
            vec2 ps_UVout2 = origins[2 * ps_partIdMod];

            if ((ps_faceId != 1 && ps_vertexId >= 2) || (ps_faceId == 1 && ps_vertexId <= 1)) {
                ps_faceIdTmp.y -= FACERES;
            }
            if (ps_vertexId == 0 || ps_vertexId == 3) {
                ps_faceIdTmp.x -= FACERES;
            }

            ps_faceIdTmp /= FACERES;
            ps_faceId = (ps_faceIdTmp.x % 4) + 4 * ps_faceIdTmp.y;
            ps_faceId = faceremap[ps_faceId];
            int ps_subuvIndex = ps_faceId;

            if (ps_slim) {
                ps_subuvIndex += 6;
            } else if (ps_partIdMod == 4) {
                ps_subuvIndex += 12;
            }

            vec4 ps_subuv = subuvs[ps_subuvIndex];
            vec2 ps_offset = vec2(0.0);

            if (ps_faceId == 1) {
                if (ps_vertexId == 0) ps_offset += ps_subuv.zw;
                else if (ps_vertexId == 1) ps_offset += ps_subuv.xw;
                else if (ps_vertexId == 2) ps_offset += ps_subuv.xy;
                else ps_offset += ps_subuv.zy;
            } else {
                if (ps_vertexId == 0) ps_offset += ps_subuv.zy;
                else if (ps_vertexId == 1) ps_offset += ps_subuv.xy;
                else if (ps_vertexId == 2) ps_offset += ps_subuv.xw;
                else ps_offset += ps_subuv.zw;
            }

            ps_UVout += ps_offset;
            ps_UVout2 += ps_offset;
            ps_UVout /= float(SKINRES);
            ps_UVout2 /= float(SKINRES);

            vec3 ps_wpos = Position;
            ps_wpos.y += SPACING * (ps_partId + 1);
            gl_Position = ProjMat * ModelViewMat * vec4(ps_wpos, 1.0);

            sphericalVertexDistance = fog_spherical_distance(ps_wpos);
            cylindricalVertexDistance = fog_cylindrical_distance(ps_wpos);

            texCoord0 = ps_UVout;
            texCoord1 = ps_UVout2;

            vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
            tintColor = Color;
            lightMapColor = sample_lightmap(Sampler2, UV2);
            overlayColor = texelFetch(Sampler1, UV1, 0);
            normal = vec4(0.0);
            cem_pos1 = cem_pos2 = cem_pos3 = cem_pos4 = vec4(0);
            cem_uv1 = cem_uv2 = vec3(0);
            cem = 0;
            cem_reverse = 0;
            cem_light = sample_lightmap(Sampler2, UV2);
            cem_size = 1.0;
            cems = ivec4(-1);
            bodypart = -1;
            markforremove = 0;
            RelativeCords = ivec2(0);
            emissive = 0.0;
            return;
        }
    }

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    #moj_import <fog_reader.glsl>
    normal = ProjMat * ModelViewMat * vec4(Normal, 0.0);

    
    #ifndef EMISSIVE
        lightMapColor = sample_lightmap(Sampler2, UV2);
    #endif
        overlayColor = texelFetch(Sampler1, UV1, 0);

    texCoord0 = UV0;
    tintColor = Color;
    emissive = 0.0;
    markforremove = 0;
    RelativeCords = ivec2(0);
    
    bool isLeather = IS_LEATHER_LAYER;
    if (isLeather) {
        ivec2 atlasSize = textureSize(Sampler0, 0);
        vec2 armorAmount = vec2(atlasSize) * vec2(INV_TEX_RES_SIX, INV_TEX_RES_THREE);
        vec2 offset = 1.0 / armorAmount;

        texCoord0 *= offset;
        shouldApplyArmor();
        if (cords.x != 0 || cords.y != 0) {
            tintColor = vec4(1);
            RelativeCords = ivec2(floor(cords));
            texCoord0 += vec2(offset.x * cords.x, offset.y * cords.y);
        }
    }
    vec4 light = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, tintColor);

    cem_pos1 = cem_pos2 = cem_pos3 = cem_pos4 = vec4(0);
    cem_uv1 = cem_uv2 = vec3(0);
    cem = cem_reverse = 0;
    cem_light = sample_lightmap(Sampler2, UV2);
    cem_size = 1.0;
    cems = ivec4(-1);
    bodypart = -1;

    #ifdef NO_CARDINAL_LIGHTING
        vertexColor = Color;
    #else
        vertexColor = light;
        if(isLeather){
            vertexColor *= ColorModulator;
        }
    #endif

    #ifdef APPLY_TEXTURE_MATRIX
        texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
    #endif

    float ch0 = getChannel(RelativeCords, ivec2(63,30), 0);
    float ch1 = getChannel(RelativeCords, ivec2(63,30), 1);
    float ch2 = getChannel(RelativeCords, ivec2(63,30), 2);
    float ch3 = getChannel(RelativeCords, ivec2(63,30), 3);

    bool isPartOne = (ch0 == 0 && ch1 == 0 && ch2 == 0 && ch3 == 255);
    bool isPartTwo = (ch0 == 255 && ch1 == 255 && ch2 == 255 && ch3 == 255);

    if (isLeather && (isPartOne || isPartTwo)) {
        float RVC_0 = getChannel(RelativeCords,ivec2(63,31), 0);
        float RVC_1 = getChannel(RelativeCords,ivec2(63,31), 1);
        float RVC_2 = getChannel(RelativeCords,ivec2(63,31), 2);
        if(RVC_0==0 && RVC_1==0 && RVC_2==0){
            markforremove = 1;
            gl_Position = vec4(0,0,0,1);
            overlayColor = vec4(0,0,0,0);
            return;
        }
        vec2 texSize = textureSize(Sampler0, 0);
        vec2 uv = floor(texCoord0 * texSize);
        uv = uv-vec2(RelativeCords.x*64,RelativeCords.y*32);
        const vec2[4] corners = vec2[4](vec2(0), vec2(0, 1), vec2(1, 1), vec2(1, 0));
        vec2 corner = corners[gl_VertexID % 4];

        int face = (gl_VertexID / 4) % 6;

        int removeAll = 0;


        int cube = (gl_VertexID / 24) % 10;
        bodypart = cube;
        
        #moj_import <mods/armor/setup.glsl>

        #moj_import <mods/armor/armor.glsl>

        if(face==TOP_FACE){
            cem_reverse = (bodypart == ARMOR_LEFT_ARM || bodypart == ARMOR_LEFT_FEET) ? 1 : 0;
            corner = corner.yx;
            if (isPartOne) {
                cem_size = 0.666667;
            } else if (isPartTwo) {
                cem_size = 0.8;
            } else {
                cem_size = 1.0;
            }
        }else{
            if(removeAll==1){
                markforremove = 1;
                gl_Position = vec4(0,0,0,1);
                overlayColor = vec4(0,0,0,0);
                return;
            }else{
              bodypart = -1;
              cems = ivec4(-1);
            }
        }

        if (gl_VertexID / 4 == 3)
            corner.x = 1 - corner.x;

        if (cems[0] > 0 || cems[1] > 0 || cems[2] > 0 || cems[3] > 0 )
        {
            cem=199;
            #moj_import <cem/vert_setup.glsl>
        }
    }
}