# Tab List

Player extension functions for setting tab list header and footer with MiniMessage support.

## Key Functions

- **`Player.tabList { }`** — DSL-style header/footer setter
- **`Player.setTabList(header, footer)`** — direct header/footer setter

## Usage

```kotlin
player.tabList {
    header("<gold><bold>My Server")
    footer("<gray>Online: 42")
}

player.setTabList("<gold>Header", "<gray>Footer")
```

Both functions accept MiniMessage-formatted strings.
