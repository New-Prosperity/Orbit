package me.nebula.orbit.utils.entitybuilder.file

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.Duration

internal fun JsonObject.string(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
internal fun JsonObject.string(key: String, default: String): String = string(key) ?: default
internal fun JsonObject.requireString(key: String): String =
    string(key) ?: error("Missing required string field '$key' in $this")

internal fun JsonObject.int(key: String, default: Int): Int = get(key)?.takeIf { it.isJsonPrimitive }?.asInt ?: default
internal fun JsonObject.long(key: String, default: Long): Long = get(key)?.takeIf { it.isJsonPrimitive }?.asLong ?: default
internal fun JsonObject.float(key: String, default: Float): Float = get(key)?.takeIf { it.isJsonPrimitive }?.asFloat ?: default
internal fun JsonObject.double(key: String, default: Double): Double = get(key)?.takeIf { it.isJsonPrimitive }?.asDouble ?: default
internal fun JsonObject.bool(key: String, default: Boolean): Boolean = get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: default

internal fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
internal fun JsonObject.requireObj(key: String): JsonObject =
    obj(key) ?: error("Missing required object field '$key' in $this")

internal fun JsonObject.array(key: String): JsonArray? = get(key)?.takeIf { it.isJsonArray }?.asJsonArray

internal fun JsonObject.ticksAsDuration(key: String, default: Int): Duration =
    Duration.ofMillis(int(key, default) * 50L)

internal fun JsonElement.asObjectOrError(): JsonObject =
    if (isJsonObject) asJsonObject else error("Expected JSON object, got: $this")
