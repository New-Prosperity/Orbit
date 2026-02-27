#define BOTTOM_FACE 0
#define TOP_FACE 1
#define RIGHT_FACE 2
#define NORTH_FACE 3
#define LEFT_FACE 4
#define SOUTH_FACE 5

ivec4 addCem(ivec4 currentCems,int id){
    if(currentCems.x == -1){
        return ivec4(id,-1,-1,-1);
    }
    if(currentCems.y == -1){
        return ivec4(currentCems.x,id,-1,-1);
    }
    if(currentCems.z == -1){
        return ivec4(currentCems.x,currentCems.y,id,-1);
    }
    if(currentCems.w == -1){
        return ivec4(currentCems.x,currentCems.y,currentCems.z,id);
    }
    return currentCems;
}

mat3 rotationMatrix(vec3 axis, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    float oneMinusC = 1.0 - c;

    vec3 a = normalize(axis);

    mat3 rotation = mat3(
        c + a.x * a.x * oneMinusC, a.x * a.y * oneMinusC - a.z * s, a.x * a.z * oneMinusC + a.y * s,
        a.y * a.x * oneMinusC + a.z * s, c + a.y * a.y * oneMinusC, a.y * a.z * oneMinusC - a.x * s,
        a.z * a.x * oneMinusC - a.y * s, a.z * a.y * oneMinusC + a.x * s, c + a.z * a.z * oneMinusC
    );

    return rotation;
}

vec3 rotateNormal(vec3 normal, vec3 axis, float angle) {
    mat3 rotation = rotationMatrix(axis, angle);
    return rotation * normal;
}

#define VERTEX_FRONT() {\
   vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, rotateNormal(Normal,vec3(1,1,0),radians(180.0)), Color) * texelFetch(Sampler2, UV2 / 16, 0);\
}
#define VERTEX_BACK(){\
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, rotateNormal(Normal,vec3(1,1,0),radians(-90.0)), Color) * texelFetch(Sampler2, UV2 / 16, 0);\
}

#define VERTEXT_ORIGINAL(){\
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color) * texelFetch(Sampler2, UV2 / 16, 0);\
}
