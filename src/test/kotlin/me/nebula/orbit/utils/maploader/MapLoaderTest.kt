package me.nebula.orbit.utils.maploader

import me.nebula.orbit.utils.nebulaworld.NebulaWorld
import me.nebula.orbit.utils.nebulaworld.NebulaWorldWriter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapLoaderTest {

    @TempDir
    lateinit var tmp: Path

    @TempDir
    lateinit var workTmp: Path

    @BeforeEach
    fun setup() {
        MapLoader.overrideMountDirForTest(tmp)
        MapLoader.overrideWorldsDirForTest(workTmp)
    }

    @AfterEach
    fun teardown() {
        MapLoader.resetMountDirForTest()
        MapLoader.resetWorldsDirForTest()
    }

    @Test
    fun `resolve returns nebula file when present in worlds dir`() {
        val file = workTmp.resolve("battleroyale.nebula")
        Files.writeString(file, "stub")

        val resolved = MapLoader.resolve("battleroyale")
        assertEquals(file.toAbsolutePath(), resolved.toAbsolutePath())
    }

    @Test
    fun `resolve fails when no nebula file exists and no anvil source is present`() {
        assertFailsWith<IllegalStateException> { MapLoader.resolve("missing") }
    }

    @Test
    fun `resolve fails when directory has no region subtree (not a real anvil world)`() {
        val dir = tmp.resolve("not-anvil")
        Files.createDirectories(dir)
        assertFailsWith<IllegalStateException> { MapLoader.resolve("not-anvil") }
        assertTrue(Files.isDirectory(dir), "Non-anvil directory should not be touched")
    }

    @Test
    fun `ingestAnvilIfPresent returns null when directory does not exist`() {
        assertNull(MapLoader.ingestAnvilIfPresent("ghost"))
    }

    @Test
    fun `ingestAnvilIfPresent returns null when directory is not an anvil world`() {
        val dir = tmp.resolve("plain")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("stuff.txt"), "no region here")
        assertNull(MapLoader.ingestAnvilIfPresent("plain"))
        assertTrue(Files.isDirectory(dir))
    }

    @Test
    fun `ingestAnvilIfPresent refuses when conflicting target already exists in worlds dir`() {
        val dir = tmp.resolve("clash")
        Files.createDirectories(dir.resolve("dimensions/minecraft/overworld/region"))
        Files.createDirectories(workTmp)
        Files.writeString(workTmp.resolve("clash.nebula"), "conflict")

        assertFailsWith<IllegalArgumentException> { MapLoader.ingestAnvilIfPresent("clash") }
        assertTrue(Files.isDirectory(dir), "Anvil dir preserved on conflict")
    }

    @Test
    fun `isNebulaFile recognises the nebula suffix on regular files only`() {
        val file = tmp.resolve("world.nebula")
        Files.writeString(file, "x")
        assertTrue(MapLoader.isNebulaFile(file))

        val dir = tmp.resolve("world-dir.nebula")
        Files.createDirectories(dir)
        assertFalse(MapLoader.isNebulaFile(dir))

        val nonSuffix = tmp.resolve("world.txt")
        Files.writeString(nonSuffix, "x")
        assertFalse(MapLoader.isNebulaFile(nonSuffix))
    }

    @Test
    fun `scanAndIngest returns zero when mounts directory empty`() {
        assertEquals(0, MapLoader.scanAndIngest())
    }

    @Test
    fun `scanAndIngest skips non-anvil directories`() {
        Files.createDirectories(tmp.resolve("random"))
        Files.writeString(tmp.resolve("world.nebula"), "x")
        assertEquals(0, MapLoader.scanAndIngest())
    }

    @Test
    fun `ingestAnvilIfPresent skips when valid nebula already exists in worlds dir and preserves anvil source`() {
        val dir = tmp.resolve("already-done")
        Files.createDirectories(dir.resolve("dimensions/minecraft/overworld/region"))
        Files.createDirectories(workTmp)
        val bytes = NebulaWorldWriter.write(NebulaWorld(
            dataVersion = 1, minSection = -4, maxSection = 19, chunks = emptyMap(),
        ))
        Files.write(workTmp.resolve("already-done.nebula"), bytes)

        assertNull(MapLoader.ingestAnvilIfPresent("already-done"))
        assertTrue(Files.isDirectory(dir), "Anvil source directory in maps/ should be preserved")
        assertTrue(Files.isRegularFile(workTmp.resolve("already-done.nebula")))
    }

    @Test
    fun `scanAndIngest counts only newly converted worlds`() {
        val dir = tmp.resolve("ready")
        Files.createDirectories(dir.resolve("dimensions/minecraft/overworld/region"))
        Files.createDirectories(workTmp)
        val bytes = NebulaWorldWriter.write(NebulaWorld(
            dataVersion = 1, minSection = -4, maxSection = 19, chunks = emptyMap(),
        ))
        Files.write(workTmp.resolve("ready.nebula"), bytes)

        assertEquals(0, MapLoader.scanAndIngest())
        assertTrue(Files.isDirectory(dir), "Anvil source in maps/ preserved across scan")
    }
}
