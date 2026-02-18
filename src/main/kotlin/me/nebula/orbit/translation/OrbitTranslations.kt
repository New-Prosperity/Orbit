package me.nebula.orbit.translation

import me.nebula.ether.utils.translation.TranslationRegistry

object OrbitTranslations {

    fun register(translations: TranslationRegistry) {
        translations.putAll("en", mechanic() + utility() + hub())
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

    private fun hub(): Map<String, String> = mapOf(
        "orbit.hub.scoreboard.title" to "<gradient:#7B68EE:#00CED1><bold>NEBULA</bold></gradient>",
        "orbit.hub.scoreboard.online" to "<gray>Online: <white><online>",
        "orbit.hub.scoreboard.rank" to "<gray>Rank: <rank>",
        "orbit.hub.scoreboard.server" to "<gray>Server: <white><server>",
        "orbit.hub.scoreboard.website" to "<dark_gray>play.nebula.me",
        "orbit.hub.tab.header" to "\n<gradient:#7B68EE:#00CED1><bold>NEBULA NETWORK</bold></gradient>\n",
        "orbit.hub.tab.footer" to "\n<gray>Online: <white><online> <dark_gray>| <gray>Server: <white><server>\n",
        "orbit.hub.selector.item" to "<green><bold>Server Selector",
        "orbit.hub.selector.title" to "<gradient:#7B68EE:#00CED1><bold>Server Selector</bold></gradient>",
    )
}
