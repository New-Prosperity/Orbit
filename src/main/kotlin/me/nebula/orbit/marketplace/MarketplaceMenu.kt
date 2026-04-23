package me.nebula.orbit.marketplace

import me.nebula.gravity.audit.AuditAction
import me.nebula.gravity.audit.AuditStore
import me.nebula.gravity.cosmetic.CosmeticCategory
import me.nebula.gravity.cosmetic.CosmeticPlayerData
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.cosmetic.RemoveCosmeticProcessor
import me.nebula.gravity.cosmetic.TransferCosmeticProcessor
import me.nebula.gravity.economy.AddBalanceProcessor
import me.nebula.gravity.economy.EconomyStore
import me.nebula.orbit.perks.EconomyPerks
import me.nebula.gravity.economy.EconomyTransactionStore
import me.nebula.gravity.economy.PurchaseCosmeticProcessor
import me.nebula.gravity.economy.TransactionType
import me.nebula.gravity.marketplace.MarketplaceListing
import me.nebula.gravity.marketplace.MarketplaceListingStore
import me.nebula.gravity.marketplace.activeListingsPredicate
import me.nebula.gravity.marketplace.listingsBySellerPredicate
import me.nebula.gravity.messaging.MarketplaceSaleMessage
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.messaging.PlayerTextMessage
import me.nebula.orbit.cosmetic.CosmeticDataCache
import me.nebula.orbit.cosmetic.CosmeticMenu
import me.nebula.orbit.cosmetic.CosmeticRegistry
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.GuiBuilder
import me.nebula.orbit.utils.gui.confirmGui
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.gui.paginatedGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import me.nebula.gravity.translation.Keys

object MarketplaceMenu {

    private val categorySlots = mapOf(
        CosmeticCategory.ARMOR_SKIN to Pair(11, Material.LEATHER_CHESTPLATE),
        CosmeticCategory.KILL_EFFECT to Pair(12, Material.REDSTONE),
        CosmeticCategory.TRAIL to Pair(13, Material.BLAZE_POWDER),
        CosmeticCategory.WIN_EFFECT to Pair(14, Material.FIREWORK_ROCKET),
        CosmeticCategory.PROJECTILE_TRAIL to Pair(15, Material.ARROW),
        CosmeticCategory.COMPANION to Pair(20, Material.ARMOR_STAND),
        CosmeticCategory.PET to Pair(21, Material.BONE),
        CosmeticCategory.MOUNT to Pair(22, Material.SADDLE),
        CosmeticCategory.SPAWN_EFFECT to Pair(23, Material.ENDER_PEARL),
        CosmeticCategory.DEATH_EFFECT to Pair(24, Material.WITHER_SKELETON_SKULL),
        CosmeticCategory.AURA to Pair(29, Material.NETHER_STAR),
        CosmeticCategory.ELIMINATION_MESSAGE to Pair(30, Material.NAME_TAG),
        CosmeticCategory.JOIN_QUIT_MESSAGE to Pair(31, Material.OAK_SIGN),
        CosmeticCategory.GADGET to Pair(32, Material.BLAZE_ROD),
        CosmeticCategory.GRAVESTONE to Pair(33, Material.MOSSY_COBBLESTONE),
    )

    private val rarityPriceDefaults = mapOf(
        "COMMON" to 100,
        "RARE" to 250,
        "EPIC" to 500,
        "LEGENDARY" to 1000,
    )

    fun openMain(player: Player) {
        if (TradeManager.isTrading(player.uuid)) {
            player.sendMessage(player.translate(Keys.Orbit.Trade.AlreadyTrading))
            return
        }
        val gui = gui(player.translateRaw(Keys.Orbit.Marketplace.Menu.Title), rows = 3) {
            fillDefault()
            slot(11, itemStack(Material.EMERALD) {
                name("<green>${player.translateRaw(Keys.Orbit.Marketplace.Menu.Browse)}")
                clean()
            }) { openBrowseCategories(it) }
            slot(13, itemStack(Material.GOLD_INGOT) {
                name("<gold>${player.translateRaw(Keys.Orbit.Marketplace.Menu.Sell)}")
                clean()
            }) { openSellSelect(it) }
            slot(15, itemStack(Material.BOOK) {
                name("<yellow>${player.translateRaw(Keys.Orbit.Marketplace.Menu.MyListings)}")
                clean()
            }) { openMyListings(it) }
        }
        player.openGui(gui)
    }

