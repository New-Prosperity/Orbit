# ResourcePack

Resource pack manager with named packs, manual/auto sending, and first-spawn auto-delivery.

## PackConfig

```kotlin
PackConfig(
    url = "https://example.com/pack.zip",
    hash = "abc123...",
    required = true,
    prompt = Component.text("Please accept the resource pack"),
)
```

## API

| Method | Description |
|---|---|
| `ResourcePackManager.register(name, config)` | Register a named pack |
| `ResourcePackManager.unregister(name)` | Remove a pack |
| `ResourcePackManager.send(player, name)` | Send a specific pack to a player |
| `ResourcePackManager.sendAll(player)` | Send all registered packs to a player |
| `ResourcePackManager.enableAutoSend()` | Auto-send all packs on first spawn |
| `ResourcePackManager.install()` | Register spawn event listener |
| `ResourcePackManager.uninstall()` | Remove event listener |
| `ResourcePackManager.clear()` | Remove all packs and uninstall |

## Example

```kotlin
ResourcePackManager.register("main", PackConfig(
    url = "https://cdn.example.com/textures.zip",
    hash = "a1b2c3d4e5f6",
    required = true,
    prompt = Component.text("Download the server texture pack?"),
))

ResourcePackManager.enableAutoSend()

ResourcePackManager.send(player, "main")
```
