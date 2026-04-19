package me.nebula.orbit.rules

object Rules {

    val PVP_ENABLED = RuleRegistry.register("pvp_enabled", default = false, scope = RuleScope.GAMEMODE)
    val DAMAGE_ENABLED = RuleRegistry.register("damage_enabled", default = true, scope = RuleScope.GAMEMODE)
    val FRIENDLY_FIRE = RuleRegistry.register("friendly_fire", default = false, scope = RuleScope.GAMEMODE)
    val DAMAGE_MULTIPLIER = RuleRegistry.register("damage_multiplier", default = 1.0, scope = RuleScope.GAMEMODE)

    val ZONE_SHRINKING = RuleRegistry.register("zone_shrinking", default = true, scope = RuleScope.GAMEMODE)
    val MOB_SPAWNING = RuleRegistry.register("mob_spawning", default = false, scope = RuleScope.GAMEMODE)
    val ITEM_DROPS = RuleRegistry.register("item_drops", default = true, scope = RuleScope.GAMEMODE)
    val NATURAL_REGEN = RuleRegistry.register("natural_regen", default = true, scope = RuleScope.GAMEMODE)
    val HUNGER_DRAIN = RuleRegistry.register("hunger_drain", default = true, scope = RuleScope.GAMEMODE)

    val XP_MULTIPLIER = RuleRegistry.register("xp_multiplier", default = 1.0, scope = RuleScope.GAMEMODE)
    val LOOT_QUANTITY_MULTIPLIER = RuleRegistry.register("loot_quantity_multiplier", default = 1.0, scope = RuleScope.GAMEMODE)

    fun preload() {}
}
