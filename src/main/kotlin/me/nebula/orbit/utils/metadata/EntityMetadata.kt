package me.nebula.orbit.utils.metadata

import net.minestom.server.entity.Entity
import net.minestom.server.tag.Tag
import java.util.concurrent.ConcurrentHashMap

fun Entity.setString(key: String, value: String) =
    setTag(Tag.String(key), value)

fun Entity.getString(key: String): String? =
    getTag(Tag.String(key))

fun Entity.setInt(key: String, value: Int) =
    setTag(Tag.Integer(key), value)

fun Entity.getInt(key: String): Int? =
    getTag(Tag.Integer(key))

fun Entity.setLong(key: String, value: Long) =
    setTag(Tag.Long(key), value)

fun Entity.getLong(key: String): Long? =
    getTag(Tag.Long(key))

fun Entity.setFloat(key: String, value: Float) =
    setTag(Tag.Float(key), value)

fun Entity.getFloat(key: String): Float? =
    getTag(Tag.Float(key))

fun Entity.setDouble(key: String, value: Double) =
    setTag(Tag.Double(key), value)

fun Entity.getDouble(key: String): Double? =
    getTag(Tag.Double(key))

fun Entity.setBoolean(key: String, value: Boolean) =
    setTag(Tag.Boolean(key), value)

fun Entity.getBoolean(key: String): Boolean? =
    getTag(Tag.Boolean(key))

fun Entity.setByte(key: String, value: Byte) =
    setTag(Tag.Byte(key), value)

fun Entity.getByte(key: String): Byte? =
    getTag(Tag.Byte(key))

fun Entity.removeTag(key: String) {
    removeTag(Tag.String(key))
}

fun Entity.hasTag(key: String): Boolean =
    hasTag(Tag.String(key))

@PublishedApi internal const val PROPERTY_PREFIX = "orbit_prop_"

object EntityPropertyRegistry {

    @PublishedApi internal val registeredTypes = ConcurrentHashMap<String, Tag<*>>()

    inline fun <reified T : Any> registerType(key: String) {
        val tag = when (T::class) {
            String::class -> Tag.String("$PROPERTY_PREFIX$key")
            Int::class -> Tag.Integer("$PROPERTY_PREFIX$key")
            Long::class -> Tag.Long("$PROPERTY_PREFIX$key")
            Float::class -> Tag.Float("$PROPERTY_PREFIX$key")
            Double::class -> Tag.Double("$PROPERTY_PREFIX$key")
            Boolean::class -> Tag.Boolean("$PROPERTY_PREFIX$key")
            Byte::class -> Tag.Byte("$PROPERTY_PREFIX$key")
            else -> throw IllegalArgumentException("Unsupported property type: ${T::class}")
        }
        registeredTypes[key] = tag
    }

    fun tagFor(key: String): Tag<*>? = registeredTypes[key]

    fun registeredKeys(): Set<String> = registeredTypes.keys.toSet()
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> Entity.setProperty(key: String, value: T) {
    val tag = EntityPropertyRegistry.tagFor(key)
    if (tag != null) {
        setTag(tag as Tag<T>, value)
        return
    }
    when (value) {
        is String -> setTag(Tag.String("$PROPERTY_PREFIX$key"), value)
        is Int -> setTag(Tag.Integer("$PROPERTY_PREFIX$key"), value)
        is Long -> setTag(Tag.Long("$PROPERTY_PREFIX$key"), value)
        is Float -> setTag(Tag.Float("$PROPERTY_PREFIX$key"), value)
        is Double -> setTag(Tag.Double("$PROPERTY_PREFIX$key"), value)
        is Boolean -> setTag(Tag.Boolean("$PROPERTY_PREFIX$key"), value)
        is Byte -> setTag(Tag.Byte("$PROPERTY_PREFIX$key"), value)
        else -> setTag(Tag.String("$PROPERTY_PREFIX$key"), value.toString())
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> Entity.getProperty(key: String): T? {
    val tag = EntityPropertyRegistry.tagFor(key)
    if (tag != null) return getTag(tag) as? T
    return (getTag(Tag.String("$PROPERTY_PREFIX$key")) as? T)
        ?: (getTag(Tag.Integer("$PROPERTY_PREFIX$key")) as? T)
        ?: (getTag(Tag.Long("$PROPERTY_PREFIX$key")) as? T)
        ?: (getTag(Tag.Float("$PROPERTY_PREFIX$key")) as? T)
        ?: (getTag(Tag.Double("$PROPERTY_PREFIX$key")) as? T)
        ?: (getTag(Tag.Boolean("$PROPERTY_PREFIX$key")) as? T)
        ?: (getTag(Tag.Byte("$PROPERTY_PREFIX$key")) as? T)
}

fun Entity.removeProperty(key: String) {
    removeTag(Tag.String("$PROPERTY_PREFIX$key"))
    removeTag(Tag.Integer("$PROPERTY_PREFIX$key"))
    removeTag(Tag.Long("$PROPERTY_PREFIX$key"))
    removeTag(Tag.Float("$PROPERTY_PREFIX$key"))
    removeTag(Tag.Double("$PROPERTY_PREFIX$key"))
    removeTag(Tag.Boolean("$PROPERTY_PREFIX$key"))
    removeTag(Tag.Byte("$PROPERTY_PREFIX$key"))
}

fun Entity.hasProperty(key: String): Boolean {
    val tag = EntityPropertyRegistry.tagFor(key)
    if (tag != null) return hasTag(tag)
    return hasTag(Tag.String("$PROPERTY_PREFIX$key"))
        || hasTag(Tag.Integer("$PROPERTY_PREFIX$key"))
        || hasTag(Tag.Long("$PROPERTY_PREFIX$key"))
        || hasTag(Tag.Float("$PROPERTY_PREFIX$key"))
        || hasTag(Tag.Double("$PROPERTY_PREFIX$key"))
        || hasTag(Tag.Boolean("$PROPERTY_PREFIX$key"))
        || hasTag(Tag.Byte("$PROPERTY_PREFIX$key"))
}

fun Entity.propertyKeys(): Set<String> =
    EntityPropertyRegistry.registeredKeys().filter { hasProperty(it) }.toSet()