    private fun openBrowseCategories(player: Player) {
        val gui = gui(player.translateRaw(Keys.Orbit.Marketplace.Browse.Title), rows = 5) {
            fillDefault()
            categorySlots.forEach { (category, config) ->
                val (slot, material) = config
                slot(slot, itemStack(material) {
                    name(player.translateRaw(category.displayKey))
                    clean()
                }) { p -> openBrowseListings(p, category) }
            }
            backButton(36) { openMain(it) }
        }
        player.openGui(gui)
    }

    private fun openBrowseListings(player: Player, category: CosmeticCategory) {
        val now = System.currentTimeMillis()
        val listings = MarketplaceListingStore.query(activeListingsPredicate(now))
            .filter { CosmeticRegistry[it.cosmeticId]?.category == category }
            .sortedBy { it.price }

        val gui = paginatedGui(player.translateRaw(category.displayKey), rows = 6) {
            border(Material.GRAY_STAINED_GLASS_PANE)
            for (listing in listings) {
                val definition = CosmeticRegistry[listing.cosmeticId] ?: continue
                val material = Material.fromKey(definition.material) ?: Material.BARRIER
                item(itemStack(material) {
                    name("${definition.rarity.colorTag}${player.translateRaw(definition.nameKey)}")
                    lore(player.translateRaw(Keys.Orbit.Marketplace.Listing.Seller, "seller" to listing.sellerName))
                    lore(player.translateRaw(Keys.Orbit.Marketplace.Listing.Price, "price" to listing.price.toString()))
                    if (definition.maxLevel > 1) {
                        lore(player.translateRaw(Keys.Orbit.Marketplace.Listing.Level, "level" to listing.cosmeticLevel.toString()))
                    }
                    clean()
                }) { p -> openBuyConfirmation(p, listing) }
            }
            backButton(49) { openBrowseCategories(it) }
        }
        gui.open(player)
    }

    private fun openBuyConfirmation(player: Player, listing: MarketplaceListing) {
        if (listing.sellerId == player.uuid) {
            player.sendMessage(player.translate(Keys.Orbit.Marketplace.CannotBuyOwn))
            return
        }

        val definition = CosmeticRegistry[listing.cosmeticId] ?: return
        val material = Material.fromKey(definition.material) ?: Material.BARRIER

        val confirm = confirmGui(
            title = player.translateRaw(Keys.Orbit.Marketplace.ConfirmBuy.Title),
            confirmItem = itemStack(Material.GREEN_WOOL) {
                name("<green>${player.translateRaw(Keys.Orbit.Marketplace.ConfirmBuy.Accept)}")
                lore(player.translateRaw(Keys.Orbit.Marketplace.ConfirmBuy.Cost, "price" to listing.price.toString()))
                clean()
            },
            cancelItem = itemStack(Material.RED_WOOL) {
                name("<red>${player.translateRaw(Keys.Orbit.Marketplace.ConfirmBuy.Cancel)}")
                clean()
            },
            previewItem = itemStack(material) {
                name("${definition.rarity.colorTag}${player.translateRaw(definition.nameKey)}")
                lore(player.translateRaw(Keys.Orbit.Marketplace.Listing.Seller, "seller" to listing.sellerName))
                lore(player.translateRaw(Keys.Orbit.Marketplace.ConfirmBuy.Cost, "price" to listing.price.toString()))
                clean()
            },
            onConfirm = { p -> executeBuy(p, listing) },
            onCancel = { p -> openBrowseCategories(p) },
        )
        player.openGui(confirm)
    }

