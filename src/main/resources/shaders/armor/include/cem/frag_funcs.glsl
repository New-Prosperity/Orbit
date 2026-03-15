in vec4 cem_pos1, cem_pos2, cem_pos3, cem_pos4;
in vec3 cem_uv1, cem_uv2;
in vec3 cem_glPos;
in vec4 cem_lightMapColor;
flat in int cem;
flat in int cem_reverse;
flat in vec4 cem_light;
#define EXT_ENABLE 1
#define MAX_DEPTH 1024000.0
#define VEDEPTH vec3(MAX_DEPTH)
#define VECNULL vec3(0)
#define VECNULL4 vec4(0)


vec3 planeIntersect(in vec3 ro, in vec3 rd, in vec3 v0, in vec3 v1, in vec3 v2) {
    vec3 v1v0 = v1 - v0;
    vec3 v2v0 = v2 - v0;
    vec3 rov0 = ro - v0;
    
    vec3 n = cross(v1v0, v2v0);
    vec3 q = cross(rov0, rd);
    float d = 1.0/dot(rd, n);
    
    return vec3(
        d * dot(-q, v2v0),
        d * dot(q, v1v0),
        d * dot(-n, rov0)
    );
}

vec3 squareIntersect(in vec3 ro, in vec3 rd, in vec3 v0, in vec3 v1, in vec3 v2) {
    vec3 val = planeIntersect(ro, rd, v0, v1, v2);
    val.z = (val.x < 0.0 || val.y < 0.0 || val.x > 1.0 || val.y > 1.0 || val.z < 0.0) ? MAX_DEPTH : val.z;
    return val;
}

vec3 triIntersect(in vec3 ro, in vec3 rd, in vec3 v0, in vec3 v1, in vec3 v2) {
    vec3 val = planeIntersect(ro, rd, v0, v1, v2);
    val.z = (val.x < 0.0 || val.y < 0.0 || val.z < 0.0 || val.x + val.y > 1.0) ? MAX_DEPTH : val.z;
    return val;
}

bool isInside(vec3 ro, vec3 rd, vec3 size, out vec3 outNormal, bool enableTransparency) {
    vec3 m = 1.0 / rd;
    vec3 n = m * ro;
    vec3 k = abs(m) * size;
    vec3 t1 = -n - k;
    float tN = max(max(t1.x, t1.y), t1.z);
    return tN < 0.0;
}

vec3 boxIntersect(vec3 ro, vec3 rd, vec3 size, out vec3 outNormal, bool enableTransparency) {   
    vec3 m = 1.0 / rd;
    vec3 n = m * ro;
    vec3 k = abs(m) * size;
    vec3 t1 = -n - k;
    vec3 t2 = -n + k;
    float tN = max(max(t1.x, t1.y), t1.z);
    float tF = min(min(t2.x, t2.y), t2.z);
    
    if (tN > tF || tF < 0.0) return VEDEPTH;
    
    outNormal = -sign(rd) * step(t1.yzx, t1.xyz) * step(t1.zxy, t1.xyz);
    
    float t = tN > 0.0 ? tN : tF;
    
    vec3 pos = (ro + rd * t) / size;
    vec2 tex;
    
    float absX = abs(outNormal.x);
    float absY = abs(outNormal.y);
    float absZ = abs(outNormal.z);
    
    bvec3 isAxis = bvec3(absX > 0.9, absY > 0.9, absZ > 0.9);
    
    tex = isAxis.x ? pos.zy : (isAxis.y ? pos.xz : pos.xy);
    
    return vec3(clamp(tex * 0.5 + 0.5, vec2(0), vec2(1)), t);
}

vec3 boxIntersectInv(vec3 ro, vec3 rd, vec3 size, out vec3 outNormal, bool enableTransparency) {
    vec3 m = 1.0 / rd;
    vec3 n = m * ro;
    vec3 k = abs(m) * size;
    vec3 t1 = -n - k;
    vec3 t2 = -n + k;
    float tN = max(max(t1.x, t1.y), t1.z);
    float tF = min(min(t2.x, t2.y), t2.z);
    
    if (tN > tF || tF < 0.0) return VEDEPTH;
    
    outNormal = sign(rd) * step(t2.xyz, t2.yzx) * step(t2.xyz, t2.zxy);
    
    vec3 pos = (ro + rd * tF) / size;
    vec2 tex;
    
    float absX = abs(outNormal.x);
    float absY = abs(outNormal.y);
    float absZ = abs(outNormal.z);
    
    bvec3 isAxis = bvec3(absX > 0.9, absY > 0.9, absZ > 0.9);
    tex = isAxis.x ? pos.zy : (isAxis.y ? pos.xz : pos.xy);
    
    return vec3(clamp(tex * 0.5 + 0.5, vec2(0), vec2(1)), tF);
}

vec4 sBoxExt(vec3 ro, vec3 rd, vec3 size, mat3 TBN, vec4 color, inout float T, 
              vec4 dSide, vec4 uSide, vec4 nSide, vec4 eSide, vec4 sSide, vec4 wSide, bool enableTransparency) {
    vec2 texSize = textureSize(Sampler0, 0);
    vec3 normal;

    vec3 box = boxIntersect(ro, rd, size, normal, enableTransparency);
    if (box.z >= T) return color;

    vec4 col = VECNULL4;
    vec2 texCoord;
    vec4 selectedSide;
    
    bvec3 normalComp = bvec3(abs(normal.x) > 0.9, abs(normal.y) > 0.9, abs(normal.z) > 0.9);
    bool isPositive = dot(normal, normal) > 0.0;
    
    if (normalComp.x) {
        selectedSide = normal.x > 0.0 ? eSide : wSide;
        texCoord = normal.x > 0.0 ? box.xy : vec2(1.0 - box.x, box.y);
    }
    else if (normalComp.z) {
        selectedSide = normal.z > 0.0 ? sSide : nSide;
        texCoord = normal.z > 0.0 ? vec2(1.0 - box.x, box.y) : box.xy;
    }
    else if (normalComp.y) {
        selectedSide = normal.y > 0.0 ? dSide : uSide;
        texCoord = box.xy;
    }
    
    col = texture(Sampler0, (selectedSide.xy + selectedSide.zw * texCoord) / texSize);

    if ((col.rgb == VECNULL && !enableTransparency) || (col.a < 0.1 && EXT_ENABLE == 1)) {
        box = boxIntersectInv(ro, rd, size, normal, enableTransparency);
        
        if (normalComp.x) {
            selectedSide = normal.x > 0.0 ? eSide : wSide;
            texCoord = normal.x > 0.0 ? box.xy : vec2(1.0 - box.x, box.y);
        }
        else if (normalComp.z) {
            selectedSide = normal.z > 0.0 ? sSide : nSide;
            texCoord = normal.z > 0.0 ? vec2(1.0 - box.x, box.y) : box.xy;
        }
        else if (normalComp.y) {
            selectedSide = normal.y > 0.0 ? dSide : uSide;
            texCoord = box.xy;
        }
        
        col = texture(Sampler0, (selectedSide.xy + selectedSide.zw * texCoord) / texSize);
    }

    col = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(TBN * normal), col);

    if (col.a < 0.1) return color;

    T = box.z;
    return col;
}

