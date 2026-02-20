package me.nebula.orbit.utils.customcontent.block

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.loot.LootEntry
import me.nebula.orbit.utils.loot.LootTable
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

data class CustomBlockDefinition(
    val id: String,
    val hitbox: BlockHitbox,
    val itemId: String,
    val hardness: Float,
    val drops: CustomBlockDrops,
    val modelPath: String,
    val placeSound: String,
    val breakSound: String,
)

object CustomBlockLoader {

    fun load(resources: ResourceManager, path: String): CustomBlockDefinition {
        val root = JsonParser.parseString(resources.readText(path)).asJsonObject
        return parse(root)
    }

    fun loadAll(resources: ResourceManager, directory: String): List<CustomBlockDefinition> =
        resources.list(directory, "json").map { load(resources, it) }

    private fun parse(obj: JsonObject): CustomBlockDefinition {
        val id = obj["id"].asString
        val hitbox = BlockHitbox.fromString(obj["hitbox"].asString)
        val itemId = obj["item"].asString
        val hardness = obj["hardness"]?.asFloat ?: 1.5f
        val placeSound = obj["place_sound"]?.asString ?: "block.stone.place"
        val breakSound = obj["break_sound"]?.asString ?: "block.stone.break"
        val model = obj["model"].asString
        val drops = parseDrops(id, obj.getAsJsonObject("drops"))

        return CustomBlockDefinition(
            id = id,
            hitbox = hitbox,
            itemId = itemId,
            hardness = hardness,
            drops = drops,
            modelPath = model,
            placeSound = placeSound,
            breakSound = breakSound,
        )
    }

    private fun parseDrops(blockId: String, dropsObj: JsonObject?): CustomBlockDrops {
        if (dropsObj == null) return CustomBlockDrops.SelfDrop
        val self = dropsObj["self"]?.asBoolean ?: true
        if (self) return CustomBlockDrops.SelfDrop

        val lootObj = dropsObj.getAsJsonObject("loot_table")
            ?: error("Block $blockId: drops.self=false requires a loot_table")
        val rolls = lootObj["rolls"]?.asInt ?: 1
        val entries = lootObj.getAsJsonArray("entries")?.map { entry ->
            val e = entry.asJsonObject
            val materialKey = e["material"].asString
            val material = Material.fromKey(materialKey)
                ?: error("Block $blockId: unknown drop material $materialKey")
            val weight = e["weight"]?.asInt ?: 1
            val minCount = e["min_count"]?.asInt ?: 1
            val maxCount = e["max_count"]?.asInt ?: 1
            LootEntry(ItemStack.of(material), weight, minCount, maxCount)
        } ?: emptyList()

        val lootTable = LootTable("cc:$blockId", entries, rolls..rolls)
        return CustomBlockDrops.LootTableDrop(lootTable)
    }
}

class CustomBlockDsl @PublishedApi internal constructor(val id: String) {

    @PublishedApi internal var hitbox: BlockHitbox = BlockHitbox.Full
    @PublishedApi internal var itemId: String = id
    @PublishedApi internal var hardness = 1.5f
    @PublishedApi internal var drops: CustomBlockDrops = CustomBlockDrops.SelfDrop
    @PublishedApi internal var modelPath: String = "$id.bbmodel"
    @PublishedApi internal var placeSound = "block.stone.place"
    @PublishedApi internal var breakSound = "block.stone.break"

    fun hitbox(hitbox: BlockHitbox) { this.hitbox = hitbox }
    fun item(itemId: String) { this.itemId = itemId }
    fun hardness(value: Float) { hardness = value }
    fun placeSound(sound: String) { placeSound = sound }
    fun breakSound(sound: String) { breakSound = sound }
    fun model(path: String) { modelPath = path }

    fun drops(block: CustomBlockDropsDsl.() -> Unit) {
        drops = CustomBlockDropsDsl().apply(block).build()
    }

    @PublishedApi internal fun toDefinition(): CustomBlockDefinition = CustomBlockDefinition(
        id = id,
        hitbox = hitbox,
        itemId = itemId,
        hardness = hardness,
        drops = drops,
        modelPath = modelPath,
        placeSound = placeSound,
        breakSound = breakSound,
    )
}

class CustomBlockDropsDsl @PublishedApi internal constructor() {

    private val entries = mutableListOf<LootEntry>()
    private var rolls = 1

    fun entry(material: Material, weight: Int = 1, minCount: Int = 1, maxCount: Int = 1) {
        entries += LootEntry(ItemStack.of(material), weight, minCount, maxCount)
    }

    fun rolls(count: Int) { rolls = count }

    @PublishedApi internal fun build(): CustomBlockDrops =
        if (entries.isEmpty()) CustomBlockDrops.SelfDrop
        else CustomBlockDrops.LootTableDrop(LootTable("dsl", entries.toList(), rolls..rolls))
}
