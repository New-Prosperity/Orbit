package me.nebula.orbit.utils.customcontent.furniture

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.ether.utils.translation.TranslationKey
import me.nebula.orbit.utils.modelengine.generator.BlockbenchParser

object FurnitureJsonLoader {

    private val logger = logger("FurnitureJsonLoader")

    data class ParsedFurniture(
        val definition: FurnitureDefinition,
        val bbmodelPath: String?,
        val colliderElementUuids: Set<String>,
    )

    fun loadAll(resources: ResourceManager, directory: String): List<ParsedFurniture> {
        resources.ensureDirectory(directory)
        val jsons = resources.list(directory, "json", recursive = false)
        val bbmodels = resources.list(directory, "bbmodel", recursive = false)
        val bbmodelByStem = bbmodels.associateBy {
            it.substringAfterLast('/').removeSuffix(".bbmodel")
        }

        val results = mutableListOf<ParsedFurniture>()
        val covered = mutableSetOf<String>()

        for (jsonPath in jsons) {
            val stem = jsonPath.substringAfterLast('/').removeSuffix(".json")
            covered += stem
            runCatching {
                val parsed = parseOne(resources, directory, stem, jsonPath, bbmodelByStem[stem])
                results += parsed
            }.onFailure { e ->
                logger.warn { "Failed to parse furniture json '$jsonPath': ${e.message}" }
            }
        }

        for ((stem, bbmodelPath) in bbmodelByStem) {
            if (stem in covered) continue
            runCatching {
                val definition = FurnitureDefinition(id = stem, itemId = stem)
                results += ParsedFurniture(definition, bbmodelPath, emptySet())
            }.onFailure { e ->
                logger.warn { "Failed to bootstrap furniture from bbmodel '$bbmodelPath': ${e.message}" }
            }
        }

        return results
    }

    private fun parseOne(
        resources: ResourceManager,
        directory: String,
        stem: String,
        jsonPath: String,
        bbmodelPath: String?,
    ): ParsedFurniture {
        val root = JsonParser.parseString(resources.readText(jsonPath)).asJsonObject
        val id = root.get("id")?.asString ?: stem
        val itemId = root.get("item")?.asString ?: root.get("itemId")?.asString ?: id

        val placeSound = root.get("placeSound")?.asString ?: root.get("place_sound")?.asString ?: "block.wood.place"
        val breakSound = root.get("breakSound")?.asString ?: root.get("break_sound")?.asString ?: "block.wood.break"
        val scale = root.get("scale")?.asDouble ?: 1.0
        val visualRotationSnap = root.get("visualRotationSnap")?.asDouble
            ?: root.get("rotation_snap")?.asDouble
            ?: 0.0
        val collision = parseCollision(root.get("collision")?.asString)
        val interaction = parseInteraction(root.getAsJsonObject("interaction"))
        val lightLevel = root.get("lightLevel")?.asInt ?: root.get("light_level")?.asInt ?: 0
        val lightOnlyWhenOpen = root.get("lightOnlyWhenOpen")?.asBoolean
            ?: root.get("light_only_when_open")?.asBoolean
            ?: false
        val placement = parsePlacement(root.get("placement"))

        val colliderPrefix = root.get("colliderPrefix")?.asString ?: DEFAULT_COLLIDER_PREFIX
        val (footprint, colliderUuids, cellDecisions) = parseFootprintAndColliders(
            resources, directory, root.get("footprint"), bbmodelPath, colliderPrefix,
        )

        val definition = FurnitureDefinition(
            id = id,
            itemId = itemId,
            footprint = footprint,
            placeSound = placeSound,
            breakSound = breakSound,
            scale = scale,
            visualRotationSnap = visualRotationSnap,
            collision = collision,
            interaction = interaction,
            lightLevel = lightLevel,
            lightOnlyWhenOpen = lightOnlyWhenOpen,
            cellDecisions = cellDecisions,
            placement = placement,
        )
        return ParsedFurniture(definition, bbmodelPath, colliderUuids)
    }

    private fun parseCollision(raw: String?): FurnitureCollision = when (raw?.lowercase()) {
        null, "solid" -> FurnitureCollision.Solid
        "nonsolid", "non_solid", "non-solid" -> FurnitureCollision.NonSolid
        else -> error("Unknown collision mode: $raw (expected 'solid' or 'nonsolid')")
    }

    private fun parsePlacement(element: com.google.gson.JsonElement?): FurniturePlacement {
        if (element == null) return FurniturePlacement.FLOOR
        if (element.isJsonPrimitive) {
            val name = element.asString
            return FurniturePlacement.byName(name)
                ?: error("Unknown placement profile: '$name' (expected 'floor', 'ceiling', 'wall', or 'any')")
        }
        if (!element.isJsonObject) error("placement must be a string profile or an object")
        val obj = element.asJsonObject
        val facesArray = obj.getAsJsonArray("allowed_faces") ?: obj.getAsJsonArray("allowedFaces")
        val allowedFaces = if (facesArray == null) {
            net.minestom.server.instance.block.BlockFace.values().toSet()
        } else {
            facesArray.map { parseFace(it.asString) }.toSet()
        }
        val autoOrient = obj.get("auto_orient")?.asBoolean
            ?: obj.get("autoOrient")?.asBoolean
            ?: true
        require(allowedFaces.isNotEmpty()) { "placement.allowed_faces must not be empty" }
        return FurniturePlacement(allowedFaces, autoOrient)
    }

