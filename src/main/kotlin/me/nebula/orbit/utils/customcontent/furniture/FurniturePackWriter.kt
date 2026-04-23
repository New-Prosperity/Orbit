package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.orbit.utils.modelengine.generator.GeneratedBoneModel
import me.nebula.orbit.utils.modelengine.generator.ModelGenerator
import me.nebula.orbit.utils.modelengine.generator.BlockbenchModel

object FurniturePackWriter {

    data class FurnitureModelArtifact(
        val model: GeneratedBoneModel,
        val atlasBytes: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FurnitureModelArtifact) return false
            return model == other.model && atlasBytes.contentEquals(other.atlasBytes)
        }

        override fun hashCode(): Int {
            var result = model.hashCode()
            result = 31 * result + atlasBytes.contentHashCode()
            return result
        }
    }

    fun buildVisualModel(model: BlockbenchModel, colliderPrefix: String = FurnitureJsonLoader.DEFAULT_COLLIDER_PREFIX): FurnitureModelArtifact {
        val excluded = BlockbenchColliderParser.elementUuidsUnderColliderBones(model, colliderPrefix)
        val (flat, atlasBytes) = ModelGenerator.buildFlatModel(model, elementFilter = { it.uuid !in excluded })
        return FurnitureModelArtifact(flat, atlasBytes)
    }
}
