# Vanilla Minecraft Features — Implementation Status

Living reference for all vanilla mechanics and their status in Orbit's VanillaModule system.

Status: `[NOT IMPLEMENTED]` `[PLANNED]` `[PARTIAL]` `[IMPLEMENTED]`

---

## Combat

| Feature | Status | Module | Notes |
|---------|--------|--------|-------|
| PvP damage | [PARTIAL] | `damage-system` | Exists in `utils/damage/`, needs migration |
| Armor damage reduction | [IMPLEMENTED] | `armor-reduction` | Vanilla formula with ARMOR + ARMOR_TOUGHNESS attributes |
| Shield blocking | [IMPLEMENTED] | `shield-blocking` | Sneak to block, axes disable for 100 ticks, directional check |
| Critical hits | [IMPLEMENTED] | `critical-hits` | 1.5x when falling + not sprinting + velocity.y < 0 |
| Attack cooldown (1.9+) | [IMPLEMENTED] | `attack-cooldown` | Reads ATTACK_SPEED attribute, scales damage by progress |
| Sweep attack | [IMPLEMENTED] | `sweep-attack` | AoE damage to nearby entities when on ground, particles |
| Knockback | [PARTIAL] | `knockback` | Exists in `utils/knockback/`, needs migration |
| Combat tagging | [PARTIAL] | `combat-tag` | Exists in `utils/combatlog/`, needs migration |

## Projectiles

| Feature | Status | Module | Notes |
|---------|--------|--------|-------|
| Arrows (bow/crossbow) | [IMPLEMENTED] | `projectiles` | Bow charging, arrow damage scales with charge, consumes arrows |
| Tridents | [NOT IMPLEMENTED] | `projectiles` | |
| Snowballs | [IMPLEMENTED] | `projectiles` | Knockback on hit, consume on throw |
| Ender pearls | [IMPLEMENTED] | `projectiles` | Teleport + fall damage on land |
| Eggs | [IMPLEMENTED] | `projectiles` | Throwable, knockback on hit |
| Fire charges | [IMPLEMENTED] | `projectiles` | Throwable, sets fire on block impact, ignites entities |
| Fishing rod | [NOT IMPLEMENTED] | `fishing` | |
| Potions (splash/lingering) | [NOT IMPLEMENTED] | `potion-effects` | |

## Player Mechanics

| Feature | Status | Module | Notes |
|---------|--------|--------|-------|
| Food / hunger system | [IMPLEMENTED] | `hunger` | Exhaustion, saturation, starvation |
| Natural health regeneration | [IMPLEMENTED] | `natural-regen` | Fast (food>=18) and slow (food==20) regen |
| Fall damage | [IMPLEMENTED] | `fall-damage` | Configurable multiplier and minimum distance |
| Drowning | [IMPLEMENTED] | `drowning` | Air supply depletion, configurable rates |
| Fire / lava damage | [IMPLEMENTED] | `fire-damage` | Fire ticks, lava damage, water extinguish |
| Void damage | [IMPLEMENTED] | `void-damage` | Configurable threshold, damage, tick rate |
| Suffocation | [IMPLEMENTED] | `suffocation` | Head-in-solid-block check with cached position |
| Swimming physics | [IMPLEMENTED] | `swimming` | Water/lava aerodynamics (reduced gravity, increased drag) |
| Ladder climbing | [IMPLEMENTED] | `ladder-climbing` | Climb ladders, vines, scaffolding; sneak to hold position |
| Freezing (powder snow) | [NOT IMPLEMENTED] | | |
| Bed respawn point | [IMPLEMENTED] | `bed-respawn` | Right-click bed to set spawn, respawn at bed |
| Block pick (middle click) | [IMPLEMENTED] | `block-pick` | PlayerPickBlockEvent |
| Totem of undying | [IMPLEMENTED] | `totem-of-undying` | Prevents lethal damage, restores 1 HP, extinguishes fire, plays animation |

## Block Mechanics

