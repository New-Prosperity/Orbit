#ifdef VSH

if(isPartOne) {
    if(uv.y < 16) {
        cube = STASIS_HELMET;
    }
    else if(cube!=ARMOR_CHESTPLATE) {

        if((cube==ARMOR_RIGHT_ARM||cube==ARMOR_LEFT_ARM)
        ){
            if(uv.x<=32){

                if(cube==ARMOR_LEFT_ARM) {
                    cube = STASIS_LEFT_BOOT;
                }
                else if(cube==ARMOR_RIGHT_ARM) {
                    cube = STASIS_RIGHT_BOOT;
                }
            }else{

                if(cube==ARMOR_RIGHT_ARM) {
                    cube = STASIS_RIGHT_ARM;
                }
                else if(cube==ARMOR_LEFT_ARM) {
                    cube = STASIS_LEFT_ARM;
                }

            }
        }

        if(cube==ARMOR_LEFT_FEET) {
            cube = STASIS_RIGHT_BOOT;
        }
        else if(cube==ARMOR_RIGHT_FEET) {
            cube = STASIS_LEFT_BOOT;
        }
    }
    else{
        cube = STASIS_CHESTPLATE;
    }

}


if(isPartTwo) {
    if(cube==ARMOR_RIGHT_ARM) {
        cube = STASIS_LEFT_LEG;
    }
    else if(cube==ARMOR_LEFT_ARM) {
        cube = STASIS_RIGHT_LEG;
    } else 
    if(cube==ARMOR_CHESTPLATE) {
        cube = STASIS_INNER_ARMOR;
    }
    
}
#endif