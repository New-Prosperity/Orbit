package me.nebula.orbit.progression

import me.nebula.gravity.battlepass.BattlePassData
import me.nebula.gravity.battlepass.BattlePassDefinitions
import me.nebula.gravity.battlepass.BattlePassProgress
import me.nebula.gravity.battlepass.BattlePassStore
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.confirmGui
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.gui.paginatedGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.Material

object BattlePassMenu {

    fun open(player: Player) {
        val active = BattlePassRegistry.activePasses()
        if (active.isEmpty()) return
        if (active.size == 1) {
            openTierView(player, active.first().id)
            return
        }
        openPassSelector(player)
    }

    private fun openPassSelector(player: Player) {
        val active = BattlePassRegistry.activePasses()
        val data = BattlePassStore.load(player.uuid) ?: BattlePassData()

        val selectorGui = gui(player.translateRaw("orbit.battlepass.title"), rows = 3) {
            active.forEachIndexed { index, definition ->
                val progress = data.passes[definition.id] ?: BattlePassProgress()
                val material = Material.fromKey(definition.icon) ?: Material.EXPERIENCE_BOTTLE
                val xpNeeded = if (progress.tier < definition.xpPerTier.size) definition.xpPerTier[progress.tier] else 0L

                slot(10 + index, itemStack(material) {
                    name(player.translateRaw(definition.nameKey))
                    lore(player.translateRaw("orbit.battlepass.tier_progress",
                        "tier" to progress.tier.toString(),
                        "max" to definition.maxTier.toString(),
                    ))
                    lore(player.translateRaw("orbit.battlepass.xp_progress",
                        "xp" to progress.xp.toString(),
                        "needed" to xpNeeded.toString(),
                    ))
                    lore(player.translateRaw("orbit.battlepass.days_remaining",
                        "days" to definition.daysRemaining().toString(),
                    ))
                    if (progress.premium) {
                        lore(player.translateRaw("orbit.battlepass.xp_boost"))
                    }
                    clean()
                }) { p -> openTierView(p, definition.id) }
            }

            slot(16, itemStack(Material.BOOK) {
                name("<gray>${player.translateRaw("orbit.battlepass.history_title")}")
                clean()
            }) { p -> openSeasonHistory(p) }

            fillDefault()
        }
        player.openGui(selectorGui)
    }

    private fun openTierView(player: Player, passId: String) {
        val definition = BattlePassRegistry[passId] ?: return
        val data = BattlePassStore.load(player.uuid) ?: BattlePassData()
        val progress = data.passes[passId] ?: BattlePassProgress()
        val xpNeeded = if (progress.tier < definition.xpPerTier.size) definition.xpPerTier[progress.tier] else 0L
        val tierGui = paginatedGui(player.translateRaw(definition.nameKey), rows = 6) {
            border(Material.GRAY_STAINED_GLASS_PANE)

            staticSlot(4, itemStack(Material.CLOCK) {
                name(player.translateRaw(definition.nameKey))
                lore(player.translateRaw("orbit.battlepass.tier_progress",
                    "tier" to progress.tier.toString(),
                    "max" to definition.maxTier.toString(),
                ))
                lore(player.translateRaw("orbit.battlepass.xp_progress",
                    "xp" to progress.xp.toString(),
                    "needed" to xpNeeded.toString(),
                ))
                lore(player.translateRaw("orbit.battlepass.total_xp",
                    "xp" to progress.totalXpEarned.toString(),
                ))
                if (definition.isExpired()) {
                    lore(player.translateRaw("orbit.battlepass.season_ended"))
                } else {
                    lore(player.translateRaw("orbit.battlepass.days_remaining",
                        "days" to definition.daysRemaining().toString(),
                    ))
                }
                if (progress.premium) {
                    lore(player.translateRaw("orbit.battlepass.xp_boost"))
                }
                clean()
            })

            val progressPanes = 9
            val percentage = if (xpNeeded > 0) (progress.xp.toDouble() / xpNeeded).coerceIn(0.0, 1.0) else 1.0
            val filled = (percentage * progressPanes).toInt()
            for (i in 0 until progressPanes) {
                val mat = if (i < filled) Material.LIME_STAINED_GLASS_PANE else Material.GRAY_STAINED_GLASS_PANE
                staticSlot(36 + i, itemStack(mat) {
                    name("<gray> ")
                    clean()
                })
            }

            for (tier in 1..definition.maxTier) {
                val freeReward = definition.freeRewards[tier]
                val premiumReward = definition.premiumRewards[tier]
                val reached = tier <= progress.tier
                val freeClaimed = tier in progress.claimedFree
                val premiumClaimed = tier in progress.claimedPremium

                val tierMaterial = when {
                    reached && !freeClaimed -> Material.LIME_STAINED_GLASS_PANE
                    freeClaimed -> Material.GREEN_CONCRETE
                    else -> Material.RED_STAINED_GLASS_PANE
                }

                item(itemStack(tierMaterial) {
                    name("<white>Tier $tier")
                    if (freeReward != null) {
                        lore(player.translateRaw("orbit.battlepass.free_reward",
                            "reward" to "${freeReward.amount}x ${freeReward.value}",
                        ))
                    }
                    if (premiumReward != null) {
                        val premiumMat = when {
                            reached && progress.premium && !premiumClaimed -> "<gold>"
                            premiumClaimed -> "<green>"
                            else -> "<dark_purple>"
                        }
                        lore("${premiumMat}${player.translateRaw("orbit.battlepass.premium_reward",
                            "reward" to "${premiumReward.amount}x ${premiumReward.value}",
                        )}")
                    }
                    when {
                        reached && !freeClaimed -> lore("<yellow>${player.translateRaw("orbit.battlepass.click_claim")}")
                        !reached -> lore("<red>${player.translateRaw("orbit.battlepass.locked")}")
                    }
                    clean()
                }) { p ->
                    if (!reached) return@item
                    if (!freeClaimed) {
                        BattlePassManager.claimReward(p, passId, tier, false)
                    }
                    if (progress.premium && !premiumClaimed && premiumReward != null) {
                        BattlePassManager.claimReward(p, passId, tier, true)
                    }
                    openTierView(p, passId)
                }
            }

            if (!progress.premium && definition.premiumPrice > 0) {
                staticSlot(46, itemStack(Material.GOLD_INGOT) {
                    name("<gold>${player.translateRaw("orbit.battlepass.unlock_premium")}")
                    lore(player.translateRaw("orbit.battlepass.premium_cost", "price" to definition.premiumPrice.toString()))
                    clean()
                }) { p ->
                    openPremiumConfirmation(p, passId)
                }
            }

            if (progress.tier < definition.maxTier && definition.tierPurchasePrice > 0) {
                staticSlot(48, itemStack(Material.EMERALD) {
                    name("<green>${player.translateRaw("orbit.battlepass.buy_tier",
                        "cost" to definition.tierPurchasePrice.toString(),
                    )}")
                    clean()
                }) { p ->
                    BattlePassManager.purchaseTier(p, passId)
                    openTierView(p, passId)
                }
            }