| Feature | Status | Module | Notes |
|---------|--------|--------|-------|
| TNT / explosions | [IMPLEMENTED] | `tnt-explosions` | Ray-cast explosion, chain TNT, entity knockback |
| Fire spread | [IMPLEMENTED] | `fire-spread` | Tick-based spreading to flammable blocks, burn-out timer |
| Gravity blocks (sand/gravel) | [IMPLEMENTED] | `gravity-blocks` | FallingBlock entity, chain column fall |
| Water flow | [IMPLEMENTED] | `water-flow` | Level-based spreading, infinite source, draining |
| Lava flow | [IMPLEMENTED] | `lava-flow` | LiquidFlowEngine with maxLevel=3, tickRate=30, no infinite source |
| Crop growth | [IMPLEMENTED] | `crop-growth` | Random tick simulation, grows wheat/carrots/potatoes/beetroots/nether wart |
| Farmland (hoeing) | [IMPLEMENTED] | `farmland` | Hoe dirt/grass into farmland |
| Bone meal | [IMPLEMENTED] | `bone-meal` | Grows crops, saplings (simple tree gen), spreads grass |
| Random ticks | [NOT IMPLEMENTED] | | Block updates system |
| Block updates (neighbor) | [NOT IMPLEMENTED] | | |
| Copper oxidation | [NOT IMPLEMENTED] | | |
| Concrete powder solidification | [IMPLEMENTED] | `gravity-blocks` | Solidifies on placement adjacent to water + when falling into water |
| Block drops / loot | [IMPLEMENTED] | `block-drops` | Tool requirements, multi-drops for ores |
| Door interaction | [IMPLEMENTED] | `door-interaction` | Open/close doors, trapdoors, fence gates (not iron) |
| Slime block bouncing | [IMPLEMENTED] | `slime-block` | Bounce on landing, velocity preserved |

## Containers

| Feature | Status | Module | Notes |
|---------|--------|--------|-------|
| Crafting table (3x3) | [IMPLEMENTED] | `crafting` | 50+ shaped/shapeless recipes, pattern matching |
| Inventory crafting (2x2) | [NOT IMPLEMENTED] | `crafting` | |
| Furnace / smelting | [IMPLEMENTED] | `furnace` | 30+ smelting recipes, fuel system, tick-based cooking |
| Blast furnace | [IMPLEMENTED] | `furnace` | Shares furnace module |
| Smoker | [IMPLEMENTED] | `furnace` | Shares furnace module |
| Anvil | [IMPLEMENTED] | `anvil` | Material repair (25%/unit), combine identical items (+12% bonus), 12% degrade chance |
| Enchanting table | [NOT IMPLEMENTED] | `enchanting` | |
| Brewing stand | [IMPLEMENTED] | `brewing-stand` | Blaze powder fuel (20 charges), 400-tick brew time, potion recipes, splash/lingering |
| Chest / barrel | [IMPLEMENTED] | `chests` | Per-block inventories, item drops on break |
| Shulker box | [IMPLEMENTED] | `shulker-box` | 3-row inventory, items drop on break |
| Hopper | [NOT IMPLEMENTED] | | Redstone category |
| Dropper / dispenser | [NOT IMPLEMENTED] | | Redstone category |
| Stonecutter | [IMPLEMENTED] | `stonecutter` | Opens stonecutter UI on interaction |
| Smithing table | [IMPLEMENTED] | `smithing-table` | Opens smithing interface on interaction |
| Campfire cooking | [IMPLEMENTED] | `campfire-cooking` | Place raw food on campfire, cooks over 600 ticks |

## Items

| Feature | Status | Module | Notes |
|---------|--------|--------|-------|
| Tool durability | [IMPLEMENTED] | `tool-durability` | MAX_DAMAGE/DAMAGE components, break on deplete | | `tool-durability` | |
| Food consumption | [IMPLEMENTED] | `food-consumption` | All vanilla food values, bowl/bottle return |
| Bucket mechanics | [IMPLEMENTED] | `buckets` | Place/collect water/lava | | | Place/collect water/lava |
| Armor equipping | [PARTIAL] | | Minestom handles equip, no damage calc |
| Shield blocking | [IMPLEMENTED] | `shield-blocking` | Sneak + shield to block, axe disables |
| Flint and steel | [IMPLEMENTED] | `flint-and-steel` | Place fire on block face, durability damage | | | Fire placement + TNT ignition |
| Lead / leash | [NOT IMPLEMENTED] | | |
| Dye usage | [NOT IMPLEMENTED] | | |

## Entities

