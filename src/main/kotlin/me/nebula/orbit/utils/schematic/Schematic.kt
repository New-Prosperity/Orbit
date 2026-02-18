package me.nebula.orbit.utils.schematic

import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.instance.batch.AbsoluteBlockBatch
import net.minestom.server.instance.block.Block
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag

private val blocksByName: Map<String, Block> by lazy {
    Block.values().associateBy { it.name() }
}

class Schematic private constructor(
    val width: Int,
    val height: Int,
    val length: Int,
    private val blocks: Array<Block>,
) {
    val size: Int get() = width * height * length

    fun getBlock(x: Int, y: Int, z: Int): Block {
        require(x in 0 until width && y in 0 until height && z in 0 until length)
        return blocks[y * width * length + z * width + x]
    }

    fun paste(instance: Instance, origin: Pos, applyImmediately: Boolean = true) {
        val batch = AbsoluteBlockBatch()
        for (y in 0 until height) {
            for (z in 0 until length) {
                for (x in 0 until width) {
                    val block = getBlock(x, y, z)
                    if (!block.isAir) {
                        batch.setBlock(
                            origin.blockX() + x,
                            origin.blockY() + y,
                            origin.blockZ() + z,
                            block,
                        )
                    }
                }
            }
        }
        if (applyImmediately) {
            batch.apply(instance) {}
        }
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

            return Schematic(width, height, length, blocks)
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
    }
}

fun Instance.pasteSchematic(schematic: Schematic, origin: Pos) = schematic.paste(this, origin)