vec4 sBoxExtWithRotation(vec3 ro, vec3 rd, vec3 size, mat3 TBN, vec4 color, inout float T, vec4 dSide, vec4 uSide, vec4 nSide, vec4 eSide, vec4 sSide, vec4 wSide, float dSideRotationAngle, float uSideRotationAngle, float nSideRotationAngle, float eSideRotationAngle, float sSideRotationAngle, float wSideRotationAngle, bool enableTransparency)
{
    vec2 texSize = textureSize(Sampler0, 0);
    vec3 normal = VECNULL;

    if(isInside(ro, rd, size, normal,enableTransparency)){
        return color;
    }
    vec3 box = boxIntersect(ro, rd, size, normal,enableTransparency);
    

    if (box.z >= T)
        return color;

    vec4 col = VECNULL4;

    if (normal.x > 0.9 && eSide != VECNULL4)
    {
        vec2 rotatedUV = eSide.xy + eSide.zw * box.xy;

        if (eSideRotationAngle == 1.0) {
            rotatedUV = eSide.xy + eSide.zw * box.yx;
        } else if (eSideRotationAngle == 2.0) {
            rotatedUV = eSide.xy + eSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
        } else if (eSideRotationAngle == 3.0) {
            rotatedUV = eSide.xy + eSide.zw * vec2(1.0 - box.y, box.x);
        } 
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.x < -0.9 && wSide != VECNULL4)
    {
        vec2 rotatedUV = wSide.xy + wSide.zw * vec2(1 - box.x, box.y);
        if (wSideRotationAngle == 1.0) {
            rotatedUV = wSide.xy + wSide.zw * vec2(box.y, 1.0 - box.x);
        } else if (wSideRotationAngle == 2.0) {
            rotatedUV = wSide.xy + wSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
        } else if (wSideRotationAngle == 3.0) {
            rotatedUV = wSide.xy + wSide.zw * vec2(1.0 - box.y, box.x);
        } 
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.z > 0.9 && sSide != VECNULL4)
    {
        vec2 rotatedUV = sSide.xy + sSide.zw * vec2(1 - box.x, box.y);
        if (sSideRotationAngle == 1.0) {
            rotatedUV = sSide.xy + sSide.zw * vec2(box.y, 1.0 - box.x);
        } else if (sSideRotationAngle == 2.0) {
            rotatedUV = sSide.xy + sSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
        } else if (sSideRotationAngle == 3.0) {
            rotatedUV = sSide.xy + sSide.zw * vec2(1.0 - box.y, box.x);
        }
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.z < -0.9 && nSide != VECNULL4)
    {
        vec2 rotatedUV = nSide.xy + nSide.zw * box.xy;
        if (nSideRotationAngle == 1.0) {
            rotatedUV = nSide.xy + nSide.zw * box.yx;
        } else if (nSideRotationAngle == 2.0) {
            rotatedUV = nSide.xy + nSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
        } else if (nSideRotationAngle == 3.0) {
            rotatedUV = nSide.xy + nSide.zw * vec2(1.0 - box.y, box.x);
        }
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.y > 0.9 && uSide != VECNULL4)
    {
        vec2 rotatedUV = uSide.xy + uSide.zw * box.xy;
        if (uSideRotationAngle == 1.0) {
            rotatedUV = uSide.xy + uSide.zw * box.yx;
        } 
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.y < -0.9 && dSide != VECNULL4)
    {
        vec2 rotatedUV = dSide.xy + dSide.zw * box.xy;
        if (dSideRotationAngle == 1.0) {
            rotatedUV = dSide.xy + dSide.zw * box.yx;
        } 
        col = texture(Sampler0, (rotatedUV) / texSize);
    }

    if ((col.rgb == VECNULL && !enableTransparency) || col.a <0.1 && EXT_ENABLE == 1)
    {
        box = boxIntersectInv(ro, rd, size, normal,enableTransparency);
        

        if (normal.x > 0.9 && eSide != VECNULL4)
        {
            vec2 rotatedUV = eSide.xy + eSide.zw * box.xy;

            if (eSideRotationAngle == 1.0) {
                rotatedUV = eSide.xy + eSide.zw * box.yx;
            } else if (eSideRotationAngle == 2.0) {
                rotatedUV = eSide.xy + eSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
            } else if (eSideRotationAngle == 3.0) {
                rotatedUV = eSide.xy + eSide.zw * vec2(1.0 - box.y, box.x);
            } 
            col = texture(Sampler0, (rotatedUV) / texSize);
        }
        else if (normal.x < -0.9 && wSide != VECNULL4)
        {
            vec2 rotatedUV = wSide.xy + wSide.zw * vec2(1 - box.x, box.y);
            if (wSideRotationAngle == 1.0) {
                rotatedUV = wSide.xy + wSide.zw * vec2(box.y, 1.0 - box.x);
            } else if (wSideRotationAngle == 2.0) {
                rotatedUV = wSide.xy + wSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
            } else if (wSideRotationAngle == 3.0) {
                rotatedUV = wSide.xy + wSide.zw * vec2(1.0 - box.y, box.x);
            } 
            col = texture(Sampler0, (rotatedUV) / texSize);
        }
        else if (normal.z > 0.9 && sSide != VECNULL4)
        {
            vec2 rotatedUV = sSide.xy + sSide.zw * vec2(1 - box.x, box.y);
            if (sSideRotationAngle == 1.0) {
                rotatedUV = sSide.xy + sSide.zw * vec2(box.y, 1.0 - box.x);
            } else if (sSideRotationAngle == 2.0) {
                rotatedUV = sSide.xy + sSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
            } else if (sSideRotationAngle == 3.0) {
                rotatedUV = sSide.xy + sSide.zw * vec2(1.0 - box.y, box.x);
            }
            col = texture(Sampler0, (rotatedUV) / texSize);
        }
        else if (normal.z < -0.9 && nSide != VECNULL4)
        {
            vec2 rotatedUV = nSide.xy + nSide.zw * box.xy;
            if (nSideRotationAngle == 1.0) {
                rotatedUV = nSide.xy + nSide.zw * box.yx;
            } else if (nSideRotationAngle == 2.0) {
                rotatedUV = nSide.xy + nSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
            } else if (nSideRotationAngle == 3.0) {
                rotatedUV = nSide.xy + nSide.zw * vec2(1.0 - box.y, box.x);
            }
            col = texture(Sampler0, (rotatedUV) / texSize);
        }
        else if (normal.y > 0.9 && uSide != VECNULL4)
        {
            vec2 rotatedUV = uSide.xy + uSide.zw * box.xy;
            if (uSideRotationAngle == 1.0) {
                rotatedUV = uSide.xy + uSide.zw * box.yx;
            } 
            col = texture(Sampler0, (rotatedUV) / texSize);
        }
        else if (normal.y < -0.9 && dSide != VECNULL4)
        {
            vec2 rotatedUV = dSide.xy + dSide.zw * box.xy;
            if (dSideRotationAngle == 1.0) {
                rotatedUV = dSide.xy + dSide.zw * box.yx;
            } 
            col = texture(Sampler0, (rotatedUV) / texSize);
        }
    }

    col = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(TBN * normal), col);

    if (col.a < 0.1) return color;

    if (col.rgb == VECNULL && !enableTransparency){
        return color;
    }

    T = box.z;

    return col;
}

