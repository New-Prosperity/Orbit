# Particle Effect Catalog

Audit of every particle effect emitted today, classified by the cheapest backend that serves it. Used as the gate for deciding whether a shader-particle layer is justified.

---

## Existing infrastructure

- **`utils/particle/Particle.kt`** — `ParticleEffect`, `ParticleShape` (Circle/Sphere/Helix/Line/Cuboid), `ParticleShapeRenderer`, `spawnParticle*` extensions. Pure vanilla `ParticlePacket` backend.
- **`utils/particle/AnimatedParticle.kt`** — pre-computed packet arrays, entity-attached, tickable. Still vanilla.
- **`utils/hologram/Hologram.kt`** — TextDisplay floaters, multi-line, per-player variants.
- **`utils/damage/Damage.kt`** — `DamageIndicator` already emits TextDisplay-based floating damage numbers. **No particle migration needed for damage UI.**
- **`utils/leaderboard/LeaderboardTextDisplay.kt`** — TextDisplay for persistent rankings.
- **`utils/hud/shader/`**, **`utils/screen/shader/`** — custom core-shader pipelines shipped via resource pack. Proves team has shader authoring capability.
- **`utils/npc/`** — packet-only fake entity pattern (no server `Entity`). Template for packet-only display entities.

Cosmetic definitions (`Gravity/cosmetic/CosmeticDefinitions.kt`) reference particles by string name → `Particle.fromKey("minecraft:...")`. Current set uses **only vanilla preset types**: `flame`, `heart`, `damage_indicator`, `soul`, `soul_fire_flame`, `enchant`, `firework`, `totem_of_undying`, `end_rod`, `dragon_breath`, `electric_spark`, `enchanted_hit`, `item_snowball`, `composter`, `crit`, `sweep_attack`, `bubble`, `explosion`, `cloud`. **Zero brand-colored DUST, zero custom shapes.**

---

## Call-site catalog