    private fun executeBuy(player: Player, listing: MarketplaceListing) {
        val definition = CosmeticRegistry[listing.cosmeticId]
        if (definition == null) {
            player.sendMessage(player.translate(Keys.Orbit.Marketplace.ListingUnavailable))
            return
        }

        val claimed = MarketplaceListingStore.delete(listing.id)
        if (claimed == null) {
            player.sendMessage(player.translate(Keys.Orbit.Marketplace.ListingUnavailable))
            openBrowseCategories(player)
            return
        }

        val buyerPrice = EconomyPerks.costAfterShopDiscount(player.uuid, claimed.price.toDouble())
        val paid = EconomyStore.executeOnKey(player.uuid, PurchaseCosmeticProcessor("coins", buyerPrice))
        if (!paid) {
            MarketplaceListingStore.save(claimed.id, claimed)
            player.sendMessage(player.translate(Keys.Orbit.Marketplace.InsufficientFunds))
            openBrowseCategories(player)
            return
        }

        val received = CosmeticStore.executeOnKey(player.uuid, TransferCosmeticProcessor(claimed.cosmeticId, claimed.cosmeticLevel))
        if (!received) {
            EconomyStore.executeOnKey(player.uuid, AddBalanceProcessor("coins", buyerPrice))
            MarketplaceListingStore.save(claimed.id, claimed)
            player.sendMessage(player.translate(Keys.Orbit.Marketplace.AlreadyOwned))
            openBrowseCategories(player)
            return
        }

        val sellerProceeds = claimed.price * (1.0 - MarketplaceListingStore.MARKETPLACE_FEE)
        EconomyStore.executeOnKey(claimed.sellerId, AddBalanceProcessor("coins", sellerProceeds))

        EconomyTransactionStore.record(player.uuid, "coins", claimed.price.toDouble(), TransactionType.PAY, "Marketplace buy: ${claimed.cosmeticId}")
        EconomyTransactionStore.record(claimed.sellerId, "coins", sellerProceeds, TransactionType.GIVE, "Marketplace sale: ${claimed.cosmeticId}")

        CosmeticDataCache.invalidate(player.uuid)

        AuditStore.log(
            actorId = player.uuid,
            actorName = player.username,
            action = AuditAction.MARKETPLACE_BUY,
            targetId = claimed.sellerId,
            targetName = claimed.sellerName,
            details = "Bought ${claimed.cosmeticId} (level ${claimed.cosmeticLevel}) for ${claimed.price} coins",
            source = "orbit",
        )

        NetworkMessenger.publish(MarketplaceSaleMessage(
            listingId = claimed.id,
            buyerId = player.uuid,
            buyerName = player.username,
            sellerId = claimed.sellerId,
            sellerName = claimed.sellerName,
            cosmeticId = claimed.cosmeticId,
            cosmeticLevel = claimed.cosmeticLevel,
            price = claimed.price,
            rarity = definition.rarity.name,
        ))

        NetworkMessenger.publish(PlayerTextMessage(
            claimed.sellerId,
            "<green><lang:orbit.marketplace.sold_notification:${claimed.cosmeticId}:${player.username}:${claimed.price}:${sellerProceeds.toInt()}>"
        ))

        player.sendMessage(player.translate(Keys.Orbit.Marketplace.Purchased,
            "cosmetic" to player.translateRaw(definition.nameKey),
            "price" to claimed.price.toString()
        ))
        openMain(player)
    }

    fun openSellSelect(player: Player) {
        val playerData = CosmeticStore.load(player.uuid) ?: CosmeticPlayerData()
        if (playerData.owned.isEmpty()) {
            player.sendMessage(player.translate(Keys.Orbit.Marketplace.Sell.NoCosmetics))
            return
        }

        val sellable = playerData.owned.mapNotNull { (id, level) ->
            val def = CosmeticRegistry[id] ?: return@mapNotNull null
            if (def.price == 0) return@mapNotNull null
            Triple(id, level, def)
        }

        if (sellable.isEmpty()) {
            player.sendMessage(player.translate(Keys.Orbit.Marketplace.Sell.NoCosmetics))
            return
        }

        val gui = paginatedGui(player.translateRaw(Keys.Orbit.Marketplace.Sell.Title), rows = 6) {
            border(Material.GRAY_STAINED_GLASS_PANE)
            for ((cosmeticId, level, definition) in sellable) {
                val material = Material.fromKey(definition.material) ?: Material.BARRIER
                val equipped = playerData.equipped.values.contains(cosmeticId)
                item(itemStack(material) {
                    name("${definition.rarity.colorTag}${player.translateRaw(definition.nameKey)}")
                    if (definition.maxLevel > 1) {
                        lore(player.translateRaw(Keys.Orbit.Marketplace.Listing.Level, "level" to level.toString()))
                    }
                    if (equipped) {
                        lore("<yellow>${player.translateRaw(Keys.Orbit.Marketplace.Sell.EquippedWarning)}")
                    }
                    clean()
                }) { p -> openSellPricing(p, cosmeticId, level) }
            }
            backButton(49) { openMain(it) }
        }
        gui.open(player)
    }

    private fun openSellPricing(player: Player, cosmeticId: String, level: Int) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val basePrice = rarityPriceDefaults[definition.rarity.name] ?: definition.price