| Feature | Status | Module | Notes |
|---------|--------|--------|-------|
| Mob AI | [PARTIAL] | | Exists in `utils/entityai/` |
| Mob spawning (natural) | [NOT IMPLEMENTED] | | |
| Mob breeding | [NOT IMPLEMENTED] | | |
| Mob drops | [NOT IMPLEMENTED] | | |
| Item entity pickup | [IMPLEMENTED] | `item-pickup` | PickupItemEvent with inventory space check |
| Item despawn + merge | [IMPLEMENTED] | `item-despawn` | 5-min despawn timer, nearby identical stack merging |
| XP orb collection | [PARTIAL] | `item-pickup` | Item pickup works, XP orb needs separate handling |
| Falling block entity | [IMPLEMENTED] | `gravity-blocks` | |
| Primed TNT entity | [IMPLEMENTED] | `tnt-explosions` | Fuse timer, chain detonation |
| Vehicle riding (boat/minecart) | [NOT IMPLEMENTED] | | |
| Villager trading | [NOT IMPLEMENTED] | | |

## World

| Feature | Status | Module | Notes |
|---------|--------|--------|-------|
| Weather (rain/thunder) | [NOT IMPLEMENTED] | `weather` | Exists in `utils/weathercontrol/` (toggle only) |
| Lightning strikes | [NOT IMPLEMENTED] | `weather` | |
| Daylight effects (mob burning) | [NOT IMPLEMENTED] | `daylight-effects` | |
| Phantom spawning | [NOT IMPLEMENTED] | `daylight-effects` | |
| Ice / snow formation | [NOT IMPLEMENTED] | | |
| Nether portals | [IMPLEMENTED] | `nether-portal` | Obsidian frame detection (2-21 wide, 3-21 tall), ignition, break propagation |

## Effects

| Feature | Status | Module | Notes |
|---------|--------|--------|-------|
| Potion effects system | [IMPLEMENTED] | `potion-effects` | Poison, Regeneration, Wither, Hunger, Instant Damage, Instant Health with amplifier scaling |
| Beacon | [NOT IMPLEMENTED] | | |
| Conduit | [NOT IMPLEMENTED] | | |
| Area of effect | [PARTIAL] | | Exists in `utils/areaeffect/` |

## Redstone

| Feature | Status | Module | Notes |
|---------|--------|--------|-------|
| Redstone wire | [NOT IMPLEMENTED] | | |
| Repeater | [NOT IMPLEMENTED] | | |
| Comparator | [NOT IMPLEMENTED] | | |
| Piston / sticky piston | [NOT IMPLEMENTED] | | |
| Hopper | [NOT IMPLEMENTED] | | |
| Observer | [NOT IMPLEMENTED] | | |
| Lever | [IMPLEMENTED] | `lever-button` | Toggle powered state on click |
| Button | [IMPLEMENTED] | `lever-button` | Temporary activation, auto-reset (wood 1.5s, stone 1s) |
| Pressure plate | [IMPLEMENTED] | `pressure-plate` | Wood=all entities, stone=players+mobs, deactivation on leave |
| Tripwire | [NOT IMPLEMENTED] | | |
| Target block | [NOT IMPLEMENTED] | | |
| Note block | [IMPLEMENTED] | `note-block` | 25 pitches, instrument from block below (20+ instruments), right-click to tune |
| Sculk sensor | [NOT IMPLEMENTED] | | |

## Miscellaneous

| Feature | Status | Module | Notes |
|---------|--------|--------|-------|
| Creative inventory browsing | [NOT IMPLEMENTED] | | |
| Loot tables (chest/entity) | [PARTIAL] | | `utils/loot/` for custom, vanilla tables not loaded |
| Advancement system | [NOT IMPLEMENTED] | | |
| Recipe book | [NOT IMPLEMENTED] | | |
| Respawn anchor | [IMPLEMENTED] | `respawn-anchor` | Glowstone charging (max 4), nether spawn set, overworld explosion (power 5) |
| Cauldron | [IMPLEMENTED] | `cauldron` | Water/lava fill from buckets, bottle fill, 3 water levels |
| Ender chest persistence | [IMPLEMENTED] | `ender-chest` | Per-player inventory, session-scoped |
| Jukebox / music discs | [IMPLEMENTED] | `jukebox` | Insert/eject discs, eject on break, supports all disc types |
| Composter | [IMPLEMENTED] | `composter` | 7 fill layers, probability-based composting, bone meal output |