void writeDepth(vec3 Pos)
{
    if (ProjMat[3][0] == -1)
    {
        vec4 ProjPos = ProjMat * vec4(Pos, 1);
        gl_FragDepth = ProjPos.z / ProjPos.w * 0.5 + 0.5;
 
    }
    else
    {
        vec4 ProjPos = ProjMat * ModelViewMat * vec4(Pos, 1);
        gl_FragDepth = ProjPos.z / ProjPos.w * 0.5 + 0.5;
    }
}

vec4 sSquareWithRotation(vec3 ro, vec3 rd, vec3 p1, vec3 p2, vec3 p3, bool normalizeNormal,vec4 tint, vec4 color, inout float T, vec4 uv,float rotAngle)
{
    vec3 normal = normalize(rd);
    vec3 tris = squareIntersect(ro, rd, p1, p2, p3);


    if (tris.z >= T) return color;
    vec2 rotatedUV = uv.xy + uv.zw * tris.xy;

    if (rotAngle == 1.0) {
        rotatedUV = uv.xy + uv.zw * vec2(tris.y, 1.0 - tris.x);
    } else if (rotAngle == 2.0) {
        rotatedUV = uv.xy + uv.zw * vec2(1.0 - tris.x, 1.0 - tris.y);
    } else if (rotAngle == 3.0) {
        rotatedUV = uv.xy + uv.zw * vec2(1.0 - tris.y, tris.x);
    }
    else if (rotAngle == 4.0) {
        rotatedUV = uv.xy + uv.zw * vec2(1.0 - tris.x, tris.y);
    }
    else if (rotAngle == 5.0){
        rotatedUV = uv.xy + uv.zw * vec2(1.0 - tris.y, 1.0 - tris.x);
    }
    else if (rotAngle == 6.0){
        rotatedUV = uv.xy + uv.zw * vec2(1.0 - tris.x, 1.0 - tris.y);
    }
    else if (rotAngle == 7.0){
        rotatedUV = uv.xy + uv.zw * vec2(tris.y, tris.x);
    }
    else if (rotAngle == 8.0){
        rotatedUV = uv.xy + uv.zw * vec2(tris.x, tris.y);
    }
    vec4 col = texelFetch(Sampler0, ivec2(rotatedUV), 0) ;
    
    if(normalizeNormal){
        normal = normalize(cross(p2 - p1, p3 - p1)*rd);
        col = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, col);
    }else{
        col = col * tint;
    }

    
    if (col.a < 0.1) return color;

    if (col.rgb == VECNULL){
        return color;
    }

    T = tris.z;
    return col;
}

vec4 sSquare(vec3 ro, vec3 rd, vec3 p1, vec3 p2, vec3 p3, bool normalizeNormal,vec4 tint, vec4 color, inout float T, vec4 uv)
{
    vec3 normal = normalize(rd);
    vec3 tris = squareIntersect(ro, rd, p1, p2, p3);

    if (tris.z >= T) return color;

    vec4 col = texelFetch(Sampler0, ivec2(uv.xy + uv.zw * tris.xy), 0) ;
    
    if(normalizeNormal){
        normal = normalize(cross(p2 - p1, p3 - p1)*rd);
        col = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, col)*tint;
    }else{
        col = col * tint;
    }

    
    if (col.a < 0.1) return color;

    if (col.rgb == VECNULL){
        return color;
    }

    T = tris.z;
    return col;
}
vec4 sTrisWithRotation(vec3 ro, vec3 rd, vec3 p1, vec3 p2, vec3 p3, bool normalizeNormal,vec4 tint, vec4 color, inout float T, vec4 uv, float rotAngle)
{
    vec3 normal = normalize(rd);
    vec3 tris = triIntersect(ro, rd, p1, p2, p3);

    if (tris.z >= T) return color;

    vec2 rotatedUV = uv.xy + uv.zw * tris.xy;

    if (rotAngle == 1.0) {
        rotatedUV = uv.xy + uv.zw * vec2(tris.y, 1.0 - tris.x);
    } else if (rotAngle == 2.0) {
        rotatedUV = uv.xy + uv.zw * vec2(1.0 - tris.x, 1.0 - tris.y);
    } else if (rotAngle == 3.0) {
        rotatedUV = uv.xy + uv.zw * vec2(1.0 - tris.y, tris.x);
    }

    vec4 col = texelFetch(Sampler0, ivec2(rotatedUV), 0) ;
    if(normalizeNormal){
        normal = normalize(cross(p2 - p1, p3 - p1)*rd);
        col = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, col);
    }else{
        col = col * tint;
    }

    if (col.a < 0.1) return color;

    if (col.rgb == VECNULL){
        return color;
    }

    T = tris.z;
    return col;
}

vec4 sTris(vec3 ro, vec3 rd, vec3 p1, vec3 p2, vec3 p3, vec4 color, inout float T, vec4 uv)
{
    vec3 tris = triIntersect(ro, rd, p1, p2, p3);

    if (tris.z >= T) return color;

    vec4 col = texelFetch(Sampler0, ivec2(uv.xy + uv.zw * tris.xy), 0);
    
    if (col.a < 0.1) return color;

    if (col.rgb == VECNULL){
        return color;
    }

    T = tris.z;
    return col;
}

vec4 sQuad(vec3 ro, vec3 rd, float modelSize, vec3 p1, vec3 p2, vec3 p3, vec3 p4, vec4 color, inout float T, vec4 uv)
{
    color = sTris(ro, rd, p1 * modelSize, p2 * modelSize, p3 * modelSize, color, T, uv);
    color = sTris(ro, rd, p4 * modelSize, p3 * modelSize, p2 * modelSize, color, T, vec4(uv.xy + uv.zw, -uv.zw));
    return color;
}

