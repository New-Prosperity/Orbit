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
import me.nebula.gravity.translation.Keys
import me.nebula.ether.utils.translation.asTranslationKey

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

        val selectorGui = gui(player.translateRaw(Keys.Orbit.Battlepass.Title), rows = 3) {
            active.forEachIndexed { index, definition ->
                val progress = data.passes[definition.id] ?: BattlePassProgress()
                val material = Material.fromKey(definition.icon) ?: Material.EXPERIENCE_BOTTLE
                val xpNeeded = if (progress.tier < definition.xpPerTier.size) definition.xpPerTier[progress.tier] else 0L

                slot(10 + index, itemStack(material) {
                    name(player.translateRaw(definition.nameKey))
                    lore(player.translateRaw(Keys.Orbit.Battlepass.TierProgress,
                        "tier" to progress.tier.toString(),
                        "max" to definition.maxTier.toString(),
                    ))
                    lore(player.translateRaw(Keys.Orbit.Battlepass.XpProgress,
                        "xp" to progress.xp.toString(),
                        "needed" to xpNeeded.toString(),
                    ))
                    lore(player.translateRaw(Keys.Orbit.Battlepass.DaysRemaining,
                        "days" to definition.daysRemaining().toString(),
                    ))
                    if (progress.premium) {
                        lore(player.translateRaw(Keys.Orbit.Battlepass.XpBoost))
                    }
                    clean()
                }) { p -> openTierView(p, definition.id) }
            }

            slot(16, itemStack(Material.BOOK) {
                name("<gray>${player.translateRaw(Keys.Orbit.Battlepass.HistoryTitle)}")
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
                lore(player.translateRaw(Keys.Orbit.Battlepass.TierProgress,
                    "tier" to progress.tier.toString(),
                    "max" to definition.maxTier.toString(),
                ))
                lore(player.translateRaw(Keys.Orbit.Battlepass.XpProgress,
                    "xp" to progress.xp.toString(),
                    "needed" to xpNeeded.toString(),
                ))
                lore(player.translateRaw(Keys.Orbit.Battlepass.TotalXp,
                    "xp" to progress.totalXpEarned.toString(),
                ))
                if (definition.isExpired()) {
                    lore(player.translateRaw(Keys.Orbit.Battlepass.SeasonEnded))
                } else {
                    lore(player.translateRaw(Keys.Orbit.Battlepass.DaysRemaining,
                        "days" to definition.daysRemaining().toString(),
                    ))
                }
                if (progress.premium) {
                    lore(player.translateRaw(Keys.Orbit.Battlepass.XpBoost))
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
                        lore(player.translateRaw(Keys.Orbit.Battlepass.FreeReward,
                            "reward" to "${freeReward.amount}x ${freeReward.value}",
                        ))
                    }
                    if (premiumReward != null) {
                        val premiumMat = when {
                            reached && progress.premium && !premiumClaimed -> "<gold>"
                            premiumClaimed -> "<green>"
                            else -> "<dark_purple>"
                        }
                        lore("${premiumMat}${player.translateRaw(Keys.Orbit.Battlepass.PremiumReward,
                            "reward" to "${premiumReward.amount}x ${premiumReward.value}",
                        )}")
                    }
                    when {
                        reached && !freeClaimed -> lore("<yellow>${player.translateRaw(Keys.Orbit.Battlepass.ClickClaim)}")
                        !reached -> lore("<red>${player.translateRaw(Keys.Orbit.Battlepass.Locked)}")
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
                    name("<gold>${player.translateRaw(Keys.Orbit.Battlepass.UnlockPremium)}")
                    lore(player.translateRaw(Keys.Orbit.Battlepass.PremiumCost, "price" to definition.premiumPrice.toString()))
                    clean()
                }) { p ->
                    openPremiumConfirmation(p, passId)
                }
            }

            if (progress.tier < definition.maxTier && definition.tierPurchasePrice > 0) {
                staticSlot(48, itemStack(Material.EMERALD) {
                    name("<green>${player.translateRaw(Keys.Orbit.Battlepass.BuyTier,
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
                    name("<gray>${player.translateRaw(Keys.Orbit.Battlepass.Back)}")
                    clean()
                }) { p -> openPassSelector(p) }
            }

            staticSlot(50, itemStack(Material.BOOK) {
                name("<gray>${player.translateRaw(Keys.Orbit.Battlepass.HistoryTitle)}")
                clean()
            }) { p -> openSeasonHistory(p) }
        }
        tierGui.open(player)
    }

    fun openSeasonHistory(player: Player) {
        val data = BattlePassStore.load(player.uuid) ?: BattlePassData()
        val history = data.seasonHistory

        val historyGui = gui(player.translateRaw(Keys.Orbit.Battlepass.HistoryTitle), rows = 3) {
            if (history.isEmpty()) {
                slot(13, itemStack(Material.BARRIER) {
                    name("<gray>${player.translateRaw(Keys.Orbit.Battlepass.HistoryEmpty)}")
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
                        player.translateRaw(Keys.Orbit.Battlepass.HistoryCompleted)
                    } else {
                        player.translateRaw(Keys.Orbit.Battlepass.HistoryIncomplete)
                    }
                    val material = def?.icon?.let { Material.fromKey(it) } ?: Material.PAPER

                    slot(slotIndex, itemStack(material) {
                        name(player.translateRaw(Keys.Orbit.Battlepass.HistoryEntry,
                            "season" to player.translateRaw(nameKey.asTranslationKey()),
                            "tier" to summary.finalTier.toString(),
                            "max" to maxTier.toString(),
                            "status" to status,
                        ))
                        lore(player.translateRaw(Keys.Orbit.Battlepass.TotalXp,
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
            title = player.translateRaw(Keys.Orbit.Battlepass.PremiumConfirmTitle),
            confirmItem = itemStack(Material.GREEN_WOOL) {
                name("<green>${player.translateRaw(Keys.Orbit.Battlepass.PremiumConfirm)}")
                lore(player.translateRaw(Keys.Orbit.Battlepass.PremiumCost, "price" to definition.premiumPrice.toString()))
                clean()
            },
            cancelItem = itemStack(Material.RED_WOOL) {
                name("<red>${player.translateRaw(Keys.Orbit.Battlepass.PremiumCancel)}")
                clean()
            },
            previewItem = itemStack(Material.GOLD_INGOT) {
                name("<gold>${player.translateRaw(Keys.Orbit.Battlepass.UnlockPremium)}")
                lore(player.translateRaw(Keys.Orbit.Battlepass.PremiumCost, "price" to definition.premiumPrice.toString()))
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
