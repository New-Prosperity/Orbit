package me.nebula.orbit.utils.customcontent.furniture

import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.trait.CancellableEvent

class FurniturePlacePreEvent(
    val player: Player,
    val definition: FurnitureDefinition,
    val anchor: Point,
) : Event, CancellableEvent {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
}

class FurniturePlacedEvent(
    val player: Player?,
    val furniture: FurnitureInstance,
) : Event

class FurnitureBreakPreEvent(
    val player: Player?,
    val furniture: FurnitureInstance,
) : Event, CancellableEvent {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
}

class FurnitureBrokenEvent(
    val player: Player?,
    val furniture: FurnitureInstance,
) : Event