vec4 sBox(vec3 ro, vec3 rd, vec3 size, mat3 TBN, vec4 color, inout float T, vec4 dSide, vec4 uSide, vec4 nSide, vec4 eSide, vec4 sSide, vec4 wSide,bool enableTransparency)
{
    vec2 texSize = textureSize(Sampler0, 0);
    vec3 normal = VECNULL;

    vec3 box = boxIntersect(ro, rd, size, normal,enableTransparency);

    if (box.z >= T)
        return color;

    vec4 col = VECNULL4;

    if (normal.x > 0.9)
    {
        col = texture(Sampler0, (eSide.xy + eSide.zw * box.xy) / texSize);
    }
    else if (normal.x < -0.9)
    {
        col = texture(Sampler0, (wSide.xy + wSide.zw * vec2(1 - box.x, box.y)) / texSize);
    }
    else if (normal.z > 0.9)
    {
        col = texture(Sampler0, (sSide.xy + sSide.zw * vec2(1 - box.x, box.y)) / texSize);
    }
    else if (normal.z < -0.9)
    {
        col = texture(Sampler0, (nSide.xy + nSide.zw * box.xy) / texSize);
    }
    else if (normal.y > 0.9)
    {
        col = texture(Sampler0, (uSide.xy + uSide.zw * box.xy) / texSize);
    }
    else if (normal.y < -0.9)
    {
        col = texture(Sampler0, (dSide.xy + dSide.zw * box.xy) / texSize);
    }

    col = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(TBN * normal), col);

    if (col.a < 0.1) return color;

    if (col.rgb == VECNULL && !enableTransparency){
        return color;
    }

    T = box.z;

    return col;
}

vec4 sBoxWithRotation(vec3 ro, vec3 rd, vec3 size, mat3 TBN, vec4 color, inout float T, vec4 dSide, vec4 uSide, vec4 nSide, vec4 eSide, vec4 sSide, vec4 wSide, float dSideRotationAngle, float uSideRotationAngle, float nSideRotationAngle, float eSideRotationAngle, float sSideRotationAngle, float wSideRotationAngle, bool enableTransparency)
{
    vec2 texSize = textureSize(Sampler0, 0);
    vec3 normal = VECNULL;

    if(isInside(ro, rd, size, normal,enableTransparency)){
        return color;
    }
    vec3 box = boxIntersect(ro, rd, size, normal,enableTransparency);

    if (box.z >= T)
        return color;

    vec4 col = VECNULL4;

    if (normal.x > 0.9)
    {
        vec2 rotatedUV = eSide.xy + eSide.zw * box.xy;

        if (eSideRotationAngle == 1.0) {
            rotatedUV = eSide.xy + eSide.zw * box.yx;
        } else if (eSideRotationAngle == 2.0) {
            rotatedUV = eSide.xy + eSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
        } else if (eSideRotationAngle == 3.0) {
            rotatedUV = eSide.xy + eSide.zw * vec2(1.0 - box.y, box.x);
        } 
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.x < -0.9)
    {
        vec2 rotatedUV = wSide.xy + wSide.zw * vec2(1 - box.x, box.y);
        if (wSideRotationAngle == 1.0) {
            rotatedUV = wSide.xy + wSide.zw * vec2(box.y, 1.0 - box.x);
        } else if (wSideRotationAngle == 2.0) {
            rotatedUV = wSide.xy + wSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
        } else if (wSideRotationAngle == 3.0) {
            rotatedUV = wSide.xy + wSide.zw * vec2(1.0 - box.y, box.x);
        } 
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.z > 0.9)
    {
        vec2 rotatedUV = sSide.xy + sSide.zw * vec2(1 - box.x, box.y);
        if (sSideRotationAngle == 1.0) {
            rotatedUV = sSide.xy + sSide.zw * vec2(box.y, 1.0 - box.x);
        } else if (sSideRotationAngle == 2.0) {
            rotatedUV = sSide.xy + sSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
        } else if (sSideRotationAngle == 3.0) {
            rotatedUV = sSide.xy + sSide.zw * vec2(1.0 - box.y, box.x);
        }
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.z < -0.9)
    {
        vec2 rotatedUV = nSide.xy + nSide.zw * box.xy;
        if (nSideRotationAngle == 1.0) {
            rotatedUV = nSide.xy + nSide.zw * box.yx;
        } else if (nSideRotationAngle == 2.0) {
            rotatedUV = nSide.xy + nSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
        } else if (nSideRotationAngle == 3.0) {
            rotatedUV = nSide.xy + nSide.zw * vec2(1.0 - box.y, box.x);
        }
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.y > 0.9)
    {
        vec2 rotatedUV = uSide.xy + uSide.zw * box.xy;
        if (uSideRotationAngle == 1.0) {
            rotatedUV = uSide.xy + uSide.zw * box.yx;
        } 
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.y < -0.9)
    {
        vec2 rotatedUV = dSide.xy + dSide.zw * box.xy;
        if (dSideRotationAngle == 1.0) {
            rotatedUV = dSide.xy + dSide.zw * box.yx;
        } 
        col = texture(Sampler0, (rotatedUV) / texSize);
    }

    col = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(TBN * normal), col);

    if (col.a < 0.1) return color;

    if (col.rgb == VECNULL && !enableTransparency){
        return color;
    }

    T = box.z;

    return col;
}
vec4 sBoxWithRotationEmissive(vec3 ro, vec3 rd, vec3 size, mat3 TBN, vec4 color, inout float T, vec4 dSide, vec4 uSide, vec4 nSide, vec4 eSide, vec4 sSide, vec4 wSide, float dSideRotationAngle, float uSideRotationAngle, float nSideRotationAngle, float eSideRotationAngle, float sSideRotationAngle, float wSideRotationAngle, bool enableTransparency)
{
    vec2 texSize = textureSize(Sampler0, 0);
    vec3 normal = VECNULL;

    if(isInside(ro, rd, size, normal,enableTransparency)){
        return color;
    }
    vec3 box = boxIntersect(ro, rd, size, normal,enableTransparency);

    if (box.z >= T)
        return color;

    vec4 col = VECNULL4;

    if (normal.x > 0.9)
    {
        vec2 rotatedUV = eSide.xy + eSide.zw * box.xy;

        if (eSideRotationAngle == 1.0) {
            rotatedUV = eSide.xy + eSide.zw * box.yx;
        } else if (eSideRotationAngle == 2.0) {
            rotatedUV = eSide.xy + eSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
        } else if (eSideRotationAngle == 3.0) {
            rotatedUV = eSide.xy + eSide.zw * vec2(1.0 - box.y, box.x);
        } 
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.x < -0.9)
    {
        vec2 rotatedUV = wSide.xy + wSide.zw * vec2(1 - box.x, box.y);
        if (wSideRotationAngle == 1.0) {
            rotatedUV = wSide.xy + wSide.zw * vec2(box.y, 1.0 - box.x);
        } else if (wSideRotationAngle == 2.0) {
            rotatedUV = wSide.xy + wSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
        } else if (wSideRotationAngle == 3.0) {
            rotatedUV = wSide.xy + wSide.zw * vec2(1.0 - box.y, box.x);
        } 
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.z > 0.9)
    {
        vec2 rotatedUV = sSide.xy + sSide.zw * vec2(1 - box.x, box.y);
        if (sSideRotationAngle == 1.0) {
            rotatedUV = sSide.xy + sSide.zw * vec2(box.y, 1.0 - box.x);
        } else if (sSideRotationAngle == 2.0) {
            rotatedUV = sSide.xy + sSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
        } else if (sSideRotationAngle == 3.0) {
            rotatedUV = sSide.xy + sSide.zw * vec2(1.0 - box.y, box.x);
        }
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.z < -0.9)
    {
        vec2 rotatedUV = nSide.xy + nSide.zw * box.xy;
        if (nSideRotationAngle == 1.0) {
            rotatedUV = nSide.xy + nSide.zw * box.yx;
        } else if (nSideRotationAngle == 2.0) {
            rotatedUV = nSide.xy + nSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
        } else if (nSideRotationAngle == 3.0) {
            rotatedUV = nSide.xy + nSide.zw * vec2(1.0 - box.y, box.x);
        }
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.y > 0.9)
    {
        vec2 rotatedUV = uSide.xy + uSide.zw * box.xy;
        if (uSideRotationAngle == 1.0) {
            rotatedUV = uSide.xy + uSide.zw * box.yx;
        } 
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.y < -0.9)
    {
        vec2 rotatedUV = dSide.xy + dSide.zw * box.xy;
        if (dSideRotationAngle == 1.0) {
            rotatedUV = dSide.xy + dSide.zw * box.yx;
        } 
        col = texture(Sampler0, (rotatedUV) / texSize);
    }


    if (col.a < 0.1) return color;

    if (col.rgb == VECNULL && !enableTransparency){
        return color;
    }

    T = box.z;

    return col;
}

