# Boss Bar

DSL for creating Adventure boss bars with MiniMessage titles.

## Key Functions

- **`bossBar(title, block)`** — creates a `BossBar` via DSL
- **`Player.showBossBar(bar)`** / **`Player.hideBossBar(bar)`** — show/hide extensions
- **`BossBar.updateTitle(title)`** — update title with MiniMessage text

## Usage

```kotlin
val bar = bossBar("<red>Dragon Fight") {
    color = BossBar.Color.RED
    overlay = BossBar.Overlay.PROGRESS
    progress = 0.75f
}

player.showBossBar(bar)

bar.progress(0.5f)
bar.updateTitle("<yellow>Half Health!")

player.hideBossBar(bar)
```

## Builder Properties

| Property | Default | Description |
|----------|---------|-------------|
| `color` | `WHITE` | Bar color |
| `overlay` | `PROGRESS` | Bar style (progress, notched) |
| `progress` | `1f` | Fill amount (0.0 to 1.0) |
