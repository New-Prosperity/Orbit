package me.nebula.orbit.utils.modelengine.generator

import me.nebula.ether.utils.resource.ResourceManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object ModelIdRegistry {

    private val assignments = ConcurrentHashMap<String, Int>()
    private val nextId = AtomicInteger(1)
    private lateinit var resources: ResourceManager
    private lateinit var filePath: String

    fun init(resources: ResourceManager, path: String) {
        this.resources = resources
        this.filePath = path
        if (resources.exists(path)) {
            resources.readLines(path).forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val id = parts[1].trim().toIntOrNull() ?: return@forEach
                    assignments[key] = id
                    if (id >= nextId.get()) nextId.set(id + 1)
                }
            }
        }
    }

    fun assignId(key: String): Int {
        var isNew = false
        val id = assignments.computeIfAbsent(key) {
            isNew = true
            nextId.getAndIncrement()
        }
        if (isNew) save()
        return id
    }

    fun assignId(modelName: String, boneName: String): Int =
        assignId("$modelName:$boneName")

    fun getId(key: String): Int? = assignments[key]

    fun getId(modelName: String, boneName: String): Int? =
        getId("$modelName:$boneName")

    fun all(): Map<String, Int> = assignments.toMap()

    fun clear() {
        assignments.clear()
        nextId.set(1)
        save()
    }

    private fun save() {
        val content = assignments.entries.sortedBy { it.value }.joinToString("\n") { "${it.key}=${it.value}" }
        resources.writeText(filePath, content)
    }
}
