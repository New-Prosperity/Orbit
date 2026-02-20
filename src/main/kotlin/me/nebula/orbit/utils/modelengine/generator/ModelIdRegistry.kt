package me.nebula.orbit.utils.modelengine.generator

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object ModelIdRegistry {

    private val assignments = ConcurrentHashMap<String, Int>()
    private val nextId = AtomicInteger(1)
    private var persistFile: File? = null

    fun init(file: File) {
        persistFile = file
        if (file.exists()) {
            file.readLines().forEach { line ->
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

    fun assignId(modelName: String, boneName: String): Int {
        val key = "$modelName:$boneName"
        var isNew = false
        val id = assignments.computeIfAbsent(key) {
            isNew = true
            nextId.getAndIncrement()
        }
        if (isNew) save()
        return id
    }

    fun getId(modelName: String, boneName: String): Int? =
        assignments["$modelName:$boneName"]

    fun all(): Map<String, Int> = assignments.toMap()

    fun clear() {
        assignments.clear()
        nextId.set(1)
        save()
    }

    private fun save() {
        val file = persistFile ?: return
        file.parentFile?.mkdirs()
        val content = assignments.entries.sortedBy { it.value }.joinToString("\n") { "${it.key}=${it.value}" }
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(content, Charsets.UTF_8)
        temp.renameTo(file)
    }
}
