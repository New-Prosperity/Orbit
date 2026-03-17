# 3D Armor Shader System — Deep Technical Reference

## Table of Contents

1. [How Minecraft Renders Armor](#1-how-minecraft-renders-armor)
2. [The Ray-Casting Technique](#2-the-ray-casting-technique)
3. [Detection: Identifying Custom Armor](#3-detection-identifying-custom-armor)
4. [TBN Matrix: Building a Local Coordinate System](#4-tbn-matrix-building-a-local-coordinate-system)
5. [Box Intersection: The Slab Method](#5-box-intersection-the-slab-method)
6. [UV Mapping: Texturing the Hit](#6-uv-mapping-texturing-the-hit)
7. [Rotation & Pivots](#7-rotation--pivots)
8. [Depth Writing](#8-depth-writing)
9. [Lighting](#9-lighting)
10. [Current Implementation Analysis](#10-current-implementation-analysis)
11. [Known Issues & Limitations](#11-known-issues--limitations)
12. [Rebuild Plan](#12-rebuild-plan)

---

## 1. How Minecraft Renders Armor

### Shader Pipeline (1.21.4+)

Mojang consolidated all entity rendering into a single shader pair: `entity.vsh` / `entity.fsh`. The old per-rendertype files (`rendertype_armor_cutout_no_cull.vsh/.fsh`) no longer exist. All entity render types use the same shaders with behavior controlled via preprocessor defines. Armor specifically uses:

- `ALPHA_CUTOUT = 0.1` (hard alpha test, no partial transparency)
- `NO_OVERLAY` (skip damage red tint)

### Vertex Attributes

| Attribute | Type | Armor Usage |
|-----------|------|-------------|
| `Position` | `vec3` | Model-space vertex position |
| `Color` | `vec4` | Leather dye color (our armor ID carrier) |
| `UV0` | `vec2` | Texture coordinates into leather_layer_1/2.png |
| `UV1` | `ivec2` | Overlay texture (damage flash) |
| `UV2` | `ivec2` | Lightmap coordinate |
| `Normal` | `vec3` | Surface normal |

### Key Uniforms

| Uniform | Purpose |
|---------|---------|
| `ModelViewMat` | Model-view transform |
| `ProjMat` | Projection matrix |
| `Light0_Direction`, `Light1_Direction` | Directional entity lights |
| `Sampler0` | Entity texture (leather atlas) |
| `Sampler2` | Lightmap |
| `GameTime` | Monotonic time for animation |

### Armor Geometry

Each armor piece is rendered as a set of quads: 6 faces per cube, multiple cubes per piece. Each quad = 4 vertices. The game sends `gl_VertexID` which identifies vertex position within the primitive. `gl_VertexID / 4` gives the face index, `gl_VertexID % 4` gives the corner (0-3).

### 1.21.5+ Changes

- 1.21.5: Shader JSON config removed. `RenderPipeline` in code. Fog split to `sphericalVertexDistance`/`cylindricalVertexDistance`.
- 1.21.6: All uniforms converted to uniform blocks (`DynamicTransforms`, `Projection`, `Globals`). Imports changed to `<minecraft:dynamictransforms.glsl>`, `<minecraft:projection.glsl>`, `<minecraft:globals.glsl>`.

The core ray-casting technique is unaffected. Only boilerplate (imports, uniform access, fog) changes.

---

## 2. The Ray-Casting Technique

### The Core Idea

Hijack a single face (quad) of the vanilla armor model. Hide all other faces. In the fragment shader, cast a ray from the camera through each pixel of this enlarged quad. Test the ray against multiple AABB boxes. Render the closest hit with proper texturing, lighting, and depth.

### Why This Works

Core shaders cannot create new geometry. They can only reposition existing vertices and compute per-pixel colors. The fragment shader runs once per pixel of the rasterized quad. By making the quad large enough (enlarged in the vertex shader), every pixel of the 3D armor model falls within this quad's rasterization area.

### Why There's No Alternative

- **Geometry shaders**: Minecraft doesn't expose a geometry shader stage
- **Vertex displacement**: Can reshape existing quads but can't create new faces
- **ObjMC**: Bakes mesh data into textures; works for items, not entity armor
- **Multi-pass**: No multi-pass support in core shaders

Ray-casting from a single quad is the only approach.

---

## 3. Detection: Identifying Custom Armor

### Leather Armor Vertex Color

The server gives a player leather armor with a specific dye color via the `color` component. This color flows through as the `Color` vertex attribute in the shader.

### How We Detect

In the vertex shader:
1. Read `Color.rgb` from the vertex
2. Encode as integer: `(r << 16) + (g << 8) + b`
3. Switch on this value to find the matching armor definition
4. Set `cords` to the armor's atlas cell position

```glsl
int vertexColorId = colorId(Color.rgb);
switch(vertexColorId) {
    default:
    COLOR_ARMOR(r1, g1, b1) cords = vec2(cellIndex, 0);
    COLOR_ARMOR(r2, g2, b2) cords = vec2(cellIndex, 0);
    return true;
}
```

### Texture Atlas Layout

Each armor occupies a 64x32 cell in the leather texture atlas. Cells are placed side by side horizontally. Marker pixels at `(63, 30)` and `(63, 31)` identify the layer type and color ID.

---

## 4. TBN Matrix: Building a Local Coordinate System

### Vertex Shader: Capturing the Quad

The vertex shader captures all 4 vertex positions via `gl_VertexID % 4`:

```glsl
switch (gl_VertexID % 4) {
    case 0: cem_pos1 = modelPos; cem_uv1 = vec3(uv, 1); break;
    case 1: cem_pos2 = modelPos; break;
    case 2: cem_pos3 = modelPos; cem_uv2 = vec3(uv, 1); break;
    case 3: cem_pos4 = modelPos; break;
}
```

The quad is enlarged for sufficient rasterization area:
```glsl
vec2 cornerT = corner * 2 - 1;
vec4 cem_Pos = ModelViewMat * modelPos + vec4(cornerT * 2.5 * cem_size, 0, 0);
```

### Fragment Shader: TBN Construction

From `frag_main_setup.glsl`:

```glsl
vec3 Pos1 = round(cem_pos1.xyz * 100000 / cem_pos1.w) * 0.00001;
vec3 Pos2 = round(cem_pos3.xyz * 100000 / cem_pos3.w) * 0.00001;
vec3 Pos3 = gl_PrimitiveID % 2 == 0
    ? round(cem_pos2.xyz * 100000 / cem_pos2.w) * 0.00001
    : round(cem_pos4.xyz * 100000 / cem_pos4.w) * 0.00001;
```

The precision factor (100000) avoids floating-point noise in interpolated positions. `gl_PrimitiveID % 2` distinguishes the two triangles of the quad (GPU splits each quad into 2 triangles).

```glsl
vec3 tangent   = normalize(gl_PrimitiveID % 2 == 0 ? Pos3 - Pos1 : Pos2 - Pos3);
vec3 bitangent = normalize(gl_PrimitiveID % 2 == 1 ? Pos1 - Pos3 : Pos3 - Pos2);
vec3 normalT   = normalize(cross(tangent, bitangent));
mat3 TBN = mat3(tangent, bitangent, normalT);
```

`cem_reverse` flips tangent/bitangent for mirrored geometry (left arm, left leg).

### Ray Setup

```glsl
vec3 rawCenter = (Pos1 + Pos2) / 2;
vec3 center = rawCenter * TBN;                              // ray origin in TBN space
vec3 dirTBN = normalize(cem_glPos * mat3(ModelViewMat) * TBN); // ray direction in TBN space

// GUI/inventory rendering (orthographic projection)
if (ProjMat[3][0] == -1) {
    center = vec3(-cem_glPos.xy + rawCenter.xy, rawCenter.z) * TBN;
    dirTBN = normalize(vec3(0, 0, -1) * TBN);
}

float modelSize = length((gl_PrimitiveID % 2 == 1 ? Pos1 : Pos2) - Pos3);
```

`modelSize` normalizes coordinates so model definitions use pixel-like units. All box positions/sizes are multiplied by `modelSize`.

---

## 5. Box Intersection: The Slab Method

The standard AABB ray-box intersection algorithm (Inigo Quilez / Tavian Barnes):

```glsl
vec3 boxIntersect(vec3 ro, vec3 rd, vec3 size, out vec3 outNormal) {
    vec3 m = 1.0 / rd;              // reciprocal ray direction
    vec3 n = m * ro;                // transformed origin
    vec3 k = abs(m) * size;         // slab half-widths
    vec3 t1 = -n - k;              // near slab intersections
    vec3 t2 = -n + k;              // far slab intersections
    float tN = max(max(t1.x, t1.y), t1.z);  // entry t
    float tF = min(min(t2.x, t2.y), t2.z);  // exit t

    if (tN > tF || tF < 0.0) return vec3(MAX_DEPTH); // miss

    outNormal = -sign(rd) * step(t1.yzx, t1.xyz) * step(t1.zxy, t1.xyz);
    float t = tN > 0.0 ? tN : tF; // entry if in front, exit if inside

    vec3 pos = (ro + rd * t) / size; // hit point normalized to [-1,1]
    vec2 tex;
    if      (abs(outNormal.x) > 0.9) tex = pos.zy;
    else if (abs(outNormal.y) > 0.9) tex = pos.xz;
    else                              tex = pos.xy;

    return vec3(clamp(tex * 0.5 + 0.5, vec2(0), vec2(1)), t);
}
```

**How it works:**
1. For each axis, compute when the ray enters and exits the slab `[-size, +size]`
2. Entry = max of all near values. Exit = min of all far values.
3. Entry > exit or exit < 0 = miss
4. Normal = which axis had the largest entry t-value, oriented outward
5. UV = the two axes orthogonal to the hit normal, mapped to [0,1]

### boxIntersectInv (Back-Face Variant)

Used when the camera is inside a box. Returns the exit intersection with inverted normals. CEM-S calls this in `sBoxExt` as a fallback when the front-face hit is transparent.

---

## 6. UV Mapping: Texturing the Hit

After intersection, each face maps to a different texture region:

```glsl
// Each side is vec4(startX, startY, sizeX, sizeY) in texel coordinates
if (normal.x > 0.9)        // East
    col = texture(Sampler0, (eSide.xy + eSide.zw * box.xy) / texSize);
else if (normal.x < -0.9)  // West (mirrored X)
    col = texture(Sampler0, (wSide.xy + wSide.zw * vec2(1-box.x, box.y)) / texSize);
else if (normal.z > 0.9)   // South (mirrored X)
    col = texture(Sampler0, (sSide.xy + sSide.zw * vec2(1-box.x, box.y)) / texSize);
else if (normal.z < -0.9)  // North
    col = texture(Sampler0, (nSide.xy + nSide.zw * box.xy) / texSize);
else if (normal.y > 0.9)   // Up
    col = texture(Sampler0, (uSide.xy + uSide.zw * box.xy) / texSize);
else if (normal.y < -0.9)  // Down
    col = texture(Sampler0, (dSide.xy + dSide.zw * box.xy) / texSize);
```

West and South faces mirror the X coordinate because they're viewed from the opposite direction.

### Face Parameter Reorder

The `ADD_BOX` macro reorders parameters: user-facing order is `(d, u, n, e, s, w)`, but `sBox` receives `(u, d, n, w, s, e)`. This is due to the TBN coordinate system's axis conventions. This reorder is a major source of bugs.

---

## 7. Rotation & Pivots

### Rotation Matrix

```glsl
mat3 Rotate3(float angle, int type) {
    float s = sin(angle), c = cos(angle);
    if (type == 0) return mat3(1,0,0, 0,c,-s, 0,s,c);  // X
    if (type == 1) return mat3(c,0,s, 0,1,0, -s,0,c);  // Y
    if (type == 2) return mat3(c,-s,0, s,c,0, 0,0,1);  // Z
}
```

### Applying Rotation to Ray-Box Test

The rotation transforms the ray into the box's local space:

```glsl
ADD_BOX_ROTATE(pos, size, Rotation, rotPivot, faces...) =>
    sBox(
        Rotation * (-center + (pos + rotPivot) * modelSize) - rotPivot * modelSize,  // rotated ray origin
        Rotation * dirTBN,                                                             // rotated ray direction
        size * modelSize,                                                              // box size unchanged
        TBN * inverse(Rotation),                                                       // TBN adjusted for lighting
        ...
    )
```

1. Translate ray origin to pivot space
2. Apply rotation
3. Translate back
4. Rotate ray direction
5. Inverse-rotate TBN for correct lighting normals

### Composed Rotations (Multi-Joint)

For elements with group rotations (element rotation + parent group rotation), rotation matrices are multiplied inner-to-outer. The center position is pre-baked in Kotlin by `ArmorGlslGenerator.bakeRotatedCenter()`, applying each rotation level's pivot transform.

### BBModel → TBN Coordinate Mapping

Blockbench uses a different coordinate system than the TBN shader space:
- Center: `(-bbX, -bbZ, -bbY)` relative to bone origin
- Half size: `(halfX, halfZ, halfY)` + inflate
- Pivot: `(-pivotX, -pivotZ, -pivotY)` relative to bone origin
- Rotation: BB Euler `(x, y, z)` → TBN components `Y=-bbZ`, `Z=-bbY`, `X=-bbX` (order: X, Z, Y, skip zeros)

---

## 8. Depth Writing

Ray-cast geometry has real depth but the rasterized quad is flat. Without depth correction, 3D boxes clip through other geometry.

```glsl
void writeDepth(vec3 Pos) {
    if (ProjMat[3][0] == -1) {
        vec4 ProjPos = ProjMat * vec4(Pos, 1);
        gl_FragDepth = ProjPos.z / ProjPos.w * 0.5 + 0.5;
    } else {
        vec4 ProjPos = ProjMat * ModelViewMat * vec4(Pos, 1);
        gl_FragDepth = ProjPos.z / ProjPos.w * 0.5 + 0.5;
    }
}
```

**Cost**: Writing `gl_FragDepth` disables GPU early depth testing, affecting performance for every fragment.

---

## 9. Lighting

After intersection, transform the hit normal from TBN space back to world space and apply Minecraft's entity lighting:

```glsl
col = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(TBN * normal), col);
```

For rotated boxes, the TBN is pre-adjusted by `TBN * inverse(Rotation)`, so the normal transforms correctly through the composition.

For emissive elements, skip lighting entirely — output the raw texture color.

---

## 10. Current Implementation Analysis

### Architecture

```
BBModel (.bbmodel)
  → ArmorParser.kt     (parse elements, groups, rotations, UV)
  → ArmorGlslGenerator.kt  (generate GLSL switch/case with ADD_BOX macros)
  → ArmorShaderPack.kt     (assemble resource pack: shaders + leather atlas)
  → Resource pack sent to player
```

### What Works

- BBModel parsing with group/element hierarchy
- Texture layer splitting (layer 1 vs layer 2)
- Rotation baking with multi-level pivot composition
- Leather atlas generation with marker pixels
- Detection via vertex color ID

### Problems

#### P1: Massive Function Duplication in frag_funcs.glsl (1186 lines)

8 near-identical box intersection functions:
- `sBox` / `sBoxWithRotation` / `sBoxWithRotationEmissive`
- `sBoxExt` / `sBoxExtWithRotation`
- `sBoxInv` / `sBoxInvWithRotation` / `sBoxInvWithRotationEmissive`

Each is 60-90 lines. The only differences: whether UV rotation is applied, whether lighting is applied (emissive), whether front or back face is tested. **~600 lines of copy-paste.**

#### P2: 50+ Macro Variants

Combinatorial explosion of `ADD_BOX_*` macros:
`ADD_BOX`, `ADD_BOX_WITH_ROTATION`, `ADD_BOX_ROTATE`, `ADD_BOX_EXT`, `ADD_BOX_INV`
...each with `_EMISSIVE`, `_NO_TRANSPARENCY`, `_ROTATE` suffixes.

The generator only uses `ADD_BOX_EXT_WITH_ROTATION_ROTATE`. The other 49 macros are dead code.

#### P3: UV Rotation via Integer Enum

UV rotation uses hardcoded if/else chains testing integer values 0-8:
```glsl
if (rotAngle == 1.0) { ... }
else if (rotAngle == 2.0) { ... }
else if (rotAngle == 3.0) { ... }
```
This should be a 2x2 rotation matrix computed once.

#### P4: No Bounding Box Pre-Test

Every box is tested for every fragment. A model with 20 cubes does 20 ray-box tests per pixel. A single bounding-box pre-test would skip all 20 for rays that miss the model entirely.

#### P5: `col.rgb == VECNULL` as Transparency Check

Pure black pixels (`rgb(0,0,0)`) are treated as transparent. Models that intentionally use black will have holes. Should use alpha channel only.

#### P6: Face Parameter Reorder

The `ADD_BOX` macro silently reorders `(d,u,n,e,s,w)` → `(u,d,n,w,s,e)` when calling `sBox`. This is confusing and error-prone. The function signature should match the user-facing order.

#### P7: Per-Fragment Rotation Matrix Computation

`ADD_BOX_EXT_WITH_ROTATION_ROTATE(pos, size, PIX * Rotate3(a,X) * Rotate3(b,Y), ...)` computes sin/cos and matrix multiplication for every fragment, for every box. These are constants — should be pre-computed once.

#### P8: `sBoxExt` Double Intersection

`sBoxExt` always tests front face first, then if transparent, tests back face. This means 2 intersection tests per box. The back-face fallback is rarely needed and could be an opt-in flag.

---

## 11. Known Issues & Limitations

| Issue | Cause | Severity |
|-------|-------|----------|
| Black pixels invisible | `col.rgb == VECNULL` transparency check | High |
| Performance with many cubes | No bounding box pre-test, per-fragment matrix math | Medium |
| Iris/Optifine incompatible | Core shader replacement conflicts with shader loaders | Inherent |
| Camera-inside-box artifacts | Entry t < 0 handled but doubles intersection work | Low |
| Writing gl_FragDepth | Disables early depth test GPU optimization | Low |
| Transparency sorting | Hard alpha cutoff only; no partial transparency support | Inherent |
| Face mirroring bugs | Silent parameter reorder between macro and function | Medium |

---

## 12. Rebuild Plan

### Phase 1: Clean GLSL (replace frag_funcs.glsl)

Replace 8 duplicate functions + 50 macros with:

1. **Single `rayBox()` function** with flags:
```glsl
struct BoxHit { vec3 uv_t; vec3 normal; bool hit; };
BoxHit rayBox(vec3 ro, vec3 rd, vec3 size, bool backFace);
```

2. **Single `sampleBox()` function** that takes the hit and samples texture:
```glsl
vec4 sampleBox(BoxHit hit, vec4[6] faces, mat3 TBN, bool emissive);
```

3. **Single `ADD_BOX` macro**:
```glsl
#define ADD_BOX(pos, size, rot, pivot, faces, flags)
```

4. **Pre-computed rotation matrices**: Generate `const mat3 rot_N = mat3(...)` at the top of each armor's GLSL block instead of calling `Rotate3()` per fragment.

5. **Bounding box pre-test**: For each armor piece, emit a single AABB encompassing all cubes. Discard early if the ray misses.

6. **Alpha-only transparency**: Replace `col.rgb == VECNULL` with `col.a < 0.1`.

### Phase 2: Clean Kotlin Generator

1. **Pre-compute rotation matrices** in `ArmorGlslGenerator` as literal `mat3(...)` constants
2. **Compute bounding box** per armor piece from all cubes
3. **Remove dead code**: Only generate the single `ADD_BOX` macro variant
4. **Simplify face parameter order**: Match function signature to user-facing order

### Phase 3: Verify

1. Test with existing ranger/warrior armors
2. Verify in-world rendering, inventory rendering (orthographic), and on other players
3. Profile: compare fragment shader time before/after with GPU profiler
