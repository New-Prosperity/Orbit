package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.customcontent.item.CustomItemRegistry
import me.nebula.orbit.utils.nebulaworld.NebulaWorld
import me.nebula.orbit.utils.nebulaworld.NebulaWorldLoader
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.item.ItemStack

object Furniture {

    private val logger = logger("Furniture")
    private var eventNode: EventNode<Event>? = null
    private val postLoadHook: (InstanceContainer, NebulaWorld) -> Unit = { instance, world ->
        val result = FurniturePersistence.restore(instance, world)
        if (result.restored > 0 || result.skipped > 0) {
            logger.info { "Furniture post-load: restored=${result.restored} skipped=${result.skipped}" }
        }
    }
    private val preUnloadHook: (InstanceContainer, NebulaWorld) -> Unit = { instance, _ ->
        val placed = PlacedFurnitureStore.all(instance).toList()
        for (furniture in placed) {
            FurnitureDisplaySpawner.despawn(instance, furniture.displayEntityId)
            for (entityId in furniture.interactionEntityIds) {
                InteractionEntitySpawner.despawn(instance, entityId)
            }
            SeatController.onFurnitureBroken(furniture.uuid)
            DisplayCullController.onBroken(furniture.uuid)
            FurnitureLightingController.onBroken(instance, furniture)
            FurnitureInstanceState.remove(furniture.uuid)
        }
        PlacedFurnitureStore.clear(instance)
    }

    fun install() {
        if (eventNode != null) return
        FurnitureRegistry.validateAgainstItemRegistry()
        val node = EventNode.all("furniture")
        FurnitureListener.install(node)
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
        SeatController.install()
        DisplayCullController.install()
        FurnitureWandController.install()
        NebulaWorldLoader.registerPostLoadHook(postLoadHook)
        NebulaWorldLoader.registerPreUnloadHook(preUnloadHook)
        for ((name, instance) in NebulaWorldLoader.all()) {
            val world = NebulaWorldLoader.worldFor(name) ?: continue
            runCatching { postLoadHook(instance, world) }.onFailure {
                logger.warn { "Backfill restore failed for world '$name': ${it.message}" }
            }
        }
        logger.info { "Furniture installed with ${FurnitureRegistry.all().size} definitions" }
    }

    fun uninstall() {
        val node = eventNode ?: return
        MinecraftServer.getGlobalEventHandler().removeChild(node)
        eventNode = null
        NebulaWorldLoader.unregisterPostLoadHook(postLoadHook)
        NebulaWorldLoader.unregisterPreUnloadHook(preUnloadHook)
        SeatController.uninstall()
        DisplayCullController.uninstall()
        FurnitureWandController.uninstall()
        FurnitureLightingController.clearAll()
        FurnitureInstanceState.clear()
        PlacedFurnitureStore.clearAll()
    }

    fun giveItem(player: Player, id: String, amount: Int = 1): Boolean {
        val definition = FurnitureRegistry[id] ?: return false
        val customItem = CustomItemRegistry[definition.itemId] ?: return false
        val stack = customItem.createStack(amount)
        player.inventory.addItemStack(stack)
        return true
    }

    fun itemFor(id: String, amount: Int = 1): ItemStack? {
        val definition = FurnitureRegistry[id] ?: return null
        val customItem = CustomItemRegistry[definition.itemId] ?: return null
        return customItem.createStack(amount)
    }
}

@DslMarker
annotation class FurnitureDslMarker

@FurnitureDslMarker
class FurnitureBuilder @PublishedApi internal constructor(private val id: String) {

    @PublishedApi internal var itemId: String = id
    @PublishedApi internal var footprint: FurnitureFootprint = FurnitureFootprint.SINGLE
    @PublishedApi internal var placeSound: String = "block.wood.place"
    @PublishedApi internal var breakSound: String = "block.wood.break"
    @PublishedApi internal var scale: Double = 1.0
    @PublishedApi internal var visualRotationSnap: Double = 0.0
    @PublishedApi internal var collision: FurnitureCollision = FurnitureCollision.Solid
    @PublishedApi internal var interaction: FurnitureInteraction? = null

    fun item(id: String) { itemId = id }
    fun placeSound(sound: String) { placeSound = sound }
    fun breakSound(sound: String) { breakSound = sound }
    fun scale(value: Double) { scale = value }
    fun rotationSnap(degrees: Double) { visualRotationSnap = degrees }
    fun collision(mode: FurnitureCollision) { collision = mode }
    fun interaction(interaction: FurnitureInteraction) { this.interaction = interaction }

    fun seat(offsetY: Double = 0.4, yawOffsetDegrees: Float = 0f) {
        interaction = FurnitureInteraction.Seat(offsetY, yawOffsetDegrees)
    }

    fun openClose(openItemId: String, closedItemId: String) {
        interaction = FurnitureInteraction.OpenClose(openItemId, closedItemId)
    }

    fun lootContainer(rows: Int = 3) {
        interaction = FurnitureInteraction.LootContainer(rows)
    }

    fun customInteraction(handlerId: String) {
        interaction = FurnitureInteraction.Custom(handlerId)
    }

    fun footprint(block: FootprintBuilder.() -> Unit) {
        footprint = FootprintBuilder().apply(block).build()
    }

    @PublishedApi internal fun build(): FurnitureDefinition = FurnitureDefinition(
        id = id,
        itemId = itemId,
        footprint = footprint,
        placeSound = placeSound,
        breakSound = breakSound,
        scale = scale,
        visualRotationSnap = visualRotationSnap,
        collision = collision,
        interaction = interaction,
    )
}

@FurnitureDslMarker
class FootprintBuilder @PublishedApi internal constructor() {

    private val cells = mutableListOf<FootprintCell>()

    init { cells += FootprintCell(0, 0, 0) }

    fun clear() { cells.clear() }

    fun cell(dx: Int, dy: Int, dz: Int) {
        cells += FootprintCell(dx, dy, dz)
    }

    fun box(width: Int, height: Int = 1, depth: Int = 1) {
        cells.clear()
        cells.addAll(FootprintSource.Box(width, height, depth).resolveCells())
    }

    fun cells(source: List<FootprintCell>) {
        cells.clear()
        cells.addAll(source)
    }

    @PublishedApi internal fun build(): FurnitureFootprint = FurnitureFootprint(cells.toList())
}

inline fun furniture(id: String, block: FurnitureBuilder.() -> Unit): FurnitureDefinition =
    FurnitureBuilder(id).apply(block).build().also { FurnitureRegistry.register(it) }
