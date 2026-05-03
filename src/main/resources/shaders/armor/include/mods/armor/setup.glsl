#ifdef VSH

int markerYOffset;
if (Normal.y > 0.7) {
    markerYOffset = 64;
} else if (Normal.y < -0.7) {
    markerYOffset = 96;
} else if (abs(Normal.x) >= abs(Normal.z)) {
    markerYOffset = 128;
} else {
    markerYOffset = 160;
}

ivec2 mirrorMarkerPos = ivec2(uv - (1.0 - corner.yx)) + ivec2(0, markerYOffset) + ivec2(RelativeCords.x * 64, 0);
vec4 mirrorMarker = round(texelFetch(Sampler0, mirrorMarkerPos, 0) * 255);
bool isRightSide = (mirrorMarker.r == 255 && mirrorMarker.g == 0 && mirrorMarker.b == 64);
bool isLeftSide = (mirrorMarker.r == 0 && mirrorMarker.g == 64 && mirrorMarker.b == 255);

if (isPartOne) {
    if (uv.y < 16) {
        if (uv.x < 32) {
            cube = STASIS_HELMET;
            bodypart = ARMOR_LEFT_FEET;
        }
    }
    else if (uv.y < 32) {
        if (uv.x < 16) {
            if (isLeftSide) cube = STASIS_LEFT_BOOT;
            else cube = STASIS_RIGHT_BOOT;
        }
        else if (uv.x < 40) {
            cube = STASIS_CHESTPLATE;
        }
        else if (uv.x < 56) {
            if (isLeftSide) cube = STASIS_LEFT_ARM;
            else cube = STASIS_RIGHT_ARM;
        }
    }
}

if (isPartTwo) {
    if (uv.y < 32) {
        if (uv.x < 16) {
            if (isLeftSide) cube = STASIS_LEFT_LEG;
            else cube = STASIS_RIGHT_LEG;
        }
        else if (uv.x < 40) {
            cube = STASIS_INNER_ARMOR;
        }
        else if (uv.x < 56) {
            if (isLeftSide) cube = STASIS_LEFT_LEG;
            else cube = STASIS_RIGHT_LEG;
        }
    }
}

if (cube < STASIS_HELMET) {
    removeAll = 1;
}
#endif
