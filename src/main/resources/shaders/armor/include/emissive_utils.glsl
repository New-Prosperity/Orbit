#version 150

bool check_alpha(float textureAlpha, float targetAlpha) {
	
	float targetLess = targetAlpha - 0.01;
	float targetMore = targetAlpha + 0.01;
	return (textureAlpha > targetLess && textureAlpha < targetMore);
	
}

bool compare_floats(float a, float b) {
	
	float targetLess = a - 0.01;
	float targetMore = a + 0.01;
	return (b > targetLess && b < targetMore);
	
}

vec4 get_block_face_lighting(vec3 normal, float dimension) { 
    
    vec4 faceLighting = vec4(1.0, 1.0, 1.0, 1.0);
    vec3 absNormal = abs(normal);
    float top = 229.0 / 255.0;
    float bottom = 127.0 / 255.0;
    float east = 153.0 / 255.0;
    float north = 204.0 / 255.0;
    
    if (normal.y > normal.z && normal.y > normal.x && compare_floats(dimension, -1.0)) faceLighting = vec4(top, top, top, 1.0);
    
    if (normal.y < normal.z && normal.y < normal.x && !compare_floats(dimension, -1.0)) faceLighting = vec4(bottom, bottom, bottom, 1.0);
    else if (normal.y < normal.z && normal.y < normal.x && compare_floats(dimension, -1.0)) faceLighting = vec4(top, top, top, 1.0);

    if (absNormal.x > absNormal.z && absNormal.x > absNormal.y) faceLighting = vec4(east, east, east, 1.0);

    if (absNormal.z > absNormal.x && absNormal.z > absNormal.y) faceLighting = vec4(north, north, north, 1.0);

    return faceLighting;
}

vec4 apply_partial_emissivity(vec4 inputColor, vec4 originalLightColor, vec3 minimumLightColor) {
	
	vec4 newLightColor = originalLightColor;
	newLightColor.r = max(originalLightColor.r, minimumLightColor.r);
	newLightColor.g = max(originalLightColor.g, minimumLightColor.g);
	newLightColor.b = max(originalLightColor.b, minimumLightColor.b);
	return inputColor * newLightColor;
	
}

bool face_lighting_check(int inputAlpha) {

    if (inputAlpha == 252) return false;
    if (inputAlpha == 250) return false;

    return true;
}
float remap_alpha(int inputAlpha) {
    
    if (inputAlpha == 252) return 255.0;
    if (inputAlpha == 251) return 190.0;
    if (inputAlpha == 250) return 255.0;
    
    return inputAlpha;
}

vec4 make_emissive(vec4 inputColor, vec4 lightColor, vec4 faceLightColor, int inputAlpha) {

    if(face_lighting_check(inputAlpha)) inputColor *= faceLightColor;
    inputColor.a = remap_alpha(inputAlpha) / 255.0;

    if (inputAlpha == 252) return inputColor;
    if (inputAlpha == 254) return inputColor;
    if (inputAlpha == 128) return inputColor;
    if (inputAlpha == 251) return apply_partial_emissivity(inputColor, lightColor, vec3(0.411, 0.345, 0.388));
    
    return inputColor * lightColor;
}

vec4 make_emissive(vec4 inputColor, vec4 lightColor, vec4 maxLightColor, float vertexDistance, float inputAlpha) {
	
	if (vertexDistance > 800) return inputColor;
	
	if (check_alpha(inputAlpha, 252.0)) return inputColor;
	else if (check_alpha(inputAlpha, 251.0)) return apply_partial_emissivity(inputColor, lightColor, vec3(0.411, 0.345, 0.388));
	else if (check_alpha(inputAlpha, 250.0)) return inputColor;
	else if (check_alpha(inputAlpha, 254.0)) return inputColor; 
	else if (check_alpha(inputAlpha, 128.0)) return inputColor;
	
	else return inputColor * lightColor;
	
}

float get_dimension(vec4 minLightColor) {
	
	if (minLightColor.r == minLightColor.g && minLightColor.g == minLightColor.b) return 0.0;
	else if (minLightColor.r > minLightColor.g) return -1.0;
	else return 1.0;
	
}

vec4 get_face_lighting(vec3 normal, float dimension) { 
	
	vec4 faceLighting = vec4(1.0, 1.0, 1.0, 1.0);
	vec3 absNormal = abs(normal);
	float top = 229.0 / 255.0;
	float bottom = 127.0 / 255.0;
	float east = 153.0 / 255.0;
	float north = 204.0 / 255.0;
	
	if (normal.y > normal.z && normal.y > normal.x && check_alpha(dimension, -1.0)) faceLighting = vec4(top, top, top, 1.0);
	
	if (normal.y < normal.z && normal.y < normal.x && !check_alpha(dimension, -1.0)) faceLighting = vec4(bottom, bottom, bottom, 1.0);
	else if (normal.y < normal.z && normal.y < normal.x && check_alpha(dimension, -1.0)) faceLighting = vec4(top, top, top, 1.0);

	if (absNormal.x > absNormal.z && absNormal.x > absNormal.y) faceLighting = vec4(east, east, east, 1.0);

	if (absNormal.z > absNormal.x && absNormal.z > absNormal.y) faceLighting = vec4(north, north, north, 1.0);

	return faceLighting;
}



vec4 face_lighting_check(vec3 normal, float inputAlpha, float dimension) {

	if (check_alpha(inputAlpha, 250.0)) return get_face_lighting(normal, dimension);
	else return vec4(1.0, 1.0, 1.0, 1.0);
}


float remap_alpha(float inputAlpha) {
	
	if (check_alpha(inputAlpha, 252.0)) return 255.0;
	else if (check_alpha(inputAlpha, 251.0)) return 190.0;
	else if (check_alpha(inputAlpha, 250.0)) return 255.0;
	
	else return inputAlpha;
	
}

