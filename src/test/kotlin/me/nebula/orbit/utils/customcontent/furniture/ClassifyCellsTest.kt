package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.orbit.utils.customcontent.block.BlockHitbox
import me.nebula.orbit.utils.modelengine.generator.BbElement
import me.nebula.orbit.utils.modelengine.generator.BbGroup
import me.nebula.orbit.utils.modelengine.generator.BbGroupChild
import me.nebula.orbit.utils.modelengine.generator.BbMeta
import me.nebula.orbit.utils.modelengine.generator.BbResolution
import me.nebula.orbit.utils.modelengine.generator.BlockbenchModel
import net.minestom.server.coordinate.Vec
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ClassifyCellsTest {

    private fun element(uuid: String, from: Vec, to: Vec) = BbElement(
        uuid = uuid, name = "cube_$uuid", from = from, to = to,
        origin = Vec.ZERO, rotation = Vec.ZERO, inflate = 0f, faces = emptyMap(), visibility = true,
    )

    private fun modelFor(elements: List<BbElement>, groups: List<BbGroup>) = BlockbenchModel(
        name = "test",
        meta = BbMeta("4.10", "generic", false),
        resolution = BbResolution(16, 16),
        elements = elements,
        groups = groups,
        textures = emptyList(),
        animations = emptyList(),
    )

    @Test
    fun `legacy collider bone is treated as solid`() {
        assertEquals(CellCollisionMode.Solid, BlockbenchColliderParser.classifyBoneName("collider"))
        assertEquals(CellCollisionMode.Solid, BlockbenchColliderParser.classifyBoneName("collider_seat"))
    }

    @Test
    fun `collider_solid explicit classifies as solid`() {
        assertEquals(CellCollisionMode.Solid, BlockbenchColliderParser.classifyBoneName("collider_solid"))
        assertEquals(CellCollisionMode.Solid, BlockbenchColliderParser.classifyBoneName("collider_solid_back"))
    }

    @Test
    fun `collider_soft classifies as soft`() {
        assertEquals(CellCollisionMode.Soft, BlockbenchColliderParser.classifyBoneName("collider_soft"))
        assertEquals(CellCollisionMode.Soft, BlockbenchColliderParser.classifyBoneName("collider_soft_seat"))
    }

    @Test
    fun `solid bone produces Barrier decision`() {
        val e = element("e1", Vec(0.0, 0.0, 0.0), Vec(16.0, 16.0, 16.0))
        val group = BbGroup("g1", "collider_solid", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("e1")), true)
        val model = modelFor(listOf(e), listOf(group))
        val decisions = BlockbenchColliderParser.classifyCells(model, "collider")
        assertEquals(CellDecision.Barrier, decisions[FootprintCell(0, 0, 0)])
    }

    @Test
    fun `soft bone with bottom-half AABB produces Shaped Slab`() {
        val e = element("e1", Vec(0.0, 0.0, 0.0), Vec(16.0, 8.0, 16.0))
        val group = BbGroup("g1", "collider_soft", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("e1")), true)
        val model = modelFor(listOf(e), listOf(group))
        val decisions = BlockbenchColliderParser.classifyCells(model, "collider")
        val decision = decisions[FootprintCell(0, 0, 0)]
        assertIs<CellDecision.Shaped>(decision)
        assertEquals(BlockHitbox.Slab, decision.hitbox)
    }

    @Test
    fun `solid wins over soft on overlapping cells`() {
        val softEl = element("soft", Vec(0.0, 0.0, 0.0), Vec(16.0, 8.0, 16.0))
        val solidEl = element("solid", Vec(0.0, 0.0, 0.0), Vec(16.0, 16.0, 16.0))
        val softGroup = BbGroup("s", "collider_soft", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("soft")), true)
        val solidGroup = BbGroup("h", "collider_solid", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("solid")), true)
        val model = modelFor(listOf(softEl, solidEl), listOf(softGroup, solidGroup))
        val decisions = BlockbenchColliderParser.classifyCells(model, "collider")
        assertEquals(CellDecision.Barrier, decisions[FootprintCell(0, 0, 0)])
    }

    @Test
    fun `mixed mode produces per-cell distinct decisions`() {
        val seatEl = element("seat", Vec(0.0, 0.0, 0.0), Vec(16.0, 8.0, 16.0))
        val backEl = element("back", Vec(0.0, 0.0, 16.0), Vec(16.0, 16.0, 32.0))
        val softGroup = BbGroup("s", "collider_soft_seat", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("seat")), true)
        val solidGroup = BbGroup("h", "collider_solid_back", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("back")), true)
        val model = modelFor(listOf(seatEl, backEl), listOf(softGroup, solidGroup))
        val decisions = BlockbenchColliderParser.classifyCells(model, "collider")
        assertIs<CellDecision.Shaped>(decisions[FootprintCell(0, 0, 0)])
        assertEquals(BlockHitbox.Slab, (decisions[FootprintCell(0, 0, 0)] as CellDecision.Shaped).hitbox)
        assertEquals(CellDecision.Barrier, decisions[FootprintCell(0, 0, 1)])
    }

    @Test
    fun `two soft cubes on same cell union AABBs before classification`() {
        val a = element("a", Vec(0.0, 0.0, 0.0), Vec(8.0, 8.0, 16.0))
        val b = element("b", Vec(8.0, 0.0, 0.0), Vec(16.0, 8.0, 16.0))
        val group = BbGroup("g", "collider_soft", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("a"), BbGroupChild.ElementRef("b")), true)
        val model = modelFor(listOf(a, b), listOf(group))
        val decisions = BlockbenchColliderParser.classifyCells(model, "collider")
        val decision = decisions[FootprintCell(0, 0, 0)]
        assertIs<CellDecision.Shaped>(decision)
        assertEquals(BlockHitbox.Slab, decision.hitbox)
    }

    @Test
    fun `no collider bones returns empty map`() {
        val e = element("e1", Vec(0.0, 0.0, 0.0), Vec(16.0, 16.0, 16.0))
        val group = BbGroup("visual", "visual", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("e1")), true)
        val model = modelFor(listOf(e), listOf(group))
        val decisions = BlockbenchColliderParser.classifyCells(model, "collider")
        assertEquals(emptyMap(), decisions)
    }

    @Test
    fun `anchor cell always present even if collider misses it`() {
        val e = element("e1", Vec(16.0, 0.0, 0.0), Vec(32.0, 16.0, 16.0))
        val group = BbGroup("g", "collider_solid", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("e1")), true)
        val model = modelFor(listOf(e), listOf(group))
        val decisions = BlockbenchColliderParser.classifyCells(model, "collider")
        assertEquals(CellDecision.Barrier, decisions[FootprintCell(0, 0, 0)])
        assertEquals(CellDecision.Barrier, decisions[FootprintCell(1, 0, 0)])
    }

    @Test
    fun `full-cell soft AABB downgrades to Barrier via inferrer Full match`() {
        val e = element("e1", Vec(0.0, 0.0, 0.0), Vec(16.0, 16.0, 16.0))
        val group = BbGroup("g", "collider_soft", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("e1")), true)
        val model = modelFor(listOf(e), listOf(group))
        val decisions = BlockbenchColliderParser.classifyCells(model, "collider")
        assertEquals(CellDecision.Barrier, decisions[FootprintCell(0, 0, 0)])
    }
}