            if (BattlePassRegistry.activePasses().size > 1) {
                staticSlot(49, itemStack(Material.ARROW) {
                    name("<gray>${player.translateRaw("orbit.battlepass.back")}")
                    clean()
                }) { p -> openPassSelector(p) }
            }

            staticSlot(50, itemStack(Material.BOOK) {
                name("<gray>${player.translateRaw("orbit.battlepass.history_title")}")
                clean()
            }) { p -> openSeasonHistory(p) }
        }
        tierGui.open(player)
    }

    fun openSeasonHistory(player: Player) {
        val data = BattlePassStore.load(player.uuid) ?: BattlePassData()
        val history = data.seasonHistory

        val historyGui = gui(player.translateRaw("orbit.battlepass.history_title"), rows = 3) {
            if (history.isEmpty()) {
                slot(13, itemStack(Material.BARRIER) {
                    name("<gray>${player.translateRaw("orbit.battlepass.history_empty")}")
                    clean()
                })
            } else {
                var slotIndex = 10
                for ((_, summary) in history) {
                    if (slotIndex > 16) break
                    val def = BattlePassDefinitions[summary.passId]
                    val nameKey = def?.nameKey?.value ?: summary.passId
                    val maxTier = def?.maxTier ?: 50
                    val status = if (summary.completedAt > 0) {
                        player.translateRaw("orbit.battlepass.history_completed")
                    } else {
                        player.translateRaw("orbit.battlepass.history_incomplete")
                    }
                    val material = def?.icon?.let { Material.fromKey(it) } ?: Material.PAPER

                    slot(slotIndex, itemStack(material) {
                        name(player.translateRaw("orbit.battlepass.history_entry",
                            "season" to player.translateRaw(nameKey),
                            "tier" to summary.finalTier.toString(),
                            "max" to maxTier.toString(),
                            "status" to status,
                        ))
                        lore(player.translateRaw("orbit.battlepass.total_xp",
                            "xp" to summary.totalXp.toString(),
                        ))
                        clean()
                    })
                    slotIndex++
                }
            }
            fillDefault()
        }
        player.openGui(historyGui)
    }

    private fun openPremiumConfirmation(player: Player, passId: String) {
        val definition = BattlePassRegistry[passId] ?: return

        val confirm = confirmGui(
            title = player.translateRaw("orbit.battlepass.premium_confirm_title"),
            confirmItem = itemStack(Material.GREEN_WOOL) {
                name("<green>${player.translateRaw("orbit.battlepass.premium_confirm")}")
                lore(player.translateRaw("orbit.battlepass.premium_cost", "price" to definition.premiumPrice.toString()))
                clean()
            },
            cancelItem = itemStack(Material.RED_WOOL) {
                name("<red>${player.translateRaw("orbit.battlepass.premium_cancel")}")
                clean()
            },
            previewItem = itemStack(Material.GOLD_INGOT) {
                name("<gold>${player.translateRaw("orbit.battlepass.unlock_premium")}")
                lore(player.translateRaw("orbit.battlepass.premium_cost", "price" to definition.premiumPrice.toString()))
                clean()
            },
            onConfirm = { p ->
                BattlePassManager.purchasePremium(p, passId)
                openTierView(p, passId)
            },
            onCancel = { p -> openTierView(p, passId) },
        )
        player.openGui(confirm)
    }
}
