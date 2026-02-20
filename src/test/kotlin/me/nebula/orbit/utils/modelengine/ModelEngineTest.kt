package me.nebula.orbit.utils.modelengine

import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.ether.utils.resource.resourceManager
import me.nebula.orbit.utils.modelengine.generator.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileReader
import java.nio.file.Files
import java.util.zip.ZipInputStream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ModelEngineTest {

    private lateinit var tempDir: File
    private lateinit var resources: ResourceManager
    private val blueprintsDir = File("src/main/resources/blueprints")
    private val parsedModels = mutableMapOf<String, BlockbenchModel>()

    @BeforeAll
    fun setup() {
        tempDir = Files.createTempDirectory("modelengine-test").toFile()
        resources = resourceManager {
            dataDirectory = tempDir.toPath()
        }
        ModelIdRegistry.init(resources, "model_ids.dat")
    }

    @AfterAll
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    private fun allBbmodelFiles(): List<File> =
        blueprintsDir.walkTopDown().filter { it.extension == "bbmodel" }.toList().sortedBy { it.name }

    private fun parseModel(file: File): BlockbenchModel =
        FileReader(file).use { BlockbenchParser.parse(file.nameWithoutExtension, it) }

    @Test
    @Order(1)
    fun `discovers all blueprint files`() {
        val files = allBbmodelFiles()
        assertTrue(files.isNotEmpty(), "No .bbmodel files found in $blueprintsDir")
        println("Found ${files.size} .bbmodel files:")
        files.forEach { println("  ${it.relativeTo(blueprintsDir)}") }
    }

    @Test
    @Order(2)
    fun `parses all bbmodel files`() {
        allBbmodelFiles().forEach { file ->
            val model = assertDoesNotThrow<BlockbenchModel>("Failed to parse ${file.name}") {
                parseModel(file)
            }
            assertEquals(file.nameWithoutExtension, model.name)
            parsedModels[model.name] = model
            println("${model.name}: elements=${model.elements.size}, groups=${model.groups.size}, " +
                "textures=${model.textures.size}, animations=${model.animations.size}")
        }
        assertEquals(allBbmodelFiles().size, parsedModels.size)
    }

    @Test
    @Order(3)
    fun `parsed models have valid structure`() {
        ensureParsed()
        parsedModels.values.forEach { model ->
            assertTrue(model.groups.isNotEmpty(), "${model.name}: no bone groups")
            assertTrue(model.textures.isNotEmpty(), "${model.name}: no textures")
            model.textures.forEach { tex ->
                assertTrue(tex.source.isNotEmpty(), "${model.name}: texture '${tex.name}' has empty source")
                assertTrue(tex.width > 0 && tex.height > 0, "${model.name}: texture '${tex.name}' has invalid dimensions")
            }
            model.groups.forEach { group ->
                assertTrue(group.uuid.isNotEmpty(), "${model.name}: group '${group.name}' has empty UUID")
                assertTrue(group.name.isNotEmpty(), "${model.name}: group has empty name")
            }
            model.elements.forEach { element ->
                assertTrue(element.uuid.isNotEmpty(), "${model.name}: element '${element.name}' has empty UUID")
                assertTrue(element.faces.isNotEmpty(), "${model.name}: element '${element.name}' has no faces")
            }
        }
    }

    @Test
    @Order(4)
    fun `animations have valid keyframes`() {
        ensureParsed()
        var totalAnimations = 0
        parsedModels.values.forEach { model ->
            model.animations.forEach { anim ->
                totalAnimations++
                assertTrue(anim.length > 0f, "${model.name}: animation '${anim.name}' has zero length")
                assertTrue(anim.loop in listOf("loop", "hold", "once"), "${model.name}: animation '${anim.name}' has unknown loop mode '${anim.loop}'")
                anim.animators.values.forEach { animator ->
                    animator.keyframes.forEach { kf ->
                        assertTrue(kf.time >= 0f, "${model.name}: animation '${anim.name}' has negative keyframe time")
                        assertTrue(kf.channel in listOf("position", "rotation", "scale"),
                            "${model.name}: animation '${anim.name}' has unknown channel '${kf.channel}'")
                    }
                }
            }
        }
        println("Validated $totalAnimations animations across ${parsedModels.size} models")
    }

    @Test
    @Order(5)
    fun `atlas stitching works for all models`() {
        ensureParsed()
        parsedModels.values.forEach { model ->
            val atlas = assertDoesNotThrow<AtlasResult>("Failed to stitch atlas for ${model.name}") {
                AtlasManager.stitch(model.textures)
            }
            assertTrue(atlas.width > 0, "${model.name}: atlas width is 0")
            assertTrue(atlas.height > 0, "${model.name}: atlas height is 0")
            assertTrue(isPowerOf2(atlas.width), "${model.name}: atlas width ${atlas.width} is not power of 2")
            assertTrue(isPowerOf2(atlas.height), "${model.name}: atlas height ${atlas.height} is not power of 2")
            assertEquals(model.textures.size, atlas.entries.size, "${model.name}: atlas entry count mismatch")

            val atlasBytes = AtlasManager.toBytes(atlas.image)
            assertTrue(atlasBytes.isNotEmpty(), "${model.name}: atlas PNG bytes are empty")

            println("${model.name}: atlas=${atlas.width}x${atlas.height}, entries=${atlas.entries.size}, bytes=${atlasBytes.size}")
        }
    }

    @Test
    @Order(6)
    fun `atlas entries have valid offsets`() {
        ensureParsed()
        parsedModels.values.forEach { model ->
            val atlas = AtlasManager.stitch(model.textures)
            atlas.entries.forEach { entry ->
                assertTrue(entry.offsetX >= 0, "${model.name}: entry '${entry.texture.name}' has negative offsetX")
                assertTrue(entry.offsetY >= 0, "${model.name}: entry '${entry.texture.name}' has negative offsetY")
                assertTrue(entry.offsetX + entry.image.width <= atlas.width,
                    "${model.name}: entry '${entry.texture.name}' overflows atlas width")
                assertTrue(entry.offsetY + entry.image.height <= atlas.height,
                    "${model.name}: entry '${entry.texture.name}' overflows atlas height")
            }
        }
    }

    @Test
    @Order(7)
    fun `bone element generation works`() {
        ensureParsed()
        parsedModels.values.forEach { model ->
            val atlas = AtlasManager.stitch(model.textures)
            model.groups.forEach { group ->
                val elements = group.children.filterIsInstance<BbGroupChild.ElementRef>()
                    .mapNotNull { ref -> model.elements.find { it.uuid == ref.uuid } }
                if (elements.isNotEmpty()) {
                    val generated = assertDoesNotThrow<List<GeneratedElement>>(
                        "Failed to build bone elements for ${model.name}/${group.name}"
                    ) {
                        ModelGenerator.buildBoneElements(elements, group, atlas)
                    }
                    assertEquals(elements.size, generated.size,
                        "${model.name}/${group.name}: generated element count mismatch")
                    generated.forEach { elem ->
                        assertEquals(3, elem.from.size, "${model.name}/${group.name}: from array is not 3 floats")
                        assertEquals(3, elem.to.size, "${model.name}/${group.name}: to array is not 3 floats")
                        assertTrue(elem.faces.isNotEmpty(), "${model.name}/${group.name}: generated element has no faces")
                        elem.faces.values.forEach { face ->
                            assertEquals(4, face.uv.size, "${model.name}/${group.name}: face UV is not 4 floats")
                        }
                    }
                }
            }
        }
    }

    @Test
    @Order(8)
    fun `full pipeline generateRaw works for all models`() {
        allBbmodelFiles().forEach { file ->
            val model = parseModel(file)
            val result = assertDoesNotThrow<RawGenerationResult>(
                "Failed generateRaw for ${file.name}"
            ) {
                ModelGenerator.generateRaw(model)
            }
            assertNotNull(result.blueprint, "${file.name}: blueprint is null")
            assertTrue(result.boneModels.isNotEmpty(), "${file.name}: no bone models generated")
            assertTrue(result.textureBytes.isNotEmpty(), "${file.name}: no texture bytes generated")

            val bp = result.blueprint
            assertTrue(bp.bones.isNotEmpty(), "${file.name}: blueprint has no bones")
            assertTrue(bp.rootBoneNames.isNotEmpty(), "${file.name}: blueprint has no root bones")

            bp.rootBoneNames.forEach { rootName ->
                assertNotNull(bp.bones[rootName], "${file.name}: root bone '$rootName' not in bone map")
            }

            bp.bones.values.forEach { bone ->
                if (bone.parentName != null) {
                    assertNotNull(bp.bones[bone.parentName],
                        "${file.name}: bone '${bone.name}' references missing parent '${bone.parentName}'")
                }
                bone.childNames.forEach { childName ->
                    assertNotNull(bp.bones[childName],
                        "${file.name}: bone '${bone.name}' references missing child '$childName'")
                }
            }

            val totalBones = result.boneModels.size
            val totalElements = result.boneModels.values.sumOf { it.elements.size }
            val totalAnimations = bp.animations.size

            println("${file.nameWithoutExtension}: bones=$totalBones, elements=$totalElements, " +
                "animations=$totalAnimations, textures=${result.textureBytes.size}")
        }
    }

    @Test
    @Order(9)
    fun `model IDs are assigned and persistent`() {
        val file = allBbmodelFiles().first()
        val model = parseModel(file)
        val firstGroupName = model.groups.first().name

        val id1 = ModelIdRegistry.assignId(model.name, firstGroupName)
        val id2 = ModelIdRegistry.assignId(model.name, firstGroupName)
        assertEquals(id1, id2, "Same key should return same ID")

        val id3 = ModelIdRegistry.assignId("test_unique_key_${System.nanoTime()}")
        assertNotEquals(id1, id3, "Different key should return different ID")

        val allIds = ModelIdRegistry.all()
        assertTrue(allIds.isNotEmpty(), "ID registry should not be empty after assignments")
        assertEquals(allIds.values.toSet().size, allIds.values.size, "All assigned IDs should be unique")
    }

    @Test
    @Order(10)
    fun `pack writer produces valid zip for each model`() {
        allBbmodelFiles().forEach { file ->
            val model = parseModel(file)
            val raw = ModelGenerator.generateRaw(model)
            val packBytes = assertDoesNotThrow<ByteArray>(
                "Failed to write pack for ${file.name}"
            ) {
                PackWriter.write(
                    packName = file.nameWithoutExtension,
                    packDescription = "Test: ${file.nameWithoutExtension}",
                    models = raw.boneModels,
                    textureBytes = raw.textureBytes,
                )
            }
            assertTrue(packBytes.isNotEmpty(), "${file.name}: pack bytes are empty")

            val entries = readZipEntries(packBytes)
            assertTrue("pack.mcmeta" in entries, "${file.name}: missing pack.mcmeta")
            assertTrue(entries.any { it.startsWith("assets/minecraft/models/modelengine/") },
                "${file.name}: no model files in pack")
            assertTrue(entries.any { it.startsWith("assets/minecraft/textures/modelengine/") },
                "${file.name}: no texture files in pack")

            val modelEntries = entries.filter { it.endsWith(".json") && it.contains("modelengine") }
            assertEquals(raw.boneModels.size, modelEntries.size - (if ("pack.mcmeta" in modelEntries) 1 else 0),
                "${file.name}: model file count doesn't match bone count")

            println("${file.nameWithoutExtension}: pack=${packBytes.size / 1024}KB, entries=${entries.size}")
        }
    }

    @Test
    @Order(11)
    fun `flat model generation works`() {
        ensureParsed()
        parsedModels.values.forEach { model ->
            val (flatModel, atlasBytes) = assertDoesNotThrow<Pair<GeneratedBoneModel, ByteArray>>(
                "Failed to build flat model for ${model.name}"
            ) {
                ModelGenerator.buildFlatModel(model)
            }
            assertTrue(flatModel.elements.isNotEmpty(), "${model.name}: flat model has no elements")
            assertTrue(flatModel.textures.isNotEmpty(), "${model.name}: flat model has no textures")
            assertTrue(atlasBytes.isNotEmpty(), "${model.name}: flat model atlas bytes empty")
            println("${model.name}: flat elements=${flatModel.elements.size}, atlasBytes=${atlasBytes.size}")
        }
    }

    @Test
    @Order(12)
    fun `generate produces valid result and registers blueprint`() {
        val file = allBbmodelFiles().first()
        val model = parseModel(file)

        val result = assertDoesNotThrow<GenerationResult>(
            "Failed full generate for ${file.name}"
        ) {
            ModelGenerator.generate(model)
        }

        assertTrue(result.packBytes.isNotEmpty(), "Pack bytes empty")
        assertTrue(result.boneCount > 0, "No bones generated")

        val registeredBlueprint = ModelEngine.blueprintOrNull(file.nameWithoutExtension)
        assertNotNull(registeredBlueprint, "Blueprint not registered in ModelEngine after generate()")

        val entries = readZipEntries(result.packBytes)
        assertTrue("pack.mcmeta" in entries, "Pack missing pack.mcmeta")

        println("Full generate: ${file.nameWithoutExtension} -> ${result.packBytes.size / 1024}KB, " +
            "bones=${result.boneCount}, blueprint registered=true")
    }

    @Test
    @Order(13)
    fun `blueprint hierarchy is consistent`() {
        allBbmodelFiles().forEach { file ->
            val model = parseModel(file)
            val raw = ModelGenerator.generateRaw(model)
            val bp = raw.blueprint

            val allChildReferences = bp.bones.values.flatMap { it.childNames }.toSet()
            val allBoneNames = bp.bones.keys

            allChildReferences.forEach { childName ->
                assertTrue(childName in allBoneNames,
                    "${file.nameWithoutExtension}: child reference '$childName' not in bone map")
            }

            val allParentReferences = bp.bones.values.mapNotNull { it.parentName }.toSet()
            allParentReferences.forEach { parentName ->
                assertTrue(parentName in allBoneNames,
                    "${file.nameWithoutExtension}: parent reference '$parentName' not in bone map")
            }

            bp.bones.values.forEach { bone ->
                bone.childNames.forEach { childName ->
                    val child = bp.bones[childName]!!
                    assertEquals(bone.name, child.parentName,
                        "${file.nameWithoutExtension}: bone '${bone.name}' lists child '$childName' " +
                            "but child's parent is '${child.parentName}'")
                }
            }

            val roots = bp.bones.values.filter { it.parentName == null }.map { it.name }.toSet()
            assertEquals(bp.rootBoneNames.toSet(), roots,
                "${file.nameWithoutExtension}: root bone names mismatch")
        }
    }

    @Test
    @Order(14)
    fun `depth-first traversal visits all bones`() {
        allBbmodelFiles().take(5).forEach { file ->
            val model = parseModel(file)
            val raw = ModelGenerator.generateRaw(model)
            val bp = raw.blueprint
            val visited = mutableSetOf<String>()
            bp.traverseDepthFirst { bone, _ -> visited += bone.name }
            assertEquals(bp.bones.keys, visited,
                "${file.nameWithoutExtension}: traversal missed some bones")
        }
    }

    @Test
    @Order(15)
    fun `summary of all models`() {
        println("\n=== MODEL ENGINE TEST SUMMARY ===")
        println("%-25s %6s %6s %6s %6s".format("Model", "Bones", "Elems", "Texs", "Anims"))
        println("-".repeat(55))

        var totalBones = 0
        var totalElements = 0
        var totalTextures = 0
        var totalAnimations = 0

        allBbmodelFiles().forEach { file ->
            val model = parseModel(file)
            val raw = ModelGenerator.generateRaw(model)
            val bp = raw.blueprint
            val elements = raw.boneModels.values.sumOf { it.elements.size }

            totalBones += bp.bones.size
            totalElements += elements
            totalTextures += raw.textureBytes.size
            totalAnimations += bp.animations.size

            println("%-25s %6d %6d %6d %6d".format(
                file.nameWithoutExtension, bp.bones.size, elements, raw.textureBytes.size, bp.animations.size))
        }

        println("-".repeat(55))
        println("%-25s %6d %6d %6d %6d".format("TOTAL", totalBones, totalElements, totalTextures, totalAnimations))
        println("Total models: ${allBbmodelFiles().size}")
        println("Total assigned IDs: ${ModelIdRegistry.all().size}")
    }

    private fun ensureParsed() {
        if (parsedModels.isEmpty()) {
            allBbmodelFiles().forEach { file ->
                parsedModels[file.nameWithoutExtension] = parseModel(file)
            }
        }
    }

    private fun readZipEntries(bytes: ByteArray): List<String> {
        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries += entry.name
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun isPowerOf2(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0
}
