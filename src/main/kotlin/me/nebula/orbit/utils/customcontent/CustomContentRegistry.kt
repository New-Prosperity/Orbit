package me.nebula.orbit.utils.customcontent

import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.customcontent.armor.ArmorShaderPack
import me.nebula.orbit.utils.customcontent.armor.CustomArmorRegistry
import me.nebula.orbit.utils.screen.shader.MapShaderPack
import me.nebula.orbit.utils.customcontent.block.*
import me.nebula.orbit.utils.customcontent.event.CustomBlockBreakHandler
import me.nebula.orbit.utils.customcontent.event.CustomBlockInteractHandler
import me.nebula.orbit.utils.customcontent.event.CustomBlockPlaceHandler
import me.nebula.orbit.utils.customcontent.item.*
import me.nebula.orbit.utils.customcontent.pack.PackMerger
import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.generator.ModelGenerator
import me.nebula.orbit.utils.modelengine.generator.ModelIdRegistry
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object CustomContentRegistry {

    private val logger = logger("CustomContent")
    private lateinit var resources: ResourceManager
    private var mergeResult: PackMerger.MergeResult? = null

    private const val BASE_DIR = "customcontent"
    private const val MODELS_DIR = "$BASE_DIR/models"
    private const val ITEMS_DIR = "$BASE_DIR/items"
    private const val BLOCKS_DIR = "$BASE_DIR/blocks"
    private const val ARMORS_DIR = "$BASE_DIR/armors"

    val packBytes: ByteArray? get() = mergeResult?.packBytes
    val packSha1: String? get() = mergeResult?.sha1

    fun init(resources: ResourceManager, eventNode: EventNode<Event>) {
        this.resources = resources

        resources.ensureDirectory(BASE_DIR)
        resources.ensureDirectory(MODELS_DIR)
        resources.ensureDirectory(ITEMS_DIR)
        resources.ensureDirectory(BLOCKS_DIR)
        resources.ensureDirectory(ARMORS_DIR)

        ModelIdRegistry.init(resources, "$BASE_DIR/model_ids.dat")
        BlockStateAllocator.init(resources, "$BASE_DIR/allocations.dat")

        val itemDefs = CustomItemLoader.loadAll(resources, ITEMS_DIR)
        val blockDefs = CustomBlockLoader.loadAll(resources, BLOCKS_DIR)

        itemDefs.forEach { def -> registerItem(def) }
        blockDefs.forEach { def -> registerBlock(def) }

        CustomArmorRegistry.loadFromResources(resources, ARMORS_DIR)

        if (!CustomItemRegistry.isEmpty() || !CustomBlockRegistry.isEmpty() || !CustomArmorRegistry.isEmpty()) {
            CustomBlockPlaceHandler.install(eventNode)
            CustomBlockBreakHandler.install(eventNode)
            CustomBlockInteractHandler.install(eventNode)

            logger.info {
                "Registered ${CustomItemRegistry.all().size} custom items, " +
                    "${CustomBlockRegistry.all().size} custom blocks, " +
                    "${CustomArmorRegistry.all().size} custom armors"
            }
        }
    }

    fun registerItem(def: CustomItemDefinition): CustomItem {
        val cmdId = ModelIdRegistry.assignId("cc:item:${def.id}")
        val item = CustomItem(
            id = def.id,
            baseMaterial = def.baseMaterial,
            customModelDataId = cmdId,
            displayName = def.displayName,
            lore = def.lore,
            unbreakable = def.unbreakable,
            glowing = def.glowing,
            maxStackSize = def.maxStackSize,
            modelPath = def.modelPath,
        )
        CustomItemRegistry.register(item)
        logger.info { "Registered custom item: ${item.id} (CMD=$cmdId)" }
        return item
    }

    fun registerBlock(def: CustomBlockDefinition): CustomBlock {
        val cmdId = ModelIdRegistry.assignId("cc:block:${def.id}")
        val allocatedState = BlockStateAllocator.allocate(def.id, def.hitbox)
        val block = CustomBlock(
            id = def.id,
            hitbox = def.hitbox,
            itemId = def.itemId,
            customModelDataId = cmdId,
            hardness = def.hardness,
            drops = def.drops,
            modelPath = def.modelPath,
            placeSound = def.placeSound,
            breakSound = def.breakSound,
            allocatedState = allocatedState,
        )
        CustomBlockRegistry.register(block)
        logger.info {
            "Registered custom block: ${block.id} (CMD=$cmdId, " +
                "state=${allocatedState.name()}[${allocatedState.properties().entries.joinToString(",") { "${it.key}=${it.value}" }}])"
        }
        return block
    }

    fun reload() {
        CustomItemRegistry.clear()
        CustomBlockRegistry.clear()
        CustomArmorRegistry.clear()

        val itemDefs = CustomItemLoader.loadAll(resources, ITEMS_DIR)
        val blockDefs = CustomBlockLoader.loadAll(resources, BLOCKS_DIR)

        itemDefs.forEach { def -> registerItem(def) }
        blockDefs.forEach { def -> registerBlock(def) }

        CustomArmorRegistry.loadFromResources(resources, ARMORS_DIR)

        logger.info {
            "Reloaded ${CustomItemRegistry.all().size} custom items, " +
                "${CustomBlockRegistry.all().size} custom blocks, " +
                "${CustomArmorRegistry.all().size} custom armors"
        }

        mergePack()
    }

    fun mergePack(): PackMerger.MergeResult {
        val modelFiles = resources.list("models", "bbmodel")
        val generated = modelFiles.map { path ->
            val fileName = path.substringAfterLast('/')
            logger.info { "Generating model from $fileName" }
            val raw = ModelGenerator.generateRaw(resources, fileName)
            ModelEngine.registerRaw(raw.blueprint.name, raw)
            raw
        }

        val allRaw = (ModelEngine.rawResults() + generated)
            .distinctBy { it.blueprint.name }

        val armorEntries = if (!CustomArmorRegistry.isEmpty()) {
            val armors = CustomArmorRegistry.all().toList()
            logger.info { "Generating armor shader pack for ${armors.size} armors" }
            ArmorShaderPack.generate(armors)
        } else {
            emptyMap()
        }

        val mapScreenEntries = MapShaderPack.generate()
        logger.info { "Generated map screen shader pack: ${mapScreenEntries.size} entries" }

        val allShaderEntries = armorEntries + mapScreenEntries
        val result = PackMerger.merge(resources, MODELS_DIR, allRaw, allShaderEntries)
        mergeResult = result

        resources.writeBytes("$BASE_DIR/pack.zip", result.packBytes)

        logger.info {
            "Pack merged: ${result.packBytes.size / 1024}KB, SHA-1=${result.sha1}, " +
                "models=${allRaw.size}, items=${CustomItemRegistry.all().size}, " +
                "blocks=${CustomBlockRegistry.all().size}, armors=${CustomArmorRegistry.all().size}"
        }
        return result
    }
}

inline fun customItem(id: String, block: CustomItemDsl.() -> Unit): CustomItem =
    CustomContentRegistry.registerItem(CustomItemDsl(id).apply(block).toDefinition())

inline fun customBlock(id: String, block: CustomBlockDsl.() -> Unit): CustomBlock =
    CustomContentRegistry.registerBlock(CustomBlockDsl(id).apply(block).toDefinition())