vec4 sSquare_Solid(vec3 ro, vec3 rd, vec3 p1, vec3 p2, vec3 p3, vec4 color, inout float T)
{
    vec3 normal = normalize(cross(p2 - p1, p3 - p1));
    float denominator = dot(normal, rd);
    
    if (abs(denominator) < 0.0001) {
        return color;
    }
    
    float t = dot(normal, p1 - ro) / denominator;
    if (t < 0.0 || t >= T) {
        return color;
    }
    
    vec3 intersectionPoint = ro + rd * t;

    vec3 v1 = p2 - p1;
    vec3 v2 = p3 - p1;
    vec3 v3 = intersectionPoint - p1;
    float dot00 = dot(v1, v1);
    float dot01 = dot(v1, v2);
    float dot02 = dot(v1, v3);
    float dot11 = dot(v2, v2);
    float dot12 = dot(v2, v3);
    float invDenom = 1.0 / (dot00 * dot11 - dot01 * dot01);
    float u = (dot11 * dot02 - dot01 * dot12) * invDenom;
    float v = (dot00 * dot12 - dot01 * dot02) * invDenom;
    
    if (u >= 0.0 && v >= 0.0 && u + v <= 1.0) {
        T = t;
        return color;
    }
    
    return color;
}

vec4 sTris_Solid(vec3 ro, vec3 rd, vec3 p1, vec3 p2, vec3 p3, vec4 color, inout float T)
{
    vec3 normal = normalize(cross(p2 - p1, p3 - p1));
    float denominator = dot(normal, rd);
    
    if (abs(denominator) < 0.0001) {
        return color;
    }
    
    float t = dot(normal, p1 - ro) / denominator;
    if (t < 0.0 || t >= T) {
        return color;
    }
    
    vec3 intersectionPoint = ro + rd * t;

    vec3 v1 = p2 - p1;
    vec3 v2 = p3 - p1;
    vec3 v3 = intersectionPoint - p1;
    float dot00 = dot(v1, v1);
    float dot01 = dot(v1, v2);
    float dot02 = dot(v1, v3);
    float dot11 = dot(v2, v2);
    float dot12 = dot(v2, v3);
    float invDenom = 1.0 / (dot00 * dot11 - dot01 * dot01);
    float u = (dot11 * dot02 - dot01 * dot12) * invDenom;
    float v = (dot00 * dot12 - dot01 * dot02) * invDenom;
    
    if (u >= 0.0 && v >= 0.0 && u + v <= 1.0) {
        T = t;
        return color;
    }
    
    return color;
}

vec4 sBox_Solid(vec3 ro, vec3 rd, vec3 size, mat3 TBN, vec4 color, inout float T,vec4 newColor,bool emissive)
{

    vec2 texSize = textureSize(Sampler0, 0);
    vec3 normal = VECNULL;

    vec3 box = boxIntersect(ro, rd, size, normal,false);

    if (box.z >= T)
        return color;

    vec4 col = newColor;
    if(!emissive){
        
    col = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(TBN * normal), col);
    }
    T = box.z;

    return col;

}

vec4 sBoxExt_Solid(vec3 ro, vec3 rd, vec3 size, mat3 TBN, vec4 color, inout float T,vec4 newColor,bool emissive)
{

    vec2 texSize = textureSize(Sampler0, 0);
    vec3 normal = VECNULL;

    vec3 box = boxIntersectInv(ro, rd, size, normal,false);

    if (box.z >= T)
        return color;

    vec4 col = newColor;
    if(!emissive){
        
    col = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(TBN * normal), col);
    }
    T = box.z;

    return col;

}


vec4 sBoxInv(vec3 ro, vec3 rd, vec3 size, mat3 TBN, vec4 color, inout float T, vec4 dSide, vec4 uSide, vec4 nSide, vec4 eSide, vec4 sSide, vec4 wSide,bool enableEmissive)
{
    vec2 texSize = textureSize(Sampler0, 0);
    vec3 normal = VECNULL;

    vec3 box = boxIntersectInv(ro, rd, size, normal,true);

    if (box.z >= T)
        return color;

    vec4 col = VECNULL4;

    if (normal.x > 0.9)
    {
        col = texture(Sampler0, (eSide.xy + eSide.zw * box.xy) / texSize);
    }
    else if (normal.x < -0.9)
    {
        col = texture(Sampler0, (wSide.xy + wSide.zw * vec2(1 - box.x, box.y)) / texSize);
    }
    else if (normal.z > 0.9)
    {
        col = texture(Sampler0, (sSide.xy + sSide.zw * vec2(1 - box.x, box.y)) / texSize);
    }
    else if (normal.z < -0.9)
    {
        col = texture(Sampler0, (nSide.xy + nSide.zw * box.xy) / texSize);
    }
    else if (normal.y > 0.9)
    {
        col = texture(Sampler0, (uSide.xy + uSide.zw * box.xy) / texSize);
    }
    else if (normal.y < -0.9)
    {
        col = texture(Sampler0, (dSide.xy + dSide.zw * box.xy) / texSize);
    }

    if(!enableEmissive){

    col = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(TBN * normal), col);
    }

    if (col.a < 0.1) return color;

    if (col.rgb == VECNULL ){
        return color;
    }

    T = box.z;

    return col;
}

