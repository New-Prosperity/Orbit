package me.nebula.orbit.translation

import me.nebula.ether.utils.translation.TranslationRegistry

object OrbitTranslations {

    fun register(translations: TranslationRegistry) {
        translations.putAll("en", utility() + game() + cosmetic() + host())
    }

    private fun utility(): Map<String, String> = mapOf(
        "orbit.util.achievement.unlocked" to "Achievement Unlocked!",
        "orbit.util.compass.display" to "<direction> - <distance>m",
        "orbit.util.instance_pool.released" to "Instance released",
        "orbit.util.leaderboard.entry" to "#<rank> <name> - <score>",
        "orbit.util.match_result.draw" to "DRAW!",
        "orbit.util.match_result.draw_title" to "DRAW",
        "orbit.util.match_result.winner" to "WINNER: <name>",
        "orbit.util.match_result.winner_subtitle" to "Winner: <name>",
        "orbit.util.match_result.mvp" to "MVP: <name>",
        "orbit.util.match_result.stats" to "Stats:",
        "orbit.util.match_result.duration" to "Duration: <time>",
        "orbit.util.pagination.header" to "Page <page>/<total>",
        "orbit.util.pagination.navigate" to "Use /page <number> to navigate",
        "orbit.util.timer.display" to "<seconds>s",
        "orbit.util.world.unloading" to "World unloading",
        "orbit.util.world.deleted" to "World deleted",
    )

    private fun game(): Map<String, String> = mapOf(
        "orbit.game.hoplite.elimination" to "<red><victim> <gray>was eliminated by <gold><killer>",
    )

    private fun cosmetic(): Map<String, String> = mapOf(
        "orbit.cosmetic.menu.title" to "Cosmetics",
        "orbit.cosmetic.category.armor_skin" to "Armor Skins",
        "orbit.cosmetic.category.kill_effect" to "Kill Effects",
        "orbit.cosmetic.category.trail" to "Trails",
        "orbit.cosmetic.category.win_effect" to "Win Effects",
        "orbit.cosmetic.category.projectile_trail" to "Projectile Trails",
        "orbit.cosmetic.category.companion" to "Companions",
        "orbit.cosmetic.category.spawn_effect" to "Spawn Effects",
        "orbit.cosmetic.category.death_effect" to "Death Effects",
        "orbit.cosmetic.category.aura" to "Auras",
        "orbit.cosmetic.category.elimination_message" to "Elimination Messages",
        "orbit.cosmetic.category.pet" to "Pets",
        "orbit.cosmetic.category.join_quit_message" to "Join/Quit Messages",
        "orbit.cosmetic.category.gadget" to "Gadgets",
        "orbit.cosmetic.category.gravestone" to "Gravestones",
        "orbit.cosmetic.category.mount" to "Mounts",

        "orbit.cosmetic.rarity.common" to "Common",
        "orbit.cosmetic.rarity.rare" to "Rare",
        "orbit.cosmetic.rarity.epic" to "Epic",
        "orbit.cosmetic.rarity.legendary" to "Legendary",

        "orbit.cosmetic.status.owned" to "Owned",
        "orbit.cosmetic.status.equipped" to "Equipped",
        "orbit.cosmetic.status.locked" to "Locked",
        "orbit.cosmetic.action.equip" to "Click to equip",
        "orbit.cosmetic.action.unequip" to "Click to unequip",
        "orbit.cosmetic.level" to "Level <level>/<max>",

        "orbit.cosmetic.display.label" to "Cosmetic Display",
        "orbit.cosmetic.display.full" to "Full",
        "orbit.cosmetic.display.full.description" to "See all player cosmetics",
        "orbit.cosmetic.display.reduced" to "Reduced",
        "orbit.cosmetic.display.reduced.description" to "Particles only, no models",
        "orbit.cosmetic.display.none" to "None",
        "orbit.cosmetic.display.none.description" to "Hide all other player cosmetics",
        "orbit.cosmetic.display.changed" to "Cosmetic display set to <mode>",

        "orbit.cosmetic.armor_knight.name" to "Knight Armor",
        "orbit.cosmetic.armor_knight.description" to "A noble knight's enchanted armor set",

        "orbit.cosmetic.kill_flame_burst.name" to "Flame Burst",
        "orbit.cosmetic.kill_flame_burst.description" to "Erupts flames at the defeat location",
        "orbit.cosmetic.kill_heart_explosion.name" to "Heart Explosion",
        "orbit.cosmetic.kill_heart_explosion.description" to "A burst of hearts upon elimination",

        "orbit.cosmetic.trail_flame.name" to "Flame Trail",
        "orbit.cosmetic.trail_flame.description" to "Leave a trail of fire behind you",
        "orbit.cosmetic.trail_soul.name" to "Soul Trail",
        "orbit.cosmetic.trail_soul.description" to "Leave eerie soul flames in your wake",
        "orbit.cosmetic.trail_enchant.name" to "Enchant Trail",
        "orbit.cosmetic.trail_enchant.description" to "Mystical enchantment glyphs follow you",

        "orbit.cosmetic.win_firework_helix.name" to "Firework Helix",
        "orbit.cosmetic.win_firework_helix.description" to "A spiraling firework celebration",
        "orbit.cosmetic.win_totem.name" to "Totem Burst",
        "orbit.cosmetic.win_totem.description" to "An explosive totem of undying effect",

        "orbit.cosmetic.projectile_flame.name" to "Flame Arrow",
        "orbit.cosmetic.projectile_flame.description" to "Your arrows leave a fiery trail",
        "orbit.cosmetic.projectile_dragon.name" to "Dragon Breath Arrow",
        "orbit.cosmetic.projectile_dragon.description" to "Your arrows leave dragon breath particles",

        "orbit.cosmetic.companion_mini_dragon.name" to "Mini Dragon",
        "orbit.cosmetic.companion_mini_dragon.description" to "A tiny dragon companion that follows you",

        "orbit.cosmetic.spawn_lightning.name" to "Lightning Entrance",
        "orbit.cosmetic.spawn_lightning.description" to "Arrive with a crackling electric helix",

        "orbit.cosmetic.death_soul_release.name" to "Soul Release",
        "orbit.cosmetic.death_soul_release.description" to "Release wandering souls upon death",

        "orbit.cosmetic.aura_flame.name" to "Flame Aura",
        "orbit.cosmetic.aura_flame.description" to "Ambient flames surround you",

        "orbit.cosmetic.elim_msg_royal.name" to "Royal Decree",
        "orbit.cosmetic.elim_msg_royal.description" to "A regal elimination message",
        "orbit.cosmetic.elim.royal" to "<gold>\u2694 <killer> <yellow>has royally defeated <gold><victim> <yellow>\u2694",

        "orbit.cosmetic.pet_wolf.name" to "Loyal Wolf",
        "orbit.cosmetic.pet_wolf.description" to "A faithful wolf that follows you around",
        "orbit.cosmetic.pet_fox.name" to "Sneaky Fox",
        "orbit.cosmetic.pet_fox.description" to "A cunning fox that trots alongside you",

        "orbit.cosmetic.join_quit_royal.name" to "Royal Arrival",
        "orbit.cosmetic.join_quit_royal.description" to "Announce your presence like royalty",
        "orbit.cosmetic.join_quit_shadow.name" to "Shadow Passage",
        "orbit.cosmetic.join_quit_shadow.description" to "Arrive and depart from the shadows",
        "orbit.cosmetic.join.royal" to "<gold>\u2726 <yellow><player> <gold>has graced the realm with their presence \u2726",
        "orbit.cosmetic.quit.royal" to "<gold>\u2726 <yellow><player> <gold>has departed the realm \u2726",
        "orbit.cosmetic.join.shadow" to "<dark_gray>\u2620 <gray><player> <dark_gray>emerged from the shadows \u2620",
        "orbit.cosmetic.quit.shadow" to "<dark_gray>\u2620 <gray><player> <dark_gray>vanished into the shadows \u2620",

        "orbit.cosmetic.gadget_firework_launcher.name" to "Firework Launcher",
        "orbit.cosmetic.gadget_firework_launcher.description" to "Launch yourself skyward with a burst of fireworks",
        "orbit.cosmetic.gadget_paint_blaster.name" to "Paint Blaster",
        "orbit.cosmetic.gadget_paint_blaster.description" to "Blast a sphere of colorful particles around you",
        "orbit.cosmetic.gadget_grappling_hook.name" to "Grappling Hook",
        "orbit.cosmetic.gadget_grappling_hook.description" to "Propel yourself in the direction you're looking",

        "orbit.cosmetic.gravestone_cross.name" to "Stone Cross",
        "orbit.cosmetic.gravestone_cross.description" to "A solemn cross marks your fall",
        "orbit.cosmetic.gravestone_angel.name" to "Guardian Angel",
        "orbit.cosmetic.gravestone_angel.description" to "An angelic statue watches over your resting place",

        "orbit.cosmetic.mount_horse.name" to "War Horse",
        "orbit.cosmetic.mount_horse.description" to "A trusty steed to ride across the battlefield",
        "orbit.cosmetic.mount_dragon.name" to "Storm Dragon",
        "orbit.cosmetic.mount_dragon.description" to "A fearsome dragon mount that commands respect",
    )

