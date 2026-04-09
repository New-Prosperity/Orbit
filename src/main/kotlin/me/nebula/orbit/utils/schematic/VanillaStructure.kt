package me.nebula.orbit.utils.schematic

import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.BinaryTagTypes
import net.kyori.adventure.nbt.CompoundBinaryTag
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
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.writeBytes

data class StructureBlock(val x: Int, val y: Int, val z: Int, val state: Int, val nbt: CompoundBinaryTag?)

data class StructureEntity(val pos: Vec, val blockPos: Vec, val nbt: CompoundBinaryTag)

class VanillaStructure private constructor(
    val width: Int,
    val height: Int,
    val length: Int,
    val palette: List<Block>,
    val blocks: List<StructureBlock>,
    val entities: List<StructureEntity>,
    val dataVersion: Int,
) {
    private val blockIndex: Array<Block?> by lazy {
        val arr = arrayOfNulls<Block>(width * height * length)
        for (sb in blocks) {
            if (sb.x in 0 until width && sb.y in 0 until height && sb.z in 0 until length) {
                var block = palette.getOrElse(sb.state) { Block.AIR }
                if (sb.nbt != null) block = block.withNbt(sb.nbt)
                arr[sb.y * width * length + sb.z * width + sb.x] = block
            }
        }
        arr
    }

    fun getBlock(x: Int, y: Int, z: Int): Block =
        blockIndex.getOrNull(y * width * length + z * width + x) ?: Block.AIR

    fun paste(
        instance: Instance,
        origin: Pos,
        rotation: Rotation = Rotation.NONE,
        mirror: MirrorAxis = MirrorAxis.NONE,
        skipAir: Boolean = true,
        skipStructureVoid: Boolean = true,
        transformer: ((Block) -> Block?)? = null,
    ) {
        val batch = AbsoluteBlockBatch()

        for (sb in blocks) {
            var block = palette.getOrElse(sb.state) { Block.AIR }
            if (skipAir && block.isAir) continue
            if (skipStructureVoid && block.compare(Block.STRUCTURE_VOID)) continue

            if (sb.nbt != null) block = block.withNbt(sb.nbt)
            block = mirrorBlock(rotateBlock(block, rotation), mirror)
            if (transformer != null) {
                block = transformer(block) ?: continue
            }

            val (mx, mz) = mirrorCoord(sb.x, sb.z, mirror, width, length)
            val (rx, rz) = rotateCoord(mx, mz, rotation, width, length)

            batch.setBlock(
                origin.blockX() + rx,
                origin.blockY() + sb.y,
                origin.blockZ() + rz,
                block,
            )
        }

        batch.apply(instance) {}
    }

    fun toBytes(): ByteArray {
        val paletteList = ListBinaryTag.builder(BinaryTagTypes.COMPOUND)
        for (block in palette) {
            val entry = CompoundBinaryTag.builder()
                .putString("Name", block.name())
            val props = block.properties()
            if (props.isNotEmpty()) {
                val propsNbt = CompoundBinaryTag.builder()
                for ((k, v) in props) propsNbt.putString(k, v)
                entry.put("Properties", propsNbt.build())
            }
            paletteList.add(entry.build())
        }

        val blocksList = ListBinaryTag.builder(BinaryTagTypes.COMPOUND)
        for (sb in blocks) {
            val entry = CompoundBinaryTag.builder()
                .putInt("state", sb.state)
                .put("pos", listOfInts(sb.x, sb.y, sb.z))
            if (sb.nbt != null) entry.put("nbt", sb.nbt)
            blocksList.add(entry.build())
        }

        val entitiesList = ListBinaryTag.builder(BinaryTagTypes.COMPOUND)
        for (se in entities) {
            val entry = CompoundBinaryTag.builder()
                .put("pos", listOfDoubles(se.pos.x(), se.pos.y(), se.pos.z()))
                .put("blockPos", listOfInts(se.blockPos.x().toInt(), se.blockPos.y().toInt(), se.blockPos.z().toInt()))
                .put("nbt", se.nbt)
            entitiesList.add(entry.build())
        }

        val root = CompoundBinaryTag.builder()
            .putInt("DataVersion", dataVersion)
            .put("size", listOfInts(width, height, length))
            .put("palette", paletteList.build())
            .put("blocks", blocksList.build())
            .put("entities", entitiesList.build())
            .build()

        val baos = ByteArrayOutputStream()
        BinaryTagIO.writer().write(root, baos, BinaryTagIO.Compression.GZIP)
        return baos.toByteArray()
    }

    fun save(path: Path) {
        path.writeBytes(toBytes())
    }

    companion object {

        fun load(path: Path): VanillaStructure {
            require(path.exists()) { "Structure file not found: $path" }
            return path.inputStream().use { load(it) }
        }

        fun load(inputStream: InputStream): VanillaStructure {
            val nbt = BinaryTagIO.reader().read(inputStream, BinaryTagIO.Compression.GZIP)
            return parse(nbt)
        }

        fun load(bytes: ByteArray): VanillaStructure =
            load(ByteArrayInputStream(bytes))

        fun copy(instance: Instance, pos1: Pos, pos2: Pos, dataVersion: Int = 3953): VanillaStructure {
            val minX = minOf(pos1.blockX(), pos2.blockX())
            val minY = minOf(pos1.blockY(), pos2.blockY())
            val minZ = minOf(pos1.blockZ(), pos2.blockZ())
            val maxX = maxOf(pos1.blockX(), pos2.blockX())
            val maxY = maxOf(pos1.blockY(), pos2.blockY())
            val maxZ = maxOf(pos1.blockZ(), pos2.blockZ())

            val w = maxX - minX + 1
            val h = maxY - minY + 1
            val l = maxZ - minZ + 1

            val paletteMap = LinkedHashMap<String, Int>()
            val paletteList = mutableListOf<Block>()
            val blocks = mutableListOf<StructureBlock>()

            for (y in 0 until h) {
                for (z in 0 until l) {
                    for (x in 0 until w) {
                        val block = instance.getBlock(minX + x, minY + y, minZ + z)
                        if (block.isAir) continue
                        val stateStr = blockStateKey(block)
                        val stateIdx = paletteMap.getOrPut(stateStr) {
                            paletteList += block.withoutNbt()
                            paletteList.size - 1
                        }
                        blocks += StructureBlock(x, y, z, stateIdx, block.nbt())
                    }
                }
            }

            if (paletteList.isEmpty()) {
                paletteList += Block.AIR
                paletteMap["minecraft:air"] = 0
            }

            return VanillaStructure(w, h, l, paletteList, blocks, emptyList(), dataVersion)
        }

        private fun parse(nbt: CompoundBinaryTag): VanillaStructure {
            val sizeList = nbt.getList("size", BinaryTagTypes.INT)
            val width = sizeList.getInt(0)
            val height = sizeList.getInt(1)
            val length = sizeList.getInt(2)
            val dataVersion = nbt.getInt("DataVersion")

            val paletteNbt = nbt.getList("palette", BinaryTagTypes.COMPOUND)
            val palette = (0 until paletteNbt.size()).map { i ->
                val entry = paletteNbt.getCompound(i)
                val name = entry.getString("Name")
                var block = Block.fromKey(name) ?: Block.AIR
                if (entry.keySet().contains("Properties")) {
                    val props = entry.getCompound("Properties")
                    for (key in props.keySet()) {
                        block = block.withProperty(key, props.getString(key))
                    }
                }
                block
            }

            val blocksNbt = nbt.getList("blocks", BinaryTagTypes.COMPOUND)
            val blocks = (0 until blocksNbt.size()).map { i ->
                val entry = blocksNbt.getCompound(i)
                val pos = entry.getList("pos", BinaryTagTypes.INT)
                val state = entry.getInt("state")
                val blockNbt = if (entry.keySet().contains("nbt")) entry.getCompound("nbt") else null
                StructureBlock(pos.getInt(0), pos.getInt(1), pos.getInt(2), state, blockNbt)
            }

            val entities = mutableListOf<StructureEntity>()
            if (nbt.keySet().contains("entities")) {
                val entNbt = nbt.getList("entities", BinaryTagTypes.COMPOUND)
                for (i in 0 until entNbt.size()) {
                    val entry = entNbt.getCompound(i)
                    val pos = entry.getList("pos", BinaryTagTypes.DOUBLE)
                    val blockPos = entry.getList("blockPos", BinaryTagTypes.INT)
                    val entityNbt = if (entry.keySet().contains("nbt")) entry.getCompound("nbt") else CompoundBinaryTag.empty()
                    entities += StructureEntity(
                        Vec(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2)),
                        Vec(blockPos.getInt(0).toDouble(), blockPos.getInt(1).toDouble(), blockPos.getInt(2).toDouble()),
                        entityNbt,
                    )
                }
            }

            return VanillaStructure(width, height, length, palette, blocks, entities, dataVersion)
        }

        private fun blockStateKey(block: Block): String {
            val props = block.properties()
            if (props.isEmpty()) return block.name()
            return "${block.name()}[${props.entries.joinToString(",") { "${it.key}=${it.value}" }}]"
        }

        private fun Block.withoutNbt(): Block = if (hasNbt()) withNbt(null) else this

        private fun listOfInts(vararg values: Int): ListBinaryTag {
            val builder = ListBinaryTag.builder(BinaryTagTypes.INT)
            for (v in values) builder.add(IntBinaryTag.intBinaryTag(v))
            return builder.build()
        }

        private fun listOfDoubles(vararg values: Double): ListBinaryTag {
            val builder = ListBinaryTag.builder(BinaryTagTypes.DOUBLE)
            for (v in values) builder.add(net.kyori.adventure.nbt.DoubleBinaryTag.doubleBinaryTag(v))
            return builder.build()
        }
    }
}
