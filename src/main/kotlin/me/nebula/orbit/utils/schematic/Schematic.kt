package me.nebula.orbit.utils.schematic

import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.BinaryTagTypes
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.IntArrayBinaryTag
import net.kyori.adventure.nbt.IntBinaryTag
import net.kyori.adventure.nbt.ListBinaryTag
import net.kyori.adventure.nbt.StringBinaryTag
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Instance
import net.minestom.server.instance.batch.AbsoluteBlockBatch
import net.minestom.server.instance.block.Block
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

private val blocksByName: Map<String, Block> by lazy {
    Block.values().associateBy { it.name() }
}

data class SchematicBlockEntity(
    val x: Int, val y: Int, val z: Int,
    val id: String,
    val nbt: CompoundBinaryTag,
)

data class SchematicEntity(
    val pos: Vec,
    val nbt: CompoundBinaryTag,
)

class Schematic private constructor(
    val width: Int,
    val height: Int,
    val length: Int,
    private val blocks: Array<Block>,
    val blockEntities: List<SchematicBlockEntity> = emptyList(),
    val entities: List<SchematicEntity> = emptyList(),
) {
    val size: Int get() = width * height * length

    fun getBlock(x: Int, y: Int, z: Int): Block {
        require(x in 0 until width && y in 0 until height && z in 0 until length)
        return blocks[y * width * length + z * width + x]
    }

    fun paste(
        instance: Instance,
        origin: Pos,
        rotation: Rotation = Rotation.NONE,
        mirror: MirrorAxis = MirrorAxis.NONE,
        skipAir: Boolean = true,
        transformer: ((Block) -> Block?)? = null,
    ) {
        val batch = AbsoluteBlockBatch()
        for (y in 0 until height) {
            for (z in 0 until length) {
                for (x in 0 until width) {
                    var block = getBlock(x, y, z)
                    if (skipAir && block.isAir) continue
                    if (block.compare(Block.STRUCTURE_VOID)) continue

                    block = mirrorBlock(rotateBlock(block, rotation), mirror)
                    if (transformer != null) {
                        block = transformer(block) ?: continue
                    }

                    val (mx, mz) = mirrorCoord(x, z, mirror, width, length)
                    val (rx, rz) = rotateCoord(mx, mz, rotation, width, length)

                    batch.setBlock(
                        origin.blockX() + rx,
                        origin.blockY() + y,
                        origin.blockZ() + rz,
                        block,
                    )
                }
            }
        }
        batch.apply(instance) {}

        for (be in blockEntities) {
            val (mx, mz) = mirrorCoord(be.x, be.z, mirror, width, length)
            val (rx, rz) = rotateCoord(mx, mz, rotation, width, length)
            val worldX = origin.blockX() + rx
            val worldY = origin.blockY() + be.y
            val worldZ = origin.blockZ() + rz
            val existing = instance.getBlock(worldX, worldY, worldZ)
            if (!existing.isAir) {
                instance.setBlock(worldX, worldY, worldZ, existing.withNbt(be.nbt))
            }
        }
    }

    fun rotated(rotation: Rotation): Schematic {
        if (rotation == Rotation.NONE) return this
        val (newW, newL) = rotatedSize(width, length, rotation)
        val newBlocks = Array(newW * height * newL) { Block.AIR }

        for (y in 0 until height) {
            for (z in 0 until length) {
                for (x in 0 until width) {
                    val block = rotateBlock(getBlock(x, y, z), rotation)
                    val (rx, rz) = rotateCoord(x, z, rotation, width, length)
                    newBlocks[y * newW * newL + rz * newW + rx] = block
                }
            }
        }

        val newBes = blockEntities.map { be ->
            val (rx, rz) = rotateCoord(be.x, be.z, rotation, width, length)
            be.copy(x = rx, z = rz)
        }

        return Schematic(newW, height, newL, newBlocks, newBes, entities)
    }

    fun mirrored(axis: MirrorAxis): Schematic {
        if (axis == MirrorAxis.NONE) return this
        val newBlocks = Array(width * height * length) { Block.AIR }

        for (y in 0 until height) {
            for (z in 0 until length) {
                for (x in 0 until width) {
                    val block = mirrorBlock(getBlock(x, y, z), axis)
                    val (mx, mz) = mirrorCoord(x, z, axis, width, length)
                    newBlocks[y * width * length + mz * width + mx] = block
                }
            }
        }

        val newBes = blockEntities.map { be ->
            val (mx, mz) = mirrorCoord(be.x, be.z, axis, width, length)
            be.copy(x = mx, z = mz)
        }

        return Schematic(width, height, length, newBlocks, newBes, entities)
    }

    fun toBytes(): ByteArray {
        val palette = LinkedHashMap<String, Int>()
        val blockDataList = mutableListOf<Byte>()

        for (y in 0 until height) {
            for (z in 0 until length) {
                for (x in 0 until width) {
                    val block = getBlock(x, y, z)
                    val stateStr = blockStateToString(block)
                    val id = palette.getOrPut(stateStr) { palette.size }
                    var v = id
                    while (v and 0x7F.inv() != 0) {
                        blockDataList += ((v and 0x7F) or 0x80).toByte()
                        v = v ushr 7
                    }
                    blockDataList += v.toByte()
                }
            }
        }

        val paletteNbt = CompoundBinaryTag.builder()
        for ((state, id) in palette) {
            paletteNbt.putInt(state, id)
        }

        val beListBuilder = ListBinaryTag.builder(BinaryTagTypes.COMPOUND)
        for (be in blockEntities) {
            val beNbt = CompoundBinaryTag.builder()
                .putString("Id", be.id)
                .put("Pos", IntArrayBinaryTag.intArrayBinaryTag(be.x, be.y, be.z))
            for ((key, value) in be.nbt) {
                if (key != "Id" && key != "Pos") beNbt.put(key, value)
            }
            beListBuilder.add(beNbt.build())
        }

        val root = CompoundBinaryTag.builder()
            .putShort("Width", width.toShort())
            .putShort("Height", height.toShort())
            .putShort("Length", length.toShort())
            .putInt("Version", 2)
            .putInt("DataVersion", 3953)
            .put("Palette", paletteNbt.build())
            .putByteArray("BlockData", blockDataList.toByteArray())
            .put("BlockEntities", beListBuilder.build())
            .build()

        val baos = ByteArrayOutputStream()
        BinaryTagIO.writer().write(root, baos, BinaryTagIO.Compression.GZIP)
        return baos.toByteArray()
    }

    fun save(path: Path) {
        Files.write(path, toBytes())
    }

    companion object {

        fun load(path: Path): Schematic {
            require(Files.exists(path)) { "Schematic file not found: $path" }
            return Files.newInputStream(path).use { load(it) }
        }

        fun load(inputStream: InputStream): Schematic {
            val nbt = BinaryTagIO.reader().read(inputStream, BinaryTagIO.Compression.GZIP)
            return parse(nbt)
        }

        fun load(bytes: ByteArray): Schematic =
            load(ByteArrayInputStream(bytes))

        fun copy(instance: Instance, pos1: Pos, pos2: Pos): Schematic {
            val minX = minOf(pos1.blockX(), pos2.blockX())
            val minY = minOf(pos1.blockY(), pos2.blockY())
            val minZ = minOf(pos1.blockZ(), pos2.blockZ())
            val maxX = maxOf(pos1.blockX(), pos2.blockX())
            val maxY = maxOf(pos1.blockY(), pos2.blockY())
            val maxZ = maxOf(pos1.blockZ(), pos2.blockZ())

            val w = maxX - minX + 1
            val h = maxY - minY + 1
            val l = maxZ - minZ + 1
            val blocks = Array(w * h * l) { Block.AIR }
            val bes = mutableListOf<SchematicBlockEntity>()

            for (y in 0 until h) {
                for (z in 0 until l) {
                    for (x in 0 until w) {
                        val block = instance.getBlock(minX + x, minY + y, minZ + z)
                        blocks[y * w * l + z * w + x] = block
                        if (block.hasNbt()) {
                            bes += SchematicBlockEntity(x, y, z, block.name(), block.nbt()!!)
                        }
                    }
                }
            }

            return Schematic(w, h, l, blocks, bes)
        }

        private fun parse(nbt: CompoundBinaryTag): Schematic {
            val width = nbt.getShort("Width").toInt()
            val height = nbt.getShort("Height").toInt()
            val length = nbt.getShort("Length").toInt()
            val paletteNbt = nbt.getCompound("Palette")
            val blockData = nbt.getByteArray("BlockData")

            val palette = mutableMapOf<Int, Block>()
            paletteNbt.keySet().forEach { key ->
                val id = paletteNbt.getInt(key)
                palette[id] = parseBlockState(key)
            }

            val blocks = Array(width * height * length) { Block.AIR }
            var index = 0
            var dataIndex = 0
            while (dataIndex < blockData.size && index < blocks.size) {
                var value = 0
                var bitOffset = 0
                var current: Byte
                do {
                    current = blockData[dataIndex++]
                    value = value or ((current.toInt() and 0x7F) shl bitOffset)
                    bitOffset += 7
                } while (current.toInt() and 0x80 != 0 && dataIndex < blockData.size)
                blocks[index++] = palette[value] ?: Block.AIR
            }

            val blockEntities = mutableListOf<SchematicBlockEntity>()
            val beList = nbt.getList("BlockEntities", BinaryTagTypes.COMPOUND)
            for (i in 0 until beList.size()) {
                val beNbt = beList.getCompound(i)
                val pos = beNbt.getIntArray("Pos")
                if (pos.size >= 3) {
                    blockEntities += SchematicBlockEntity(
                        pos[0], pos[1], pos[2],
                        beNbt.getString("Id"),
                        beNbt,
                    )
                }
            }

            val entities = mutableListOf<SchematicEntity>()
            if (nbt.keySet().contains("Entities")) {
                val entList = nbt.getList("Entities", BinaryTagTypes.COMPOUND)
                for (i in 0 until entList.size()) {
                    val entNbt = entList.getCompound(i)
                    val posList = entNbt.getList("Pos")
                    if (posList.size() >= 3) {
                        entities += SchematicEntity(
                            Vec(
                                posList.getDouble(0),
                                posList.getDouble(1),
                                posList.getDouble(2),
                            ),
                            entNbt,
                        )
                    }
                }
            }

            return Schematic(width, height, length, blocks, blockEntities, entities)
        }

        private fun parseBlockState(state: String): Block {
            val bracketIndex = state.indexOf('[')
            if (bracketIndex == -1) {
                return blocksByName[state] ?: Block.AIR
            }
            val name = state.substring(0, bracketIndex)
            val properties = state.substring(bracketIndex + 1, state.length - 1)
            var block = blocksByName[name] ?: return Block.AIR
            properties.split(',').forEach { prop ->
                val (key, value) = prop.split('=', limit = 2)
                block = block.withProperty(key, value)
            }
            return block
        }

        private fun blockStateToString(block: Block): String {
            val props = block.properties()
            if (props.isEmpty()) return block.name()
            val propStr = props.entries.joinToString(",") { "${it.key}=${it.value}" }
            return "${block.name()}[$propStr]"
        }
    }
}

fun Instance.pasteSchematic(schematic: Schematic, origin: Pos, rotation: Rotation = Rotation.NONE) =
    schematic.paste(this, origin, rotation)
