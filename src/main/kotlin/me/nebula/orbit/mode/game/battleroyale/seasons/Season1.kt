package me.nebula.orbit.mode.game.battleroyale.seasons

import me.nebula.gravity.cosmetic.CosmeticCategory
import me.nebula.orbit.mode.config.CosmeticConfig
import me.nebula.orbit.mode.config.LobbyConfig
import me.nebula.orbit.mode.game.TimingConfig
import me.nebula.orbit.mode.game.battleroyale.DeathmatchConfig
import me.nebula.orbit.mode.game.battleroyale.GoldenHeadConfig
import me.nebula.orbit.mode.game.battleroyale.KillstreakAirdropConfig
import me.nebula.orbit.mode.game.battleroyale.Season
import me.nebula.orbit.mode.game.battleroyale.SpawnModeConfig
import me.nebula.orbit.mode.game.battleroyale.season
import me.nebula.orbit.utils.chestloot.LootRarity
import me.nebula.orbit.utils.supplydrop.SupplyDropScheduleConfig

fun season1(): Season = season(1) {
    xp("kill" to 50L, "win" to 200L, "survival" to 25L)

    starterKit {
        item(0, "minecraft:wooden_sword")
        item(1, "minecraft:wooden_pickaxe")
    }

    kit("warrior") {
        name("orbit.game.br.kit.warrior")
        icon("minecraft:iron_sword")
        unlocked()
        levels(3, xp = listOf(100, 250, 500))

        tier(1) {
            armor("leather")
            item(0, "minecraft:stone_sword")
        }
        tier(2) {
            armor("chainmail")
            item(0, "minecraft:stone_sword")
            item(7, "minecraft:golden_apple")
        }
        tier(3) {
            armor("iron")
            item(0, "minecraft:iron_sword")
            item(7, "minecraft:golden_apple", 2)
        }
    }

    kit("archer") {
        name("orbit.game.br.kit.archer")
        icon("minecraft:bow")
        unlocked()
        levels(3, xp = listOf(100, 250, 500))

        tier(1) {
            item(0, "minecraft:wooden_sword")
            item(1, "minecraft:bow")
            item(8, "minecraft:arrow", 16)
        }
        tier(2) {
            helmet("minecraft:leather_helmet")
            item(0, "minecraft:wooden_sword")
            item(1, "minecraft:bow")
            item(8, "minecraft:arrow", 24)
        }
        tier(3) {
            helmet("minecraft:leather_helmet")
            chestplate("minecraft:leather_chestplate")
            item(0, "minecraft:stone_sword")
            item(1, "minecraft:bow")
            item(8, "minecraft:arrow", 32)
        }
    }

    kit("tank") {
        name("orbit.game.br.kit.tank")
        icon("minecraft:shield")
        locked()
        levels(3, xp = listOf(150, 300, 600))

        tier(1) {
            armor("iron")
            item(0, "minecraft:wooden_sword")
            item(2, "minecraft:shield")
        }
        tier(2) {
            armor("iron")
            item(0, "minecraft:stone_sword")
            item(2, "minecraft:shield")
            item(7, "minecraft:golden_apple")
        }
        tier(3) {
            helmet("minecraft:diamond_helmet")
            chestplate("minecraft:iron_chestplate")
            leggings("minecraft:iron_leggings")
            boots("minecraft:iron_boots")
            item(0, "minecraft:iron_sword")
            item(2, "minecraft:shield")
            item(7, "minecraft:golden_apple", 2)
        }
    }

    kit("scout") {
        name("orbit.game.br.kit.scout")
        icon("minecraft:feather")
        locked()
        levels(3, xp = listOf(100, 250, 500))

        tier(1) {
            boots("minecraft:leather_boots")
            item(0, "minecraft:stone_sword")
            item(1, "minecraft:ender_pearl")
        }
        tier(2) {
            boots("minecraft:chainmail_boots")
            item(0, "minecraft:stone_sword")
            item(1, "minecraft:ender_pearl", 2)
        }
        tier(3) {
            boots("minecraft:iron_boots")
            item(0, "minecraft:iron_sword")
            item(1, "minecraft:ender_pearl", 3)
        }
    }

    kit("alchemist") {
        name("orbit.game.br.kit.alchemist")
        icon("minecraft:brewing_stand")
        locked()
        levels(3, xp = listOf(100, 250, 500))

        tier(1) {
            item(0, "minecraft:wooden_sword")
            item(1, "minecraft:splash_potion")
            item(7, "minecraft:golden_apple")
        }
        tier(2) {
            helmet("minecraft:leather_helmet")
            item(0, "minecraft:stone_sword")
            item(1, "minecraft:splash_potion", 2)
            item(7, "minecraft:golden_apple")
        }
        tier(3) {
            helmet("minecraft:leather_helmet")
            chestplate("minecraft:leather_chestplate")
            item(0, "minecraft:stone_sword")
            item(1, "minecraft:splash_potion", 3)
            item(7, "minecraft:golden_apple", 2)
        }
    }

    kit("berserker") {
        name("orbit.game.br.kit.berserker")
        icon("minecraft:netherite_sword")
        locked()
        levels(3, xp = listOf(200, 400, 800))

        tier(1) {
            item(0, "minecraft:iron_sword")
            item(7, "minecraft:golden_apple")
        }
        tier(2) {
            helmet("minecraft:leather_helmet")
            item(0, "minecraft:iron_sword")
            item(7, "minecraft:golden_apple", 2)
        }
        tier(3) {
            helmet("minecraft:chainmail_helmet")
            chestplate("minecraft:chainmail_chestplate")
            item(0, "minecraft:diamond_sword")
            item(7, "minecraft:golden_apple", 2)
        }
    }

    vote("duration") {
        name("orbit.game.br.vote.category.duration")
        icon("minecraft:clock")
        default(1)
        option("orbit.game.br.vote.duration.short", "minecraft:clock", 2700)
        option("orbit.game.br.vote.duration.normal", "minecraft:clock", 3300)
        option("orbit.game.br.vote.duration.long", "minecraft:clock", 3900)
    }

    vote("health") {
        name("orbit.game.br.vote.category.health")
        icon("minecraft:golden_apple")
        default(0)
        option("orbit.game.br.vote.health.normal", "minecraft:apple", 20)
        option("orbit.game.br.vote.health.enhanced", "minecraft:golden_apple", 30)
        option("orbit.game.br.vote.health.tank", "minecraft:enchanted_golden_apple", 40)
    }

    vote("border") {
        name("orbit.game.br.vote.category.border")
        icon("minecraft:compass")
        default(1)
        option("orbit.game.br.vote.border.fast", "minecraft:tnt", 0)
        option("orbit.game.br.vote.border.normal", "minecraft:compass", 1)
        option("orbit.game.br.vote.border.slow", "minecraft:barrier", 2)
    }

    scoreboard("orbit.game.br.scoreboard.title", 2, listOf(
        "",
        "orbit.game.br.scoreboard.phase",
        "orbit.game.br.scoreboard.alive",
        "",
        "orbit.game.br.scoreboard.kills",
        "orbit.game.br.scoreboard.deathmatch",
        "",
        "orbit.game.br.scoreboard.server",
        "",
        "orbit.game.br.scoreboard.ip",
    ))

    tabList(3, "orbit.game.br.tablist.header", "orbit.game.br.tablist.footer")

    lobby(LobbyConfig(
        gameMode = "ADVENTURE",
        protectBlocks = true,
        disableDamage = true,
        disableHunger = true,
        lockInventory = false,
        voidTeleportY = -64.0,
    ))

    timing(TimingConfig(
        countdownSeconds = 15,
        gameDurationSeconds = 3300,
        endingDurationSeconds = 10,
        gracePeriodSeconds = 30,
        minPlayers = 2,
        maxPlayers = 16,
        allowReconnect = false,
        freezeDuringCountdown = true,
        voidDeathY = -64.0,
    ))

    border(
        initialDiameter = 500.0,
        finalDiameter = 20.0,
        centerX = 0.0,
        centerZ = 0.0,
        shrinkStart = 120,
        shrinkDuration = 300,
    )

    borderPhase(startAfter = 1980, targetDiameter = 350.0, shrinkDuration = 180, damage = 1f, announceLead = 180)
    borderPhase(startAfter = 2340, targetDiameter = 200.0, shrinkDuration = 150, damage = 2f, announceLead = 120)
    borderPhase(startAfter = 2610, targetDiameter = 120.0, shrinkDuration = 90, damage = 3f, announceLead = 90)
    borderPhase(startAfter = 2820, targetDiameter = 60.0, shrinkDuration = 60, damage = 5f, announceLead = 60)
    borderPhase(startAfter = 2970, targetDiameter = 30.0, shrinkDuration = 45, damage = 8f, announceLead = 45)
    borderPhase(startAfter = 3090, targetDiameter = 10.0, shrinkDuration = 30, damage = 10f, announceLead = 30)
    borderPhase(startAfter = 3180, targetDiameter = 5.0, shrinkDuration = 30, damage = 10f, announceLead = 30)

    spawnMode(SpawnModeConfig(
        ringRadius = 80.0,
        extendedRingRadius = 200.0,
        busHeight = 150.0,
        busSpeed = 1.5,
        parachuteDurationTicks = 400,
        randomMinDistance = 20.0,
        spawnProtectionTicks = 300,
    ))

    goldenHead(GoldenHeadConfig(
        enabled = true,
        healAmount = 8f,
        absorptionHearts = 4f,
        regenDurationTicks = 100,
        regenAmplifier = 1,
    ))

    deathmatch(DeathmatchConfig(
        enabled = true,
        triggerAtPlayers = 3,
        teleportToCenter = true,
        borderDiameter = 50.0,
        borderShrinkSeconds = 60,
    ))

    cosmetics(CosmeticConfig(
        enabledCategories = setOf(
            CosmeticCategory.ARMOR_SKIN.name,
            CosmeticCategory.KILL_EFFECT.name,
            CosmeticCategory.TRAIL.name,
            CosmeticCategory.WIN_EFFECT.name,
            CosmeticCategory.PROJECTILE_TRAIL.name,
        ),
    ))

    mapPreset("perfect")
    lobbyWorld("worlds/lobby")

    registerLootTable(buildStandardChest())
    registerLootTable(buildAirdropChest())
    registerLootTable(buildKillstreakChest())

    airdropTable("br_chest_airdrop")
    killstreakTable("br_chest_killstreak")

    supplyDropSchedule(SupplyDropScheduleConfig(
        enabled = true,
        firstPhase = 2,
        dropAltitudeOffset = 90.0,
        fallSpeed = 0.6,
        announceRadius = 300.0,
        chestDurationTicks = 900,
    ))

    killstreakAirdrop(KillstreakAirdropConfig(
        enabled = true,
        milestones = mapOf(
            3 to LootRarity.RARE,
            5 to LootRarity.EPIC,
            10 to LootRarity.LEGENDARY,
        ),
    ))
}
