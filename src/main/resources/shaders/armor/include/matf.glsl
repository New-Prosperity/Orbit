#version 150

#define X 0
#define Y 1
#define Z 2

#define PI 3.14159265

#define PI_05 1.570796325
#define PI_025 0.7853981625
#define PI_0125 0.39269908125
#define PI_00625 0.196349540625
#define PI_1 3.14159265
#define PI_15 4.712388975
#define PI_003125 0.0981747703125
#define PI_0015625 0.04908738515625
#define PI_00277778 0.08726646
#define PI_01666667 0.5235988


#define PIX mat3(1,0,0, 0,0,-1, 0,1,0)
#define PIX25 Rotate3(PI_025, X)
#define PIX12 Rotate3(PI_0125, X)
#define PIX62 Rotate3(PI_00625, X)
#define PIX54 Rotate3(PI_01666667, X)
#define PIX9 Rotate3(PI_00277778, X)
#define PIX2 Rotate3(PI_1, X)
#define PIX3 Rotate3(PI_15, X)

#define PIY Rotate3(PI_05, Y)
#define PIY25 mat3( 0.7071067812, 0.0, 0.7071067812,0.0, 1.0, 0.0, -0.7071067812, 0.0, 0.7071067812)
#define PIY12 Rotate3(PI_0125, Y)
#define PIY62 Rotate3(PI_00625, Y)
#define PIY54 Rotate3(PI_01666667, Y)
#define PIY9 Rotate3(PI_00277778, Y)
#define PIY2 Rotate3(PI_1, Y)
#define PIY3 Rotate3(PI_15, Y)

#define PIZ Rotate3(PI_05, Z)
#define PIZ25 Rotate3(PI_025, Z)
#define PIZ12 Rotate3(PI_0125, Z)
#define PIZ62 Rotate3(PI_00625, Z)
#define PIZ54 Rotate3(PI_01666667, Z)
#define PIZ9 Rotate3(PI_00277778, Z)
#define PIZ2 Rotate3(PI_1, Z)
#define PIZ3 Rotate3(PI_15, Z)

#define NIX Rotate3(-PI_05, X)
#define NIX25 Rotate3(-PI_025, X)
#define NIX12 Rotate3(-PI_0125, X)
#define NIX62 Rotate3(-PI_00625, X)
#define NIX54 Rotate3(-PI_01666667, X)
#define NIX9 Rotate3(-PI_00277778, X)
#define NIX2 Rotate3(-PI_1, X)
#define NIX3 Rotate3(-PI_15, X)

#define NIY Rotate3(-PI_05, Y)
#define NIY25 Rotate3(-PI_025, Y)
#define NIY12 Rotate3(-PI_0125, Y)
#define NIY62 Rotate3(-PI_00625, Y)
#define NIY54 Rotate3(-PI_01666667, Y)
#define NIY9 Rotate3(-PI_00277778, Y)
#define NIY2 Rotate3(-PI_1, Y)
#define NIY3 Rotate3(-PI_15, Y)

#define NIZ Rotate3(-PI_05, Z)
#define NIZ25 Rotate3(-PI_025, Z)
#define NIZ12 Rotate3(-PI_0125, Z)
#define NIZ62 Rotate3(-PI_00625, Z)
#define NIZ54 Rotate3(-PI_01666667, Z)
#define NIZ9 Rotate3(-PI_00277778, Z)
#define NIZ2 Rotate3(-PI_1, Z)
#define NIZ3 Rotate3(-PI_15, Z)

#define PIX31 Rotate3(PI_003125, X)
#define PIX16 Rotate3(PI_0015625, X)
#define PIY31 Rotate3(PI_003125, Y)
#define PIY16 Rotate3(PI_0015625, Y)
#define PIZ31 Rotate3(PI_003125, Z)
#define PIZ16 Rotate3(PI_0015625, Z)

#define NIX31 Rotate3(-PI_003125, X)
#define NIX16 Rotate3(-PI_0015625, X)
#define NIY31 Rotate3(-PI_003125, Y)
#define NIY16 Rotate3(-PI_0015625, Y)
#define NIZ31 Rotate3(-PI_003125, Z)
#define NIZ16 Rotate3(-PI_0015625, Z)



#define PINULL Rotate3(0,0)

vec3 matf_verifyPos(vec3 pos){
	return pos;
}

vec3 matf_verifySize(vec3 size){
	return size;
}

mat4 MakeMat4()
{
	return mat4(1.0, 0.0, 0.0, 0.0,
				0.0, 1.0, 0.0, 0.0,
				0.0, 0.0, 1.0, 0.0,
				0.0, 0.0, 0.0, 1.0);
}

mat4 Rotate(float angle, int type)
{

	float sin = sin(angle);
	float cos = cos(angle);

	if (type == 0)
		return mat4(1.0, 0.0,  0.0, 0.0,
					0.0, cos, -sin, 0.0,
					0.0, sin,  cos, 0.0,
					0.0, 0.0,  0.0, 1.0);
	if (type == 1)
		return mat4( cos, 0.0, sin, 0.0,
					 0.0, 1.0, 0.0, 0.0,
					-sin, 0.0, cos, 0.0,
					 0.0, 0.0, 0.0, 1.0);
	if (type == 2)
		return mat4(cos, -sin, 0.0, 0.0,
					sin,  cos, 0.0, 0.0,
					0.0,  0.0, 1.0, 0.0,
					0.0,  0.0, 0.0, 1.0);				

	return mat4(0.0);
}

mat3 Rotate3(float angle, int type)
{


	if (type == 0){
		
	float sin = sin(angle);
	float cos = cos(angle);
		return mat3(1.0, 0.0, 0.0,
					0.0, cos, -sin,
					0.0, sin,  cos);
	}
	if (type == 1){
		
	float sin = sin(angle);
	float cos = cos(angle);
		return mat3( cos, 0.0, sin,
					 0.0, 1.0, 0.0,
					-sin, 0.0, cos);
	}
	if (type == 2){
		
	float sin = sin(angle);
	float cos = cos(angle);
		return mat3(cos, -sin, 0.0,
					sin,  cos, 0.0,
					0.0,  0.0, 1.0);	
	}			

	return mat3(0.0);
}

mat4 RotateByAxis(vec3 axis, float angle)
{
    axis = normalize(axis);
    float s = sin(angle);
    float c = cos(angle);
    float oc = 1.0 - c;
    
    return mat4(oc * axis.x * axis.x + c,           oc * axis.x * axis.y - axis.z * s,  oc * axis.z * axis.x + axis.y * s,  0.0,
                oc * axis.x * axis.y + axis.z * s,  oc * axis.y * axis.y + c,           oc * axis.y * axis.z - axis.x * s,  0.0,
                oc * axis.z * axis.x - axis.y * s,  oc * axis.y * axis.z + axis.x * s,  oc * axis.z * axis.z + c,           0.0,
                0.0,                                0.0,                                0.0,                                1.0);
}