vec4 sBoxInvWithRotation(vec3 ro, vec3 rd, vec3 size, mat3 TBN, vec4 color, inout float T, vec4 dSide, vec4 uSide, vec4 nSide, vec4 eSide, vec4 sSide, vec4 wSide, float dSideRotationAngle, float uSideRotationAngle, float nSideRotationAngle, float eSideRotationAngle, float sSideRotationAngle, float wSideRotationAngle, bool enableEmissive)
{
    vec2 texSize = textureSize(Sampler0, 0);
    vec3 normal = VECNULL;

    if(isInside(ro, rd, size, normal,true)){
        return color;
    }
    vec3 box = boxIntersectInv(ro, rd, size, normal,true);

    if (box.z >= T)
        return color;

    vec4 col = VECNULL4;

    if (normal.x > 0.9)
    {
        vec2 rotatedUV = eSide.xy + eSide.zw * box.xy;

        if (eSideRotationAngle == 1.0) {
            rotatedUV = eSide.xy + eSide.zw * box.yx;
        } else if (eSideRotationAngle == 2.0) {
            rotatedUV = eSide.xy + eSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
        } else if (eSideRotationAngle == 3.0) {
            rotatedUV = eSide.xy + eSide.zw * vec2(1.0 - box.y, box.x);
        } 
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.x < -0.9)
    {
        vec2 rotatedUV = wSide.xy + wSide.zw * vec2(1 - box.x, box.y);
        if (wSideRotationAngle == 1.0) {
            rotatedUV = wSide.xy + wSide.zw * vec2(box.y, 1.0 - box.x);
        } else if (wSideRotationAngle == 2.0) {
            rotatedUV = wSide.xy + wSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
        } else if (wSideRotationAngle == 3.0) {
            rotatedUV = wSide.xy + wSide.zw * vec2(1.0 - box.y, box.x);
        } 
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.z > 0.9)
    {
        vec2 rotatedUV = sSide.xy + sSide.zw * vec2(1 - box.x, box.y);
        if (sSideRotationAngle == 1.0) {
            rotatedUV = sSide.xy + sSide.zw * vec2(box.y, 1.0 - box.x);
        } else if (sSideRotationAngle == 2.0) {
            rotatedUV = sSide.xy + sSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
        } else if (sSideRotationAngle == 3.0) {
            rotatedUV = sSide.xy + sSide.zw * vec2(1.0 - box.y, box.x);
        }
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.z < -0.9)
    {
        vec2 rotatedUV = nSide.xy + nSide.zw * box.xy;
        if (nSideRotationAngle == 1.0) {
            rotatedUV = nSide.xy + nSide.zw * box.yx;
        } else if (nSideRotationAngle == 2.0) {
            rotatedUV = nSide.xy + nSide.zw * vec2(1.0 - box.x, 1.0 - box.y);
        } else if (nSideRotationAngle == 3.0) {
            rotatedUV = nSide.xy + nSide.zw * vec2(1.0 - box.y, box.x);
        }
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.y > 0.9)
    {
        vec2 rotatedUV = uSide.xy + uSide.zw * box.xy;
        if (uSideRotationAngle == 1.0) {
            rotatedUV = uSide.xy + uSide.zw * box.yx;
        } 
        col = texture(Sampler0, (rotatedUV) / texSize);
    }
    else if (normal.y < -0.9)
    {
        vec2 rotatedUV = dSide.xy + dSide.zw * box.xy;
        if (dSideRotationAngle == 1.0) {
            rotatedUV = dSide.xy + dSide.zw * box.yx;
        } 
        col = texture(Sampler0, (rotatedUV) / texSize);
    }

    if(!enableEmissive){
        col = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(TBN * normal), col);
    }

    if (col.a < 0.1) return color;

    if (col.rgb == VECNULL ){
        return color;
    }

    T = box.z;

    return col;
}


vec4 sBoxInv_Solid(vec3 ro, vec3 rd, vec3 size, mat3 TBN, vec4 color, inout float T,vec4 newColor,bool emissive)
{

    vec2 texSize = textureSize(Sampler0, 0);
    vec3 normal = VECNULL;

    vec3 box = boxIntersectInv(ro, rd, size, normal,false);

    if (box.z >= T)
        return color;

    vec4 col = newColor;
    if(!emissive){
        
    col = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(TBN * normal), col);
    }
    T = box.z;

    return col;

}



#define ADD_SQUARE(p1, p2, p3, uv)  \
    color = sSquare(-center, dirTBN, matf_verifyPos(p1) * modelSize, matf_verifyPos(p2) * modelSize, matf_verifyPos(p3) * modelSize,  false,vertexColor, color, minT, uv);



#define ADD_SQUARE_LIGHT_FIX(p1, p2, p3, uv)  \
    color = sSquare(-center, dirTBN, matf_verifyPos(p1) * modelSize, (p2) * modelSize, matf_verifyPos(p3) * modelSize,  true,vertexColor, color, minT, uv);


#define ADD_BOX(pos, size, dSide, uSide, nSide, eSide, sSide, wSide)  \
    color = sBox(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT, uSide, dSide, nSide, wSide, sSide, eSide,false);


#define ADD_BOX_ROTATE(pos, size, Rotation, rotPivot, dSide, uSide, nSide, eSide, sSide, wSide)  \
    color = sBox(Rotation * (-center + (matf_verifyPos(pos)+ rotPivot) * modelSize) - rotPivot * modelSize, Rotation * dirTBN, matf_verifySize(size) * modelSize, TBN * inverse(Rotation), color, minT, uSide, dSide, nSide, wSide, sSide, eSide,false) \


#define ADD_SQUARE_ROTATE(pos, size, Rotation, rotPivot, uv)  \
    color = sSquareWithRotation(Rotation * (-center + (matf_verifyPos(pos)+ rotPivot) * modelSize) - rotPivot * modelSize, Rotation * dirTBN, matf_verifySize(size) * modelSize, vertexColor, color, minT, uv, rotAngle);


#define ADD_TRIANGLE(p1, p2, p3, uv)  \
    color = sTris(-center, dirTBN, p1 * modelSize, p2 * modelSize, p3 * modelSize, color, minT, uv);


