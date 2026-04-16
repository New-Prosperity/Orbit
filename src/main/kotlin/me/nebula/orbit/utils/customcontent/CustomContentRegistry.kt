package me.nebula.orbit.utils.customcontent

import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.resource.ResourceManager
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.imageio.ImageIO
import me.nebula.orbit.utils.customcontent.armor.ArmorShaderPack
import me.nebula.orbit.utils.customcontent.armor.CustomArmorRegistry
import me.nebula.orbit.utils.customcontent.effects.EffectsShaderPack
import me.nebula.orbit.utils.tooltip.TooltipStylePack
import me.nebula.orbit.utils.hud.font.HudFontProvider
import me.nebula.orbit.utils.hud.font.HudSpriteRegistry
import me.nebula.orbit.utils.hud.shader.HudShaderPack
import me.nebula.orbit.utils.customcontent.block.BlockStateAllocator
import me.nebula.orbit.utils.customcontent.block.CustomBlock
import me.nebula.orbit.utils.customcontent.block.CustomBlockDefinition
import me.nebula.orbit.utils.customcontent.block.CustomBlockDsl
import me.nebula.orbit.utils.customcontent.block.CustomBlockLoader
import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import me.nebula.orbit.utils.customcontent.event.CustomBlockBreakHandler
import me.nebula.orbit.utils.customcontent.event.CustomBlockInteractHandler
import me.nebula.orbit.utils.customcontent.event.CustomBlockPlaceHandler
import me.nebula.orbit.utils.customcontent.item.CustomItem
import me.nebula.orbit.utils.customcontent.item.CustomItemDefinition
import me.nebula.orbit.utils.customcontent.item.CustomItemDsl
import me.nebula.orbit.utils.customcontent.item.CustomItemLoader
import me.nebula.orbit.utils.customcontent.item.CustomItemRegistry
import me.nebula.orbit.utils.customcontent.pack.PackMerger
import me.nebula.orbit.utils.customcontent.pack.SpriteItemPack
import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.generator.ModelGenerator
import me.nebula.orbit.utils.modelengine.generator.ModelIdRegistry
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object CustomContentRegistry {

    private val logger = logger("CustomContent")
    private lateinit var resources: ResourceManager
    @Volatile
    private var mergeResult: PackMerger.MergeResult? = null

    private const val BASE_DIR = "customcontent"
    private const val MODELS_DIR = "$BASE_DIR/models"
    private const val ITEMS_DIR = "$BASE_DIR/items"
    private const val BLOCKS_DIR = "$BASE_DIR/blocks"
    private const val ARMORS_DIR = "$BASE_DIR/armors"
    private const val SPRITES_DIR = "$BASE_DIR/sprites"

    val packBytes: ByteArray? get() = mergeResult?.packBytes
    val packSha1: String? get() = mergeResult?.sha1

    fun init(resources: ResourceManager, eventNode: EventNode<Event>) {
        this.resources = resources

        resources.ensureDirectory(BASE_DIR)
        resources.ensureDirectory(MODELS_DIR)
        resources.ensureDirectory(ITEMS_DIR)
        resources.ensureDirectory(BLOCKS_DIR)
        resources.ensureDirectory(ARMORS_DIR)
        resources.ensureDirectory(SPRITES_DIR)

        loadSprites()

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
        val inputHash = computeInputHash(modelFiles)
        val cacheHashPath = "$BASE_DIR/pack.hash"
        val cachePackPath = "$BASE_DIR/pack.zip"
        val cachedHash = if (resources.exists(cacheHashPath)) resources.readText(cacheHashPath).trim() else null

        if (cachedHash == inputHash && resources.exists(cachePackPath)) {
            val packBytes = resources.readBytes(cachePackPath)
            logger.info { "Pack cache hit: ${packBytes.size / 1024}KB, hash=$inputHash — skipping merge" }
            modelFiles.forEach { path ->
                val fileName = path.substringAfterLast('/')
                val raw = ModelGenerator.generateRaw(resources, fileName)
                ModelEngine.registerRaw(raw.blueprint.name, raw)
            }
            val cachedResult = PackMerger.MergeResult(packBytes, hexSha1(packBytes))
            mergeResult = cachedResult
            return cachedResult
        }

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

        val hudShaderEntries = HudShaderPack.generate()
        logger.info { "Generated HUD shader pack: ${hudShaderEntries.size} entries" }

        val hudFontEntries = HudFontProvider.generate()
        logger.info { "Generated HUD font provider: ${hudFontEntries.size} entries" }

        val effectsEntries = emptyMap<String, ByteArray>()
        logger.info { "Effects shader pack disabled for 26.1 diagnostics" }

        val tooltipEntries = TooltipStylePack.generate()
        logger.info { "Generated tooltip style pack: ${tooltipEntries.size} entries" }

        val tabListOverrides = generateTabListOverrides()

        val spriteItemEntries = SpriteItemPack.generate(resources, SPRITES_DIR)
        logger.info { "Generated sprite item pack: ${spriteItemEntries.size} entries" }

        val allShaderEntries = armorEntries + hudShaderEntries + hudFontEntries + effectsEntries + tooltipEntries + tabListOverrides + spriteItemEntries
        val result = PackMerger.merge(resources, MODELS_DIR, allRaw, allShaderEntries)
        mergeResult = result

        resources.writeBytes(cachePackPath, result.packBytes)
        resources.writeText(cacheHashPath, inputHash)

        logger.info {
            "Pack merged: ${result.packBytes.size / 1024}KB, SHA-1=${result.sha1}, hash=$inputHash, " +
                "models=${allRaw.size}, items=${CustomItemRegistry.all().size}, " +
                "blocks=${CustomBlockRegistry.all().size}, armors=${CustomArmorRegistry.all().size}"
        }
        return result
    }

    private fun computeInputHash(modelFiles: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("models:".toByteArray())
        for (path in modelFiles.sorted()) {
            digest.update(path.toByteArray())
            digest.update(0)
            digest.update(resources.readBytes(path))
            digest.update(0)
        }
        digest.update("items:".toByteArray())
        for (item in CustomItemRegistry.all().sortedBy { it.id }) {
            digest.update(item.id.toByteArray())
            digest.update(0)
            digest.update(item.customModelDataId.toString().toByteArray())
            digest.update(0)
        }
        digest.update("blocks:".toByteArray())
        for (block in CustomBlockRegistry.all().sortedBy { it.id }) {
            digest.update(block.id.toByteArray())
            digest.update(0)
            digest.update(block.customModelDataId.toString().toByteArray())
            digest.update(0)
        }
        digest.update("armors:".toByteArray())
        for (armor in CustomArmorRegistry.all().sortedBy { it.id }) {
            digest.update(armor.id.toByteArray())
            digest.update(0)
        }
        digest.update("sprites:".toByteArray())
        for (path in resources.list(SPRITES_DIR, "png", recursive = true).sorted()) {
            digest.update(path.toByteArray())
            digest.update(0)
            digest.update(resources.readBytes(path))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hexSha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun loadSprites() {
        val files = resources.list(SPRITES_DIR, "png")
        for (path in files) {
            val fileName = path.substringAfterLast('/')
            val id = fileName.removeSuffix(".png")
            if (HudSpriteRegistry.getOrNull(id) != null) {
                logger.warn { "Sprite '$id' already registered, skipping file $fileName" }
                continue
            }
            val bytes = resources.readBytes(path)
            val image = ImageIO.read(bytes.inputStream())
            HudSpriteRegistry.register(id, image)
            logger.info { "Registered sprite: $id (${image.width}x${image.height})" }
        }
    }

    private fun generateTabListOverrides(): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()

        val transparentPixel = transparentPng(1, 1)
        val pingNames = listOf("ping_1", "ping_2", "ping_3", "ping_4", "ping_5", "ping_unknown")
        for (name in pingNames) {
            entries["assets/minecraft/textures/gui/sprites/icon/$name.png"] = transparentPixel
        }

        val transparentSkin = transparentPng(64, 64)
        val wideSkins = listOf("steve", "makena", "sunny")
        val slimSkins = listOf("alex", "ari", "efe", "kai", "noor", "zuri")
        for (name in wideSkins + slimSkins) {
            entries["assets/minecraft/textures/entity/player/wide/$name.png"] = transparentSkin
            entries["assets/minecraft/textures/entity/player/slim/$name.png"] = transparentSkin
        }

        return entries
    }

    private fun transparentPng(width: Int, height: Int): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "png", baos)
        return baos.toByteArray()
    }
}

inline fun customItem(id: String, block: CustomItemDsl.() -> Unit): CustomItem =
    CustomContentRegistry.registerItem(CustomItemDsl(id).apply(block).toDefinition())

inline fun customBlock(id: String, block: CustomBlockDsl.() -> Unit): CustomBlock =
    CustomContentRegistry.registerBlock(CustomBlockDsl(id).apply(block).toDefinition())
