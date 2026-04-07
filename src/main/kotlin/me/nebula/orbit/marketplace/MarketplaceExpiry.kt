package me.nebula.orbit.marketplace

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.audit.AuditAction
import me.nebula.gravity.audit.AuditStore
import me.nebula.gravity.cosmetic.TransferCosmeticProcessor
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.marketplace.MarketplaceListingStore
import me.nebula.gravity.marketplace.expiredListingsPredicate
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.messaging.PlayerTextMessage
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.timer.Task
import java.time.Duration

object MarketplaceExpiry {

    private val logger = logger("MarketplaceExpiry")
    private var task: Task? = null

    fun install() {
        task = repeat(Duration.ofSeconds(60)) { checkExpired() }
        logger.info { "Marketplace expiry routine installed" }
    }

    fun uninstall() {
        task?.cancel()
        task = null
        logger.info { "Marketplace expiry routine uninstalled" }
    }

    private fun checkExpired() {
        val now = System.currentTimeMillis()
        val expired = MarketplaceListingStore.query(expiredListingsPredicate(now))
        if (expired.isEmpty()) return

        for (listing in expired) {
            val removed = MarketplaceListingStore.delete(listing.id) ?: continue

            CosmeticStore.executeOnKey(removed.sellerId, TransferCosmeticProcessor(removed.cosmeticId, removed.cosmeticLevel))

            NetworkMessenger.publish(PlayerTextMessage(
                removed.sellerId,
                "<yellow><lang:orbit.marketplace.listing_expired:${removed.cosmeticId}>"
            ))

            AuditStore.log(
                actorId = removed.sellerId,
                actorName = removed.sellerName,
                action = AuditAction.MARKETPLACE_EXPIRE,
                details = "${removed.cosmeticId} (level ${removed.cosmeticLevel}) expired at price ${removed.price}",
                source = "orbit",
            )

            logger.debug { "Expired listing ${removed.id}: ${removed.cosmeticId} returned to ${removed.sellerName}" }
        }
    }
}
