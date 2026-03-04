package me.nebula.orbit.translation

import me.nebula.ether.utils.translation.TranslationRegistry

object OrbitTranslations {

    fun register(translations: TranslationRegistry) {
        translations.putAll("en", mechanic() + utility() + game() + cosmetic() + host())
    }

    private fun mechanic(): Map<String, String> = mapOf(
        "orbit.mechanic.anvil.title" to "Repair & Name",
        "orbit.mechanic.armor_trim.title" to "Upgrade Gear",
        "orbit.mechanic.barrel.title" to "Barrel",
        "orbit.mechanic.blast_furnace.title" to "Blast Furnace",
        "orbit.mechanic.brewing_stand.title" to "Brewing Stand",
        "orbit.mechanic.bundle.title" to "Bundle",
        "orbit.mechanic.cartography_table.title" to "Cartography Table",
        "orbit.mechanic.chest.title" to "Chest",
        "orbit.mechanic.command_block.info" to "Command Block [<command>]",
        "orbit.mechanic.container.chest.title" to "Chest",
        "orbit.mechanic.container.dispenser.title" to "Dispenser",
        "orbit.mechanic.container.dropper.title" to "Dropper",
        "orbit.mechanic.container.furnace.title" to "Furnace",
        "orbit.mechanic.container.hopper.title" to "Item Hopper",
        "orbit.mechanic.container.shulker_box.title" to "Shulker Box",
        "orbit.mechanic.crafter.title" to "Crafter",
        "orbit.mechanic.crafting.title" to "Crafting",
        "orbit.mechanic.double_chest.title" to "Large Chest",
        "orbit.mechanic.dropper.title" to "Dropper",
        "orbit.mechanic.enchanting.title" to "Enchant",
        "orbit.mechanic.ender_chest.title" to "Ender Chest",
        "orbit.mechanic.fletching_table.title" to "Fletching Table",
        "orbit.mechanic.grindstone.title" to "Repair & Disenchant",
        "orbit.mechanic.head_drop.name" to "<victim>'s Head",
        "orbit.mechanic.head_drop.lore" to "Killed by <killer>",
        "orbit.mechanic.hopper.title" to "Item Hopper",
        "orbit.mechanic.item_repair.title" to "Repair & Name",
        "orbit.mechanic.lectern.book_header" to "Book on lectern:",
        "orbit.mechanic.lectern.empty" to "  (empty)",
        "orbit.mechanic.loom.title" to "Loom",
        "orbit.mechanic.map.title" to "Cartography Table",
        "orbit.mechanic.recovery_compass.distance" to "<direction> - <distance>m to death point",
        "orbit.mechanic.shulker_box.title" to "Shulker Box",
        "orbit.mechanic.sign.header" to "Sign text:",
        "orbit.mechanic.sign.line" to "  Line <number>: <text>",
        "orbit.mechanic.smithing_table.title" to "Upgrade Gear",
        "orbit.mechanic.smoker.title" to "Smoker",
        "orbit.mechanic.stonecutter.title" to "Stonecutter",
        "orbit.mechanic.trading.title" to "Trade",

        "orbit.mechanic.vault.title" to "Vault",
        "orbit.mechanic.vault.already_claimed" to "You have already claimed this vault.",
        "orbit.mechanic.vault.reward" to "You received a vault reward!",
    )

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

        "orbit.util.auto_restart.warning" to "Server restarting in <time>",
        "orbit.util.auto_restart.restarting" to "Server is restarting...",
        "orbit.util.entity_cleanup.warning" to "Clearing <count> entities...",
        "orbit.util.selection_tool.pos1" to "Position 1 set to <pos>",
        "orbit.util.selection_tool.pos2" to "Position 2 set to <pos>",
        "orbit.util.command_cooldown.wait" to "Please wait <remaining>s before using this again.",
        "orbit.util.warmup.cancelled" to "Action cancelled!",
        "orbit.util.death_message.pvp" to "<victim> was slain by <killer>",
        "orbit.util.death_message.fall" to "<victim> fell from a high place",
        "orbit.util.death_message.void" to "<victim> fell out of the world",
        "orbit.util.death_message.generic" to "<victim> died",
        "orbit.util.podium.first" to "1st Place",
        "orbit.util.podium.second" to "2nd Place",
        "orbit.util.podium.third" to "3rd Place",
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

        "orbit.cosmetic.rarity.common" to "Common",
        "orbit.cosmetic.rarity.rare" to "Rare",
        "orbit.cosmetic.rarity.epic" to "Epic",
        "orbit.cosmetic.rarity.legendary" to "Legendary",

        "orbit.cosmetic.status.owned" to "Owned",
        "orbit.cosmetic.status.equipped" to "Equipped",
        "orbit.cosmetic.status.locked" to "Locked",
        "orbit.cosmetic.action.equip" to "Click to equip",
        "orbit.cosmetic.action.unequip" to "Click to unequip",

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
