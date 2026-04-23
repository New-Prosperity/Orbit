package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.orbit.utils.modelengine.generator.BbElement
import me.nebula.orbit.utils.modelengine.generator.BbGroup
import me.nebula.orbit.utils.modelengine.generator.BbGroupChild
import me.nebula.orbit.utils.modelengine.generator.BbMeta
import me.nebula.orbit.utils.modelengine.generator.BbResolution
import me.nebula.orbit.utils.modelengine.generator.BlockbenchModel
import net.minestom.server.coordinate.Vec
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockbenchColliderParserTest {

    private fun element(uuid: String, from: Vec, to: Vec) = BbElement(
        uuid = uuid,
        name = "cube_$uuid",
        from = from,
        to = to,
        origin = Vec.ZERO,
        rotation = Vec.ZERO,
        inflate = 0f,
        faces = emptyMap(),
        visibility = true,
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
    fun `single collider cube at anchor resolves to one cell`() {
        val e = element("e1", Vec(0.0, 0.0, 0.0), Vec(16.0, 16.0, 16.0))
        val group = BbGroup("g1", "collider", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("e1")), visibility = true)
        val model = modelFor(listOf(e), listOf(group))
        val cells = BlockbenchColliderParser.readFootprintCells(model, "collider")
        assertEquals(listOf(FootprintCell(0, 0, 0)), cells)
    }

    @Test
    fun `two adjacent cubes form a 2x1 footprint`() {
        val e1 = element("e1", Vec(0.0, 0.0, 0.0), Vec(16.0, 16.0, 16.0))
        val e2 = element("e2", Vec(16.0, 0.0, 0.0), Vec(32.0, 16.0, 16.0))
        val group = BbGroup("g1", "collider", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("e1"), BbGroupChild.ElementRef("e2")), true)
        val model = modelFor(listOf(e1, e2), listOf(group))
        val cells = BlockbenchColliderParser.readFootprintCells(model, "collider").toSet()
        assertEquals(setOf(FootprintCell(0, 0, 0), FootprintCell(1, 0, 0)), cells)
    }

    @Test
    fun `non collider bones are ignored`() {
        val e1 = element("e1", Vec(0.0, 0.0, 0.0), Vec(16.0, 16.0, 16.0))
        val e2 = element("e2", Vec(16.0, 0.0, 0.0), Vec(32.0, 16.0, 16.0))
        val colliderGroup = BbGroup("g1", "collider", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("e1")), true)
        val visualGroup = BbGroup("g2", "visual", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("e2")), true)
        val model = modelFor(listOf(e1, e2), listOf(colliderGroup, visualGroup))
        val cells = BlockbenchColliderParser.readFootprintCells(model, "collider").toSet()
        assertEquals(setOf(FootprintCell(0, 0, 0)), cells)
    }

    @Test
    fun `nested collider bones are recognized`() {
        val e1 = element("e1", Vec(0.0, 0.0, 0.0), Vec(16.0, 16.0, 16.0))
        val innerCollider = BbGroup("inner", "collider_seat", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("e1")), true)
        val outer = BbGroup("outer", "chair", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.SubGroup(innerCollider)), true)
        val model = modelFor(listOf(e1), listOf(outer))
        val cells = BlockbenchColliderParser.readFootprintCells(model, "collider").toSet()
        assertEquals(setOf(FootprintCell(0, 0, 0)), cells)
    }

    @Test
    fun `elements inherit parent collider match`() {
        val e1 = element("e1", Vec(0.0, 0.0, 0.0), Vec(16.0, 16.0, 16.0))
        val sub = BbGroup("sub", "seat", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("e1")), true)
        val outer = BbGroup("outer", "collider", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.SubGroup(sub)), true)
        val model = modelFor(listOf(e1), listOf(outer))
        val cells = BlockbenchColliderParser.readFootprintCells(model, "collider").toSet()
        assertTrue(cells.contains(FootprintCell(0, 0, 0)))
    }

    @Test
    fun `element uuids under collider bones are returned for filtering`() {
        val e1 = element("e1", Vec(0.0, 0.0, 0.0), Vec(16.0, 16.0, 16.0))
        val e2 = element("e2", Vec(16.0, 0.0, 0.0), Vec(32.0, 16.0, 16.0))
        val colliderGroup = BbGroup("g1", "collider", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("e1")), true)
        val visualGroup = BbGroup("g2", "visual", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("e2")), true)
        val model = modelFor(listOf(e1, e2), listOf(colliderGroup, visualGroup))
        val uuids = BlockbenchColliderParser.elementUuidsUnderColliderBones(model, "collider")
        assertEquals(setOf("e1"), uuids)
    }

    @Test
    fun `2x2 base cube resolves to four cells`() {
        val e1 = element("e1", Vec(0.0, 0.0, 0.0), Vec(32.0, 16.0, 32.0))
        val group = BbGroup("g1", "collider", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("e1")), true)
        val model = modelFor(listOf(e1), listOf(group))
        val cells = BlockbenchColliderParser.readFootprintCells(model, "collider").toSet()
        assertEquals(setOf(
            FootprintCell(0, 0, 0), FootprintCell(1, 0, 0),
            FootprintCell(0, 0, 1), FootprintCell(1, 0, 1),
        ), cells)
    }

    @Test
    fun `always includes the anchor cell even if colliders don't cover it`() {
        val e1 = element("e1", Vec(16.0, 0.0, 0.0), Vec(32.0, 16.0, 16.0))
        val group = BbGroup("g1", "collider", Vec.ZERO, Vec.ZERO,
            listOf(BbGroupChild.ElementRef("e1")), true)
        val model = modelFor(listOf(e1), listOf(group))
        val cells = BlockbenchColliderParser.readFootprintCells(model, "collider").toSet()
        assertTrue(cells.contains(FootprintCell(0, 0, 0)), "anchor cell must always be present")
        assertTrue(cells.contains(FootprintCell(1, 0, 0)))
    }
}
