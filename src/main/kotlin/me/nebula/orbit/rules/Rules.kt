package me.nebula.orbit.rules

object Rules {

    val PVP_ENABLED = RuleRegistry.register("pvp_enabled", default = false)
    val DAMAGE_ENABLED = RuleRegistry.register("damage_enabled", default = true)
    val FRIENDLY_FIRE = RuleRegistry.register("friendly_fire", default = false)
    val DAMAGE_MULTIPLIER = RuleRegistry.register("damage_multiplier", default = 1.0)

    val ZONE_SHRINKING = RuleRegistry.register("zone_shrinking", default = true)
    val MOB_SPAWNING = RuleRegistry.register("mob_spawning", default = false)
    val ITEM_DROPS = RuleRegistry.register("item_drops", default = true)
    val NATURAL_REGEN = RuleRegistry.register("natural_regen", default = true)
    val HUNGER_DRAIN = RuleRegistry.register("hunger_drain", default = true)

    val XP_MULTIPLIER = RuleRegistry.register("xp_multiplier", default = 1.0)
    val LOOT_QUANTITY_MULTIPLIER = RuleRegistry.register("loot_quantity_multiplier", default = 1.0)

    fun preload() {}
}
