in vec4 cem_pos1, cem_pos2, cem_pos3, cem_pos4;
in vec3 cem_uv1, cem_uv2;
in vec3 cem_glPos;
in vec4 cem_lightMapColor;
flat in int cem;
flat in int cem_reverse;
flat in vec4 cem_light;

#define MAX_DEPTH 1024000.0

vec3 boxIntersect(vec3 ro, vec3 rd, vec3 size, out vec3 outNormal) {
    vec3 m = 1.0 / rd;
    vec3 n = m * ro;
    vec3 k = abs(m) * size;
    vec3 t1 = -n - k;
    vec3 t2 = -n + k;
    float tN = max(max(t1.x, t1.y), t1.z);
    float tF = min(min(t2.x, t2.y), t2.z);

    if (tN > tF || tF < 0.0) return vec3(0, 0, MAX_DEPTH);

    outNormal = -sign(rd) * step(t1.yzx, t1.xyz) * step(t1.zxy, t1.xyz);
    float t = tN > 0.0 ? tN : tF;

    vec3 pos = (ro + rd * t) / size;
    vec2 tex;
    bvec3 isAxis = bvec3(abs(outNormal.x) > 0.9, abs(outNormal.y) > 0.9, abs(outNormal.z) > 0.9);
    tex = isAxis.x ? pos.zy : (isAxis.y ? pos.xz : pos.xy);

    return vec3(clamp(tex * 0.5 + 0.5, vec2(0), vec2(1)), t);
}

vec3 boxIntersectBack(vec3 ro, vec3 rd, vec3 size, out vec3 outNormal) {
    vec3 m = 1.0 / rd;
    vec3 n = m * ro;
    vec3 k = abs(m) * size;
    vec3 t1 = -n - k;
    vec3 t2 = -n + k;
    float tN = max(max(t1.x, t1.y), t1.z);
    float tF = min(min(t2.x, t2.y), t2.z);

    if (tN > tF || tF < 0.0) return vec3(0, 0, MAX_DEPTH);

    outNormal = sign(rd) * step(t2.xyz, t2.yzx) * step(t2.xyz, t2.zxy);

    vec3 pos = (ro + rd * tF) / size;
    vec2 tex;
    bvec3 isAxis = bvec3(abs(outNormal.x) > 0.9, abs(outNormal.y) > 0.9, abs(outNormal.z) > 0.9);
    tex = isAxis.x ? pos.zy : (isAxis.y ? pos.xz : pos.xy);

    return vec3(clamp(tex * 0.5 + 0.5, vec2(0), vec2(1)), tF);
}

vec4 sampleFace(vec3 normal, vec2 uv, vec2 texSize,
                vec4 up, vec4 down, vec4 north, vec4 east, vec4 south, vec4 west) {
    vec4 side;
    vec2 tc = uv;

    if      (normal.y >  0.9) { side = up;    }
    else if (normal.y < -0.9) { side = down;  }
    else if (normal.x >  0.9) { side = west;  }
    else if (normal.x < -0.9) { side = east;  tc.x = 1.0 - uv.x; }
    else if (normal.z < -0.9) { side = north; }
    else                      { side = south; tc.x = 1.0 - uv.x; }

    if (side == vec4(0)) return vec4(0);
    return texture(Sampler0, (side.xy + side.zw * tc) / texSize);
}

vec4 renderBox(vec3 ro, vec3 rd, vec3 size, mat3 lightTBN, vec4 color, inout float T,
               vec4 up, vec4 down, vec4 north, vec4 east, vec4 south, vec4 west,
               bool emissive) {
    vec3 normal;

    {
        vec3 m = 1.0 / rd;
        vec3 n = m * ro;
        vec3 k = abs(m) * size;
        vec3 t1 = -n - k;
        if (max(max(t1.x, t1.y), t1.z) < 0.0) return color;
    }

    vec3 box = boxIntersect(ro, rd, size, normal);
    if (box.z >= T) return color;

    vec2 texSize = textureSize(Sampler0, 0);
    vec4 col = sampleFace(normal, box.xy, texSize, up, down, north, east, south, west);

    if (col.a < 0.1) {
        box = boxIntersectBack(ro, rd, size, normal);
        if (box.z >= T) return color;
        col = sampleFace(normal, box.xy, texSize, up, down, north, east, south, west);
    }

    if (col.a < 0.1) return color;

    if (!emissive)
        col = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(lightTBN * normal), col);

    T = box.z;
    return col;
}

void writeDepth(vec3 Pos) {
    if (ProjMat[3][0] == -1) {
        vec4 ProjPos = ProjMat * vec4(Pos, 1);
        gl_FragDepth = ProjPos.z / ProjPos.w * 0.5 + 0.5;
    } else {
        vec4 ProjPos = ProjMat * ModelViewMat * vec4(Pos, 1);
        gl_FragDepth = ProjPos.z / ProjPos.w * 0.5 + 0.5;
    }
}

#define CEM_BOX(pos, size, elemRot, pivot, up, down, north, east, south, west, emissive) \
    { \
        vec3 _p = PIX * (matf_verifyPos(pos) * modelSize); \
        vec3 _piv = PIX * (pivot * modelSize); \
        vec3 _v = PIX * (-center) + _p - _piv; \
        vec3 _ro = 2.0 * _v - elemRot * _v + _piv; \
        vec3 _rd = elemRot * (PIX * dirTBN); \
        color = renderBox(_ro, _rd, matf_verifySize(size) * modelSize, \
            TBN * transpose(PIX) * transpose(elemRot), \
            color, minT, up, down, north, east, south, west, emissive); \
    }