| # | Effect | Site | Current backend | Visibility | Density | Recommended |
|---|---|---|---|---|---|---|
| 1 | Kill cosmetic explosion | `CosmeticApplier.playKillEffect` | preset sphere + weapon spark | per-viewer | low (one-shot) | **Vanilla stays**. Expose DUST RGB for rarity-colored variants as future-expansion, not urgent. |
| 2 | Win cosmetic helix | `CosmeticApplier.playWinEffect` | preset helix | per-viewer | medium | Vanilla stays. |
| 3 | Walking trail | `CosmeticApplier.spawnTrailParticle` | preset, per-step | per-viewer | **hot path — every N ticks per owner** | Vanilla stays for cheap cosmetics. RGB DUST for premium ones. |
| 4 | Projectile trail | `CosmeticApplier.spawnProjectileTrailParticle` | preset | per-viewer | hot (arrows/tridents) | Vanilla stays. |
| 5 | Spawn helix | `CosmeticApplier.playSpawnEffect` | preset helix | per-viewer | one-shot | Vanilla stays. |
| 6 | Death sphere | `CosmeticApplier.playDeathEffect` | preset sphere | per-viewer | one-shot | Vanilla stays. |
| 7 | Aura ambient | `CosmeticApplier.spawnAuraParticles` (`AuraManager`) | preset, per tick | per-viewer, filtered | **hottest path** (every 5 ticks per wearer × N wearers) | Vanilla stays. RGB DUST for rank-colored auras. Staggered already (Phase B). |
| 8 | Gadget effects | `CosmeticApplier.spawnGadgetShape`/`spawnGadgetParticle` | preset | per-viewer | one-shot | Vanilla stays. |
| 9 | BR bus route preview | `SpawnMode.executeBus` | `END_ROD` line | per-viewer | one-shot per-bus-deploy | Vanilla stays — END_ROD is ideal. |
| 10 | Statue podium indicator | `StatueManager` (#1/#2/#3 flame/end_rod/composter circles) | preset circle | instance-wide | persistent tick-based | **Replace with Hologram ground decal or floating TextDisplay icon.** The "circle of particles" is a worse solution than a static glowing label. |
| 11 | Bonemeal feedback | `BoneMealModule` | `HAPPY_VILLAGER` | instance-wide | one-shot per-use | Vanilla stays. Exact match to vanilla behavior. |
| 12 | Sweep attack hit | `SweepAttackModule` | `SWEEP_ATTACK` | instance-wide | per-hit | Vanilla stays. Exact match to vanilla. |
| 13 | Jump pad activation | `JumpPad` | preset + custom | instance-wide | per-activation | Vanilla stays. |
| 14 | Supply drop trail | `SupplyDrop` | `FLAME` falling trail + `EXPLOSION` on landing | instance-wide | medium (descent duration) | Vanilla stays. Could replace with a packet-based `ItemDisplay` + tracer to get rarity coloring + custom sprite. |
| 15 | WorldEdit selection border | `SelectionRenderer` | `FLAME` at corners | per-viewer | hot (tick loop) | Vanilla stays. Temporary in-world UI. |
| 16 | `utils/trail/Trail.kt` — ambient player trail | `Trail` | preset | per-viewer | hot | Vanilla stays. |
| 17 | `AnimatedParticle` (entity-attached circle/helix/sphere) | generic | pre-computed packets | instance-wide | tick loop | Vanilla stays. Already optimized. |

---

## Verdict

### What vanilla handles fine (stays put)

Items 1–9, 11–17. That's **~16 effects, all physics / density / short-lived / UI-temporary**. Vanilla particle packets are the correct tool. Migration would be a regression in every case.

### What should move (but not to a shader backend)

| Effect | Current | Should be |
|---|---|---|
| Statue podium `#1`/`#2`/`#3` indicator (10) | particle circle | **`utils/hologram/` ground label** — "1st", "2nd", "3rd" with gold/silver/bronze color, glowing, one per podium. Zero particles. |

### What vanilla *can't* do but we're not asking it to do

None. The cosmetic definitions use preset particle types on purpose; brand-colored auras/trails/death effects don't currently exist in the registered set. Nothing in the codebase today is blocked on "we need this effect but vanilla can't."

### Future-expansion doors (cheap, vanilla-based)

These would unlock *more* cosmetics without any shader work:

1. **Expose RGB DUST in cosmetic data map.** Add `particle: "dust"`, `color: "#ff6b35"`, `scale: "1.2"` support in `CosmeticDefinitions`. Requires ~20 lines in `CosmeticApplier.resolveParticle` to read the color fields and build a `DustParticleData`. Unlocks brand-colored trails/auras instantly.
2. **Expose DUST_COLOR_TRANSITION.** Same pattern, two colors + scale. Unlocks "fading" trail effects.
3. **Expose ARGB COLOR particle.** For spell-style effects.
4. **Item-display particle backend.** Packet-only fake `ItemDisplay` (like `utils/npc/` for NPCs) as a *separate* registered effect kind, for the 2–3 premium legendary-drop glows and ability symbols we may add later. This is the only actual "new backend" — but it's reusing `utils/npc/` plumbing, not a new shader pipeline.

---

## Shader-backend gate

**Recommended: do NOT build a shader-particle layer.**

Reasoning:

1. **No current effect** in the 17-item catalog is served worse by vanilla than it would be by a shader backend.
2. The two "new capabilities" we'd want (brand color, custom sprite) are served by:
   - Vanilla `DUST`/`DUST_COLOR_TRANSITION` with RGB — **zero shader work**
   - Packet-only `ItemDisplay` — **reuses `utils/npc/` pattern, zero new shader**
3. A text-shader-hijack particle system would be ~500 lines of GLSL + encoding + decoder + pack-ship maintenance, to serve ~0 unmet needs today.
4. Adding a shader-backed layer is a one-way door: once cosmetics depend on it, we can't ship to clients without the full pack.
5. `DamageIndicator` proves the team's instinct was already right: floating UI goes through TextDisplay, not particles.

**Revisit this decision when**: we see a concrete effect designed that *cannot* be served by vanilla DUST + packet `ItemDisplay` together. Until then, the "shader particle library" is yak-shaving.

---

## Action items (in priority order)

1. **Do**: Replace item 10 (statue podium circles) with a `Hologram` ground label using tier-colored text. One file change (`StatueManager.kt`), ~20 line delta. Delivers immediate UX improvement.
2. **Do**: Add `dust` + `dust_transition` handling to `CosmeticApplier.resolveParticle`. Updates cosmetic data map to accept `color` / `toColor` / `scale`. Enables rank-colored cosmetics without any shader work. ~50 line delta.
3. **Defer**: Packet-only `ItemDisplay` particle backend. Build *only* when the first cosmetic design requires it (rune circles, legendary persistent glow, branded ability VFX).
4. **Don't build**: Shader-hijack particle library, at least not now.
