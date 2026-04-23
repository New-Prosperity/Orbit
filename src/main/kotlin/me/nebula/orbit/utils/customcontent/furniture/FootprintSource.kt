package me.nebula.orbit.utils.customcontent.furniture

sealed interface FootprintSource {

    data class Cells(val cells: List<FootprintCell>) : FootprintSource {
        init { require(cells.isNotEmpty()) { "Cells list must not be empty" } }
    }

    data class Box(val width: Int, val height: Int = 1, val depth: Int = 1) : FootprintSource {
        init {
            require(width >= 1 && height >= 1 && depth >= 1) {
                "Box dimensions must all be >= 1; got ${width}x${height}x${depth}"
            }
        }
    }

    data class FromBones(val prefix: String = "collider") : FootprintSource {
        init { require(prefix.isNotBlank()) { "FromBones prefix must not be blank" } }
    }

    companion object {
        val SINGLE: FootprintSource = Cells(listOf(FootprintCell(0, 0, 0)))
    }
}

fun FootprintSource.resolveCells(colliderLookup: (String) -> List<FootprintCell>? = { null }): List<FootprintCell> =
    when (this) {
        is FootprintSource.Cells -> cells
        is FootprintSource.Box -> buildList {
            for (dx in 0 until width)
                for (dy in 0 until height)
                    for (dz in 0 until depth)
                        add(FootprintCell(dx, dy, dz))
        }
        is FootprintSource.FromBones -> colliderLookup(prefix)
            ?: error("Collider bone lookup returned null for prefix '$prefix' — make sure the .bbmodel has bones with that prefix")
    }