#define ADD_TRIANGLE_WITH_ROTATION(p1, p2, p3, uv, rotAngle)  \
    color = sTrisWithRotation(-center, dirTBN, p1 * modelSize, p2 * modelSize, p3 * modelSize,  false,vertexColor, color, minT, uv, rotAngle);

#define ADD_TRIANGLE_WITH_ROTATION_LIGHT_FIX(p1, p2, p3, uv, rotAngle)  \
    color = sTrisWithRotation(-center, dirTBN, p1 * modelSize, p2 * modelSize, p3 * modelSize,  true,vertexColor, color, minT, uv, rotAngle);

#define ADD_QUAD(p1, p2, p3, p4, uv)  \
    color = sQuad(-center, dirTBN, modelSize, p1 * modelSize, p2 * modelSize, p3 * modelSize, p4 * modelSize, vertexColor, color, minT, uv);

#define ADD_SQUARE_WITH_ROTATION(p1, p2, p3, uv, rotAngle)  \
    color = sSquareWithRotation(-center, dirTBN, p1 * modelSize, p2 * modelSize, p3 * modelSize, false,vertexColor, color, minT, uv, rotAngle);

#define ADD_SQUARE_WITH_ROTATION_LIGHT_FIX(p1, p2, p3, uv, rotAngle)  \
    color = sSquareWithRotation(-center, dirTBN, p1 * modelSize, p2 * modelSize, p3 * modelSize, true,vertexColor, color, minT, uv, rotAngle);

#define ADD_SQUARE_WITH_ROTATION_ROTATE(pos, size, Rotation, rotPivot, uv, rotAngle)  \
    color = sSquareWithRotation(Rotation * (-center + (matf_verifyPos(pos)+ rotPivot) * modelSize) - rotPivot * modelSize, Rotation * dirTBN, matf_verifySize(size) * modelSize,  false,vertexColor, color, minT, uv, rotAngle) \


#define ADD_BOX_WITH_ROTATION(pos, size, dSide, uSide, nSide, eSide, sSide, wSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle)  \
    color = sBoxWithRotation(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT, uSide, dSide, nSide, wSide, sSide, eSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle,false);


#define ADD_BOX_WITH_ROTATION_ROTATE(pos, size, Rotation, rotPivot, dSide, uSide, nSide, eSide, sSide, wSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle)  \
    color = sBoxWithRotation(Rotation * (-center + (matf_verifyPos(pos)+ rotPivot) * modelSize) - rotPivot * modelSize, Rotation * dirTBN, matf_verifySize(size) * modelSize, TBN * inverse(Rotation), color, minT, uSide, dSide, nSide, wSide, sSide, eSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle,false) \


#define ADD_BOX_WITH_ROTATION_ROTATE_NO_TRANSPARENCY(pos, size, Rotation, rotPivot, dSide, uSide, nSide, eSide, sSide, wSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle)  \
    color = sBoxWithRotation(Rotation * (-center + (matf_verifyPos(pos)+ rotPivot) * modelSize) - rotPivot * modelSize, Rotation * dirTBN, matf_verifySize(size) * modelSize, TBN * inverse(Rotation), color, minT, uSide, dSide, nSide, wSide, sSide, eSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle,true) \

#define ADD_BOX_WITH_ROTATION_NO_TRANSPARENCY(pos, size, dSide, uSide, nSide, eSide, sSide, wSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle)  \
    color = sBoxWithRotation(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT, uSide, dSide, nSide, wSide, sSide, eSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle,true);

#define ADD_BOX_WITH_ROTATION_EMISSIVE(pos, size, dSide, uSide, nSide, eSide, sSide, wSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle)  \
    color = sBoxWithRotationEmissive(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT, uSide, dSide, nSide, wSide, sSide, eSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle,false);


#define ADD_BOX_WITH_ROTATION_ROTATE_EMISSIVE(pos, size, Rotation, rotPivot, dSide, uSide, nSide, eSide, sSide, wSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle)  \
    color = sBoxWithRotationEmissive(Rotation * (-center + (matf_verifyPos(pos)+ rotPivot) * modelSize) - rotPivot * modelSize, Rotation * dirTBN, matf_verifySize(size) * modelSize, TBN * inverse(Rotation), color, minT, uSide, dSide, nSide, wSide, sSide, eSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle,false) \


#define ADD_BOX_WITH_ROTATION_ROTATE_NO_TRANSPARENCY_EMISSIVE(pos, size, Rotation, rotPivot, dSide, uSide, nSide, eSide, sSide, wSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle)  \
    color = sBoxWithRotationEmissive(Rotation * (-center + (matf_verifyPos(pos)+ rotPivot) * modelSize) - rotPivot * modelSize, Rotation * dirTBN, matf_verifySize(size) * modelSize, TBN * inverse(Rotation), color, minT, uSide, dSide, nSide, wSide, sSide, eSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle,true) \

#define ADD_BOX_WITH_ROTATION_NO_TRANSPARENCY_EMISSIVE(pos, size, dSide, uSide, nSide, eSide, sSide, wSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle)  \
    color = sBoxWithRotationEmissive(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT, uSide, dSide, nSide, wSide, sSide, eSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle,true);



#define ADD_BOX_NO_TRANSPARENCY(pos, size, dSide, uSide, nSide, eSide, sSide, wSide)  \
    color = sBox(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT, uSide, dSide, nSide, wSide, sSide, eSide,true);


#define ADD_BOX_ROTATE_NO_TRANSPARENCY(pos, size, Rotation, rotPivot, dSide, uSide, nSide, eSide, sSide, wSide)  \
    color = sBox(Rotation * (-center + (matf_verifyPos(pos)+ rotPivot) * modelSize) - rotPivot * modelSize, Rotation * dirTBN, matf_verifySize(size) * modelSize, TBN * inverse(Rotation), color, minT, uSide, dSide, nSide, wSide, sSide, eSide,true) \


#define ADD_SQUARE_SOLID(p1, p2, p3, color)  \
    color = sSquare_Solid(-center, dirTBN, p1 * modelSize, p2 * modelSize, p3 * modelSize, color, minT);


#define ADD_BOX_SOLID(pos, size, newColor)  \
    color = sBox_Solid(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT,newColor,false);


#define ADD_BOX_SOLID_EMISSIVE(pos, size, newColor)  \
    color = sBox_Solid(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT,newColor,true);

#define ADD_BOX_EXT_SOLID(pos, size, newColor)  \
    color = sBoxExt_Solid(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT,newColor,false);


#define ADD_BOX_EXT_SOLID_EMISSIVE(pos, size, newColor)  \
    color = sBoxExt_Solid(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT,newColor,true);


#define ADD_TRIANGLE_SOLID(p1, p2, p3, color)  \
    color = sTris_Solid(-center, dirTBN, p1 * modelSize, p2 * modelSize, p3 * modelSize, vertexColor, color, minT);


