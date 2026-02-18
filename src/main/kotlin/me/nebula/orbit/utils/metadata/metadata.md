# EntityMetadata

Extension functions for simplified entity tag access using Minestom's `Tag` API, plus a typed `EntityPropertyRegistry` system for structured properties.

## Tag Extensions

### Setters

| Function | Description |
|---|---|
| `entity.setString(key, value)` | Store a `String` tag |
| `entity.setInt(key, value)` | Store an `Int` tag |
| `entity.setLong(key, value)` | Store a `Long` tag |
| `entity.setFloat(key, value)` | Store a `Float` tag |
| `entity.setDouble(key, value)` | Store a `Double` tag |
| `entity.setBoolean(key, value)` | Store a `Boolean` tag |
| `entity.setByte(key, value)` | Store a `Byte` tag |

### Getters

| Function | Returns |
|---|---|
| `entity.getString(key)` | `String?` |
| `entity.getInt(key)` | `Int?` |
| `entity.getLong(key)` | `Long?` |
| `entity.getFloat(key)` | `Float?` |
| `entity.getDouble(key)` | `Double?` |
| `entity.getBoolean(key)` | `Boolean?` |
| `entity.getByte(key)` | `Byte?` |

### Utilities

| Function | Description |
|---|---|
| `entity.removeTag(key)` | Remove a tag by key |
| `entity.hasTag(key)` | Check if a tag exists |

### Example

```kotlin
entity.setString("team", "red")
entity.setInt("kills", 5)
entity.setBoolean("invincible", true)

val team = entity.getString("team")
val kills = entity.getInt("kills") ?: 0

entity.removeTag("kills")
```

## EntityPropertyRegistry

Typed property system with automatic type resolution. Properties are stored under the `orbit_prop_` prefix. Pre-register types for optimal lookup, or use auto-detection.

### Registering Types

```kotlin
EntityPropertyRegistry.registerType<Int>("score")
EntityPropertyRegistry.registerType<String>("role")
EntityPropertyRegistry.registerType<Boolean>("active")
```

Supported types: `String`, `Int`, `Long`, `Float`, `Double`, `Boolean`, `Byte`.

### Property Extensions

| Function | Description |
|---|---|
| `entity.setProperty<T>(key, value)` | Set a typed property |
| `entity.getProperty<T>(key)` | Get a typed property (returns `T?`) |
| `entity.removeProperty(key)` | Remove a property (clears all type tags) |
| `entity.hasProperty(key)` | Check if any property exists for key |
| `entity.propertyKeys()` | Set of registered keys present on entity |

### Registry Methods

| Method | Description |
|---|---|
| `registerType<T>(key)` | Register a key with a specific type |
| `tagFor(key)` | Get the `Tag<*>` for a registered key |
| `registeredKeys()` | All registered key names |

### Example

```kotlin
EntityPropertyRegistry.registerType<Int>("level")
EntityPropertyRegistry.registerType<String>("class")

entity.setProperty("level", 10)
entity.setProperty("class", "warrior")

val level: Int? = entity.getProperty("level")
val clazz: String? = entity.getProperty("class")

entity.hasProperty("level")
entity.removeProperty("class")

val keys = entity.propertyKeys()
```
