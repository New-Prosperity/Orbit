package me.nebula.orbit.utils.customcontent.furniture

data class FootprintCell(val dx: Int, val dy: Int, val dz: Int)

data class FurnitureFootprint(val cells: List<FootprintCell>) {

    init {
        require(cells.isNotEmpty()) { "Footprint must have at least one cell" }
        require(cells.toSet().size == cells.size) { "Duplicate footprint cells: $cells" }
        require(cells.any { it.dx == 0 && it.dy == 0 && it.dz == 0 }) {
            "Footprint must include the anchor cell (0,0,0); got $cells"
        }
    }

    val size: Int get() = cells.size

    companion object {
        val SINGLE: FurnitureFootprint = FurnitureFootprint(listOf(FootprintCell(0, 0, 0)))
    }
}

enum class FurnitureCollision { Solid, NonSolid }

data class FurnitureDefinition(
    val id: String,
    val itemId: String,
    val footprint: FurnitureFootprint = FurnitureFootprint.SINGLE,
    val placeSound: String = "block.wood.place",
    val breakSound: String = "block.wood.break",
    val scale: Double = 1.0,
    val visualRotationSnap: Double = 0.0,
    val collision: FurnitureCollision = FurnitureCollision.Solid,
    val interaction: FurnitureInteraction? = null,
    val lightLevel: Int = 0,
    val lightOnlyWhenOpen: Boolean = false,
    val cellDecisions: Map<FootprintCell, CellDecision> = emptyMap(),
    val placement: FurniturePlacement = FurniturePlacement.FLOOR,
) {

    fun decisionFor(cell: FootprintCell): CellDecision =
        cellDecisions[cell] ?: CellDecision.Barrier

    init {
        require(id.isNotBlank()) { "Furniture id must not be blank" }
        require(itemId.isNotBlank()) { "Furniture itemId must not be blank" }
        require(scale > 0.0) { "Scale must be positive: $scale" }
        require(visualRotationSnap >= 0.0) { "visualRotationSnap must be >= 0 (0 = continuous): $visualRotationSnap" }
        require(visualRotationSnap == 0.0 || 360.0 % visualRotationSnap < 0.001) {
            "visualRotationSnap must divide 360 evenly: $visualRotationSnap"
        }
        require(lightLevel in 0..15) { "lightLevel must be 0..15; got $lightLevel" }
    }
}
