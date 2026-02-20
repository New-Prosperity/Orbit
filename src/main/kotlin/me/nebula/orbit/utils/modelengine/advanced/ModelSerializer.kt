package me.nebula.orbit.utils.modelengine.advanced

import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.math.Quat
import me.nebula.orbit.utils.modelengine.model.ActiveModel
import me.nebula.orbit.utils.modelengine.model.ModelOwner
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import me.nebula.orbit.utils.modelengine.model.asModelOwner
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object ModelSerializer {

    private const val VERSION: Byte = 1

    fun serialize(modeledEntity: ModeledEntity): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeByte(VERSION.toInt())
            out.writeFloat(modeledEntity.headYaw)
            out.writeFloat(modeledEntity.headPitch)

            val models = modeledEntity.models
            out.writeInt(models.size)
            models.forEach { (id, model) ->
                out.writeUTF(id)
                out.writeUTF(model.blueprint.name)
                out.writeFloat(model.modelScale)
                serializeModel(out, model)
            }
        }
        return baos.toByteArray()
    }

    fun deserialize(owner: ModelOwner, data: ByteArray): ModeledEntity {
        return DataInputStream(ByteArrayInputStream(data)).use { dis ->
            val version = dis.readByte()
            require(version == VERSION) { "Unsupported serialization version: $version" }

            val modeled = ModelEngine.createModeledEntity(owner)
            modeled.headYaw = dis.readFloat()
            modeled.headPitch = dis.readFloat()

            val modelCount = dis.readInt()
            repeat(modelCount) {
                val id = dis.readUTF()
                val blueprintName = dis.readUTF()
                val blueprint = ModelEngine.blueprintOrNull(blueprintName)
                    ?: run { dis.readFloat(); return@repeat }
                val model = modeled.addModel(id, blueprint)
                model.modelScale = dis.readFloat()
                deserializeModel(dis, model)
            }

            modeled
        }
    }

    fun deserialize(entity: Entity, data: ByteArray): ModeledEntity =
        deserialize(entity.asModelOwner(), data)

    private fun serializeModel(out: DataOutputStream, model: ActiveModel) {
        out.writeInt(model.bones.size)
        model.bones.forEach { (name, bone) ->
            out.writeUTF(name)
            out.writeBoolean(bone.visible)
            writeVec(out, bone.localPosition)
            writeQuat(out, bone.localRotation)
            writeVec(out, bone.localScale)
            writeVec(out, bone.animatedPosition)
            writeQuat(out, bone.animatedRotation)
            writeVec(out, bone.animatedScale)
        }
    }

    private fun deserializeModel(dis: DataInputStream, model: ActiveModel) {
        val boneCount = dis.readInt()
        repeat(boneCount) {
            val name = dis.readUTF()
            val bone = model.bones[name] ?: run {
                dis.readBoolean()
                readVec(dis); readQuat(dis); readVec(dis)
                readVec(dis); readQuat(dis); readVec(dis)
                return@repeat
            }
            bone.visible = dis.readBoolean()
            bone.localPosition = readVec(dis)
            bone.localRotation = readQuat(dis)
            bone.localScale = readVec(dis)
            bone.animatedPosition = readVec(dis)
            bone.animatedRotation = readQuat(dis)
            bone.animatedScale = readVec(dis)
        }
    }

    private fun writeVec(out: DataOutputStream, v: Vec) {
        out.writeDouble(v.x())
        out.writeDouble(v.y())
        out.writeDouble(v.z())
    }

    private fun readVec(dis: DataInputStream): Vec = Vec(dis.readDouble(), dis.readDouble(), dis.readDouble())

    private fun writeQuat(out: DataOutputStream, q: Quat) {
        out.writeFloat(q[0])
        out.writeFloat(q[1])
        out.writeFloat(q[2])
        out.writeFloat(q[3])
    }

    private fun readQuat(dis: DataInputStream): Quat = Quat(
        dis.readFloat(), dis.readFloat(), dis.readFloat(), dis.readFloat(),
    )
}
