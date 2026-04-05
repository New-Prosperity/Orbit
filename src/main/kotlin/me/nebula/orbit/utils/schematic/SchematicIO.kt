package me.nebula.orbit.utils.schematic

import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import java.nio.file.Files
import java.nio.file.Path

enum class SchematicFormat {
    SPONGE,
    VANILLA;

    companion object {
        fun fromExtension(path: Path): SchematicFormat = when {
            path.toString().endsWith(".schem") -> SPONGE
            path.toString().endsWith(".nbt") -> VANILLA
            else -> SPONGE
        }
    }
}

object SchematicIO {

    fun readSponge(path: Path): Schematic = Schematic.load(path)

    fun readSponge(bytes: ByteArray): Schematic = Schematic.load(bytes)

    fun readVanilla(path: Path): VanillaStructure = VanillaStructure.load(path)

    fun readVanilla(bytes: ByteArray): VanillaStructure = VanillaStructure.load(bytes)

    fun writeSponge(schematic: Schematic, path: Path) = schematic.save(path)

    fun writeSponge(schematic: Schematic): ByteArray = schematic.toBytes()

    fun writeVanilla(structure: VanillaStructure, path: Path) = structure.save(path)

    fun writeVanilla(structure: VanillaStructure): ByteArray = structure.toBytes()

    fun copySponge(instance: Instance, pos1: Pos, pos2: Pos): Schematic =
        Schematic.copy(instance, pos1, pos2)

    fun copyVanilla(instance: Instance, pos1: Pos, pos2: Pos, dataVersion: Int = 3953): VanillaStructure =
        VanillaStructure.copy(instance, pos1, pos2, dataVersion)
}
