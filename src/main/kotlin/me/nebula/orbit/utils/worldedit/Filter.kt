package me.nebula.orbit.utils.worldedit

import net.minestom.server.instance.Section
import java.util.concurrent.atomic.AtomicInteger

fun interface SectionFilter {
    fun apply(section: Section, sectionY: Int, buffer: ChunkBuffer)
}

object Filters {

    fun set(pattern: Pattern): SectionFilter = SectionFilter { section, sectionY, buffer ->
        val baseY = sectionY * 16
        for (y in 0..15) {
            for (z in 0..15) {
                for (x in 0..15) {
                    buffer.set(x, baseY + y, z, pattern.apply(x, baseY + y, z))
                }
            }
        }
    }

    fun replace(mask: Mask, pattern: Pattern): SectionFilter = SectionFilter { section, sectionY, buffer ->
        val palette = section.blockPalette()
        val baseY = sectionY * 16
        for (y in 0..15) {
            for (z in 0..15) {
                for (x in 0..15) {
                    val current = palette.get(x, y, z)
                    if (mask.test(current)) {
                        buffer.set(x, baseY + y, z, pattern.apply(x, baseY + y, z))
                    }
                }
            }
        }
    }

    fun count(mask: Mask, counter: AtomicInteger): SectionFilter = SectionFilter { section, _, _ ->
        val palette = section.blockPalette()
        var localCount = 0
        for (y in 0..15) {
            for (z in 0..15) {
                for (x in 0..15) {
                    if (mask.test(palette.get(x, y, z))) {
                        localCount++
                    }
                }
            }
        }
        counter.addAndGet(localCount)
    }
}
