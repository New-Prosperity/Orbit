# Custom Biome DSL

DSL for defining `BiomeDefinition` instances with grouped scopes instead of flat constructor calls. Returns `BiomeDefinition` directly — plugs into `MapGenerationConfig.customBiomes` or `BiomeRegistry.register()`.

## Usage

```kotlin
val myBiome = customBiome("my_biome") {
    blocks {
        surface = Block.GRASS_BLOCK
        filler = Block.DIRT
        underwaterSurface = Block.SAND
        stone = Block.STONE
    }
    terrain {
        baseHeight = 64.0
        heightVariation = 6.0
        heightCurve = HeightCurve.LINEAR
    }
    climate {
        temperature = 0.5
        moisture = 0.5
        hasPrecipitation = true
        frozen = false
        snowLine = Int.MAX_VALUE
    }
    vegetation {
        treeDensity = 0.1
        vegetationDensity = 0.3
        trees(TreeType.OAK, TreeType.BIRCH)
    }
    visuals {
        waterColor = 0x3F76E4
        grassColor = 0x6ABE30
        foliageColor = 0x6ABE30
        grassModifier = GrassModifier.NONE
    }
    modifiers {
        caveFrequency = 1.0
        oreMultiplier = 1.0
    }
}
```

All scopes are optional — unset fields use defaults matching vanilla plains.

## Terrain Shape Presets

`TerrainShape` enum provides per-biome terrain shape presets. Use `shape()` in the `terrain {}` scope — individual values can be overridden after:

```kotlin
customBiome("my_mountains") {
    terrain {
        shape(TerrainShape.MOUNTAINOUS)
        baseHeight = 85.0  // override just the base height
    }
}
```

| Shape | Base Height | Variation | Curve | Character |
|---|---|---|---|---|
| `FLAT` | 64 | 2 | LINEAR | Near-zero variation |
| `PLAINS` | 64 | 5 | LINEAR | Gentle vanilla plains |
| `ROLLING_HILLS` | 66 | 8 | SMOOTH | Undulating smoothstep hills |
| `ROLLING` | 66 | 6 | ROLLING | Ultra-smooth double-smoothstep |
| `HIGHLANDS` | 72 | 12 | SMOOTH | Elevated smooth terrain |
| `FOOTHILLS` | 70 | 14 | SMOOTH | Moderate hills, mountain approach |
| `PLATEAUS` | 75 | 14 | TERRACE | Stepped flat-topped layers |
| `MESA` | 78 | 16 | MESA | Flat tops, sheer cliff edges |
| `MOUNTAINOUS` | 82 | 28 | AMPLIFIED | High ridged+noise mountains |
| `RIDGED` | 80 | 24 | RIDGED | Sharp ridgelines, knife edges |
| `PEAKS` | 92 | 35 | CLIFF | Extreme cliff peaks |
| `SPIRES` | 95 | 40 | RIDGED | Towering needle formations |
| `VALLEYS` | 56 | 18 | SMOOTH | Low-base deep valleys |
| `BASIN` | 50 | 10 | ROLLING | Bowl-shaped depression |
| `CANYON` | 48 | 30 | CLIFF | Deep cut gorges |
| `CLIFFS` | 70 | 22 | CLIFF | Dramatic cliff edges |
| `ERODED` | 65 | 10 | LINEAR | Worn down, low features |
| `DUNES` | 66 | 8 | ROLLING | Gentle rolling dune-like |
| `OCEAN_FLOOR` | 38 | 6 | SMOOTH | Deep underwater terrain |
| `SHELF` | 54 | 4 | SMOOTH | Shallow underwater shelf |

## Biome Presets

`BiomePresets` provides ready-made biomes:

```kotlin
BiomePresets.volcanic()        // blackstone/basalt, high elevation, no precipitation
BiomePresets.mushroomFields()  // mycelium, smooth terrain, grey water
BiomePresets.frozenWasteland() // snow/ice, amplified terrain, fully frozen
BiomePresets.lushCaves()       // moss/rooted dirt, high vegetation, teal water
BiomePresets.cherryGrove()     // smooth hills, pink-inspired colors
BiomePresets.deepDark()        // sculk/deepslate, low elevation, triple cave freq

BiomePresets.all()             // List<BiomeDefinition> of all presets
```

## Integration with Map Generator

```kotlin
val config = MapGenerationConfig(
    customBiomes = BiomePresets.all() + listOf(myBiome),
)
val map = BattleRoyaleMapGenerator.generate(config)
```

Custom biomes are registered via `BiomeRegistry.register()` then `BiomeRegistry.registerMinestomBiomes()` — they appear in F3 as `nebula:<id>`.