    private fun parseFace(raw: String): net.minestom.server.instance.block.BlockFace = when (raw.lowercase()) {
        "top", "up" -> net.minestom.server.instance.block.BlockFace.TOP
        "bottom", "down" -> net.minestom.server.instance.block.BlockFace.BOTTOM
        "north" -> net.minestom.server.instance.block.BlockFace.NORTH
        "south" -> net.minestom.server.instance.block.BlockFace.SOUTH
        "east" -> net.minestom.server.instance.block.BlockFace.EAST
        "west" -> net.minestom.server.instance.block.BlockFace.WEST
        else -> error("Unknown face '$raw' (expected top/bottom/north/south/east/west)")
    }

    private fun parseInteraction(obj: JsonObject?): FurnitureInteraction? {
        if (obj == null) return null
        val type = obj.get("type")?.asString?.lowercase()
            ?: error("Interaction missing 'type' field")
        return when (type) {
            "seat" -> FurnitureInteraction.Seat(
                offsetY = obj.get("offsetY")?.asDouble ?: 0.4,
                yawOffsetDegrees = obj.get("yawOffsetDegrees")?.asFloat ?: 0f,
            )
            "open_close", "openclose" -> FurnitureInteraction.OpenClose(
                openItemId = obj.get("openItemId")?.asString ?: error("open_close requires openItemId"),
                closedItemId = obj.get("closedItemId")?.asString ?: error("open_close requires closedItemId"),
            )
            "loot_container", "lootcontainer", "container" -> FurnitureInteraction.LootContainer(
                rows = obj.get("rows")?.asInt ?: 3,
                titleKey = obj.get("titleKey")?.asString?.let { TranslationKey(it) },
            )
            "custom" -> FurnitureInteraction.Custom(
                handlerId = obj.get("handlerId")?.asString ?: error("custom requires handlerId"),
            )
            else -> error("Unknown interaction type: $type")
        }
    }

    private data class FootprintParseResult(
        val footprint: FurnitureFootprint,
        val colliderUuids: Set<String>,
        val cellDecisions: Map<FootprintCell, CellDecision>,
    )

    private fun parseFootprintAndColliders(
        resources: ResourceManager,
        directory: String,
        footprintElement: com.google.gson.JsonElement?,
        bbmodelPath: String?,
        colliderPrefix: String,
    ): FootprintParseResult {
        if (footprintElement == null) return FootprintParseResult(FurnitureFootprint.SINGLE, emptySet(), emptyMap())

        if (footprintElement.isJsonArray) {
            return FootprintParseResult(FurnitureFootprint(parseCellsArray(footprintElement.asJsonArray)), emptySet(), emptyMap())
        }

        val obj = footprintElement.asJsonObject
        val type = obj.get("type")?.asString?.lowercase() ?: "cells"
        return when (type) {
            "cells" -> FootprintParseResult(FurnitureFootprint(parseCellsArray(obj.getAsJsonArray("cells"))), emptySet(), emptyMap())
            "box" -> {
                val size = obj.getAsJsonArray("size")
                val width = size[0].asInt
                val height = if (size.size() > 1) size[1].asInt else 1
                val depth = if (size.size() > 2) size[2].asInt else 1
                FootprintParseResult(FurnitureFootprint(FootprintSource.Box(width, height, depth).resolveCells()), emptySet(), emptyMap())
            }
            "bones", "from_bones", "frombones" -> {
                val prefix = obj.get("prefix")?.asString ?: colliderPrefix
                val model = bbmodelPath?.let { loadModel(resources, it) }
                    ?: error("footprint type 'bones' requires a sibling .bbmodel file")
                val decisions = BlockbenchColliderParser.classifyCells(model, prefix)
                require(decisions.isNotEmpty()) {
                    "No collider bones found with prefix '$prefix' in $bbmodelPath"
                }
                val colliderUuids = BlockbenchColliderParser.elementUuidsUnderColliderBones(model, prefix)
                FootprintParseResult(FurnitureFootprint(decisions.keys.toList()), colliderUuids, decisions)
            }
            else -> error("Unknown footprint type: $type")
        }
    }

    private fun parseCellsArray(array: JsonArray): List<FootprintCell> {
        val cells = array.map { cellElement ->
            val arr = cellElement.asJsonArray
            require(arr.size() == 3) { "Cell must be [dx, dy, dz]; got ${arr.size()} entries" }
            FootprintCell(arr[0].asInt, arr[1].asInt, arr[2].asInt)
        }
        return cells
    }

    private fun loadModel(resources: ResourceManager, path: String) =
        resources.reader(path).use { BlockbenchParser.parse(path.substringAfterLast('/').removeSuffix(".bbmodel"), it) }

    internal const val DEFAULT_COLLIDER_PREFIX = "collider"
}