    private fun host(): Map<String, String> = mapOf(
        "orbit.host.menu.title" to "Host a Game",
        "orbit.host.gamemode.name" to "<gold><gamemode>",
        "orbit.host.gamemode.players" to "<gray>Max Players: <white><max>",
        "orbit.host.tickets.count" to "<gray>Tickets: <yellow><count>",
        "orbit.host.map.title" to "Select Map",
        "orbit.host.map.name" to "<green><map>",
        "orbit.host.back" to "<gray>Back",
        "orbit.host.confirm.title" to "Confirm Host",
        "orbit.host.confirm.accept" to "<green>Confirm",
        "orbit.host.confirm.cancel" to "<red>Cancel",
        "orbit.host.confirm.gamemode" to "<gray>Mode: <white><gamemode>",
        "orbit.host.confirm.map" to "<gray>Map: <white><map>",
        "orbit.host.confirm.cost" to "<gray>Cost: <yellow>1 ticket",
        "orbit.host.error.no_tickets" to "<red>You don't have any host tickets!",
        "orbit.host.error.duplicate" to "<red>You already have a pending host request!",
        "orbit.host.error.already_pending" to "<red>Your request is already being processed!",
        "orbit.host.error.no_modes" to "<red>No game modes are available for hosting right now.",
        "orbit.host.status.requested" to "<green>Host request submitted! Provisioning your server...",
        "orbit.host.status.provisioning" to "<yellow>Your server is being provisioned...",
        "orbit.host.status.ready" to "<green>Your server is ready! Transferring...",
        "orbit.host.status.failed" to "<red>Host request failed: <reason>",
    )

}