#define APPLY_SHADOW() \
    color *= cem_light;


#define ADD_BOX_EXT(pos, size, dSide, uSide, nSide, eSide, sSide, wSide)  \
    color = sBoxExt(-center + pos* modelSize, dirTBN, size * modelSize, TBN, color, minT, dSide, uSide, nSide, wSide, sSide, eSide);


#define ADD_BOX_EXT_ROTATE(pos, size, Rotation, rotPivot, dSide, uSide, nSide, eSide, sSide, wSide)  \
    color = sBoxExt(Rotation * (-center + (matf_verifyPos(pos)+ rotPivot) * modelSize) - rotPivot * modelSize, Rotation * dirTBN, matf_verifySize(size) * modelSize, TBN * inverse(Rotation), color, minT, uSide, dSide, nSide, wSide, sSide, eSide,false) \

#define ADD_BOX_EXT_WITH_ROTATION(pos, size, dSide, uSide, nSide, eSide, sSide, wSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle)  \
    color = sBoxExtWithRotation(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT, uSide, dSide, nSide, wSide, sSide, eSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle,false);


#define ADD_BOX_EXT_WITH_ROTATION_ROTATE(pos, size, Rotation, rotPivot, dSide, uSide, nSide, eSide, sSide, wSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle)  \
    color = sBoxExtWithRotation(Rotation * (-center + (matf_verifyPos(pos)+ rotPivot) * modelSize) - rotPivot * modelSize, Rotation * dirTBN, matf_verifySize(size) * modelSize, TBN * inverse(Rotation), color, minT, uSide, dSide, nSide, wSide, sSide, eSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle,false) \


#define ADD_BOX_EXT_WITH_ROTATION_ROTATE_NO_TRANSPARENCY(pos, size, Rotation, rotPivot, dSide, uSide, nSide, eSide, sSide, wSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle)  \
    color = sBoxExtWithRotation(Rotation * (-center + (matf_verifyPos(pos)+ rotPivot) * modelSize) - rotPivot * modelSize, Rotation * dirTBN, matf_verifySize(size) * modelSize, TBN * inverse(Rotation), color, minT, uSide, dSide, nSide, wSide, sSide, eSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle,true) \

#define ADD_BOX_EXT_WITH_ROTATION_NO_TRANSPARENCY(pos, size, dSide, uSide, nSide, eSide, sSide, wSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle)  \
    color = sBoxExtWithRotation(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT, uSide, dSide, nSide, wSide, sSide, eSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle,true);


#define ADD_BOX_EXT_NO_TRANSPARENCY(pos, size, dSide, uSide, nSide, eSide, sSide, wSide)  \
    color = sBoxExt(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT, uSide, dSide, nSide, wSide, sSide, eSide,true);


#define ADD_BOX_EXT_ROTATE_NO_TRANSPARENCY(pos, size, Rotation, rotPivot, dSide, uSide, nSide, eSide, sSide, wSide)  \
    color = sBoxExt(Rotation * (-center + (matf_verifyPos(pos)+ rotPivot) * modelSize) - rotPivot * modelSize, Rotation * dirTBN, matf_verifySize(size) * modelSize, TBN * inverse(Rotation), color, minT, uSide, dSide, nSide, wSide, sSide, eSide,true) \


#define ADD_BOX_INV_WITH_ROTATION(pos, size, dSide, uSide, nSide, eSide, sSide, wSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle)  \
    color = sBoxInvWithRotation(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT, uSide, dSide, nSide, wSide, sSide, eSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle,false);


#define ADD_BOX_INV_WITH_ROTATION_ROTATE(pos, size, Rotation, rotPivot, dSide, uSide, nSide, eSide, sSide, wSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle)  \
    color = sBoxInvWithRotation(Rotation * (-center + (matf_verifyPos(pos)+ rotPivot) * modelSize) - rotPivot * modelSize, Rotation * dirTBN, matf_verifySize(size) * modelSize, TBN * inverse(Rotation), color, minT, uSide, dSide, nSide, wSide, sSide, eSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle,false) \


#define ADD_BOX_INV(pos, size, dSide, uSide, nSide, eSide, sSide, wSide)  \
    color = sBoxInv(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT, uSide, dSide, nSide, wSide, sSide, eSide,false);


#define ADD_BOX_ROTATE_INV(pos, size, Rotation, rotPivot, dSide, uSide, nSide, eSide, sSide, wSide)  \
    color = sBoxInv(Rotation * (-center + (matf_verifyPos(pos)+ rotPivot) * modelSize) - rotPivot * modelSize, Rotation * dirTBN, matf_verifySize(size) * modelSize, TBN * inverse(Rotation), color, minT, uSide, dSide, nSide, wSide, sSide, eSide,false) \


#define ADD_BOX_SOLID_INV(pos, size, newColor)  \
    color = sBoxInv_Solid(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT,newColor,false);


#define ADD_BOX_INV_WITH_ROTATION_EMISSIVE(pos, size, dSide, uSide, nSide, eSide, sSide, wSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle)  \
    color = sBoxInvWithRotation(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT, uSide, dSide, nSide, wSide, sSide, eSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle,true);


#define ADD_BOX_INV_WITH_ROTATION_ROTATE_EMISSIVE(pos, size, Rotation, rotPivot, dSide, uSide, nSide, eSide, sSide, wSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle)  \
    color = sBoxInvWithRotation(Rotation * (-center + (matf_verifyPos(pos)+ rotPivot) * modelSize) - rotPivot * modelSize, Rotation * dirTBN, matf_verifySize(size) * modelSize, TBN * inverse(Rotation), color, minT, uSide, dSide, nSide, wSide, sSide, eSide, dSideRotationAngle, uSideRotationAngle, nSideRotationAngle, eSideRotationAngle, sSideRotationAngle, wSideRotationAngle,true) \


#define ADD_BOX_INV_EMISSIVE(pos, size, dSide, uSide, nSide, eSide, sSide, wSide)  \
    color = sBoxInv(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT, uSide, dSide, nSide, wSide, sSide, eSide,true);


#define ADD_BOX_ROTATE_INV_EMISSIVE(pos, size, Rotation, rotPivot, dSide, uSide, nSide, eSide, sSide, wSide)  \
    color = sBoxInv(Rotation * (-center + (matf_verifyPos(pos)+ rotPivot) * modelSize) - rotPivot * modelSize, Rotation * dirTBN, matf_verifySize(size) * modelSize, TBN * inverse(Rotation), color, minT, uSide, dSide, nSide, wSide, sSide, eSide,true) \


#define ADD_BOX_SOLID_INV_EMISSIVE(pos, size, newColor)  \
    color = sBoxInv_Solid(-center + matf_verifyPos(pos)* modelSize, dirTBN, matf_verifySize(size) * modelSize, TBN, color, minT,newColor,true);