        val gui = gui(player.translateRaw(Keys.Orbit.Marketplace.Sell.PricingTitle), rows = 3) {
            fillDefault()
            priceSlot(this, player, 10, basePrice / 2, cosmeticId, level)
            priceSlot(this, player, 11, basePrice, cosmeticId, level)
            priceSlot(this, player, 12, (basePrice * 1.5).toInt(), cosmeticId, level)
            priceSlot(this, player, 13, basePrice * 2, cosmeticId, level)
            priceSlot(this, player, 14, basePrice * 3, cosmeticId, level)
            priceSlot(this, player, 15, basePrice * 5, cosmeticId, level)
            backButton(22) { openSellSelect(it) }
        }
        player.openGui(gui)
    }

    private fun priceSlot(builder: GuiBuilder, player: Player, slot: Int, price: Int, cosmeticId: String, level: Int) {
        builder.slot(slot, itemStack(Material.GOLD_NUGGET) {
            name("<gold>$price ${player.translateRaw(Keys.Orbit.Marketplace.Coins)}")
            clean()
        }) { p -> executeSell(p, cosmeticId, level, price) }
    }

    private fun executeSell(player: Player, cosmeticId: String, level: Int, price: Int) {
        if (TradeManager.isTrading(player.uuid)) {
            player.sendMessage(player.translate(Keys.Orbit.Trade.AlreadyTrading))
            return
        }

        val safePrice = price.coerceAtLeast(1)
        val definition = CosmeticRegistry[cosmeticId] ?: return

        val activeCount = MarketplaceListingStore.query(listingsBySellerPredicate(player.uuid)).size
        if (activeCount >= MarketplaceListingStore.MAX_ACTIVE_LISTINGS) {
            player.sendMessage(player.translate(Keys.Orbit.Marketplace.MaxListings,
                "max" to MarketplaceListingStore.MAX_ACTIVE_LISTINGS.toString()
            ))
            return
        }

        val removedLevel = CosmeticStore.executeOnKey(player.uuid, RemoveCosmeticProcessor(cosmeticId))
        if (removedLevel == 0) {
            player.sendMessage(player.translate(Keys.Orbit.Marketplace.Sell.NotOwned))
            openSellSelect(player)
            return
        }

        CosmeticMenu.despawnCategory(player.uuid, definition.category, player)
        CosmeticDataCache.invalidate(player.uuid)

        MarketplaceListingStore.record(player.uuid, player.username, cosmeticId, removedLevel, safePrice)

        AuditStore.log(
            actorId = player.uuid,
            actorName = player.username,
            action = AuditAction.MARKETPLACE_LIST,
            details = "Listed $cosmeticId (level $removedLevel) for $safePrice coins",
            source = "orbit",
        )

        player.sendMessage(player.translate(Keys.Orbit.Marketplace.Listed,
            "cosmetic" to player.translateRaw(definition.nameKey),
            "price" to price.toString()
        ))
        openMain(player)
    }

    fun openMyListings(player: Player) {
        val listings = MarketplaceListingStore.query(listingsBySellerPredicate(player.uuid))
            .sortedByDescending { it.listedAt }

        if (listings.isEmpty()) {
            player.sendMessage(player.translate(Keys.Orbit.Marketplace.MyListings.Empty))
            openMain(player)
            return
        }

        val gui = paginatedGui(player.translateRaw(Keys.Orbit.Marketplace.MyListings.Title), rows = 6) {
            border(Material.GRAY_STAINED_GLASS_PANE)
            for (listing in listings) {
                val definition = CosmeticRegistry[listing.cosmeticId] ?: continue
                val material = Material.fromKey(definition.material) ?: Material.BARRIER
                item(itemStack(material) {
                    name("${definition.rarity.colorTag}${player.translateRaw(definition.nameKey)}")
                    lore(player.translateRaw(Keys.Orbit.Marketplace.Listing.Price, "price" to listing.price.toString()))
                    lore("<red>${player.translateRaw(Keys.Orbit.Marketplace.MyListings.Cancel)}")
                    clean()
                }) { p -> executeCancelListing(p, listing) }
            }
            backButton(49) { openMain(it) }
        }
        gui.open(player)
    }

    private fun executeCancelListing(player: Player, listing: MarketplaceListing) {
        val removed = MarketplaceListingStore.delete(listing.id)
        if (removed == null) {
            player.sendMessage(player.translate(Keys.Orbit.Marketplace.ListingUnavailable))
            openMyListings(player)
            return
        }

        CosmeticStore.executeOnKey(player.uuid, TransferCosmeticProcessor(removed.cosmeticId, removed.cosmeticLevel))
        CosmeticDataCache.invalidate(player.uuid)

        player.sendMessage(player.translate(Keys.Orbit.Marketplace.ListingCancelled,
            "cosmetic" to (CosmeticRegistry[removed.cosmeticId]?.let { player.translateRaw(it.nameKey) } ?: removed.cosmeticId)
        ))
        openMyListings(player)
    }
}
