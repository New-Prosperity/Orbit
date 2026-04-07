package me.nebula.orbit.marketplace

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.audit.AuditAction
import me.nebula.gravity.audit.AuditStore
import me.nebula.gravity.cosmetic.BulkAddCosmeticsProcessor
import me.nebula.gravity.cosmetic.BulkRemoveCosmeticsProcessor
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.economy.AddBalanceProcessor
import me.nebula.gravity.economy.EconomyStore
import me.nebula.gravity.economy.PurchaseCosmeticProcessor
import me.nebula.orbit.cosmetic.CosmeticDataCache
import me.nebula.orbit.cosmetic.CosmeticMenu
import me.nebula.orbit.cosmetic.CosmeticRegistry
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.scheduler.delay
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object TradeManager {

    private val logger = logger("TradeManager")

    private val pendingRequests = ConcurrentHashMap<UUID, TradeRequest>()
    private val activeSessions = ConcurrentHashMap<UUID, TradeSessionData>()

    private const val REQUEST_TIMEOUT_TICKS = 600
    private const val TRADE_FEE = 0.05

    data class TradeRequest(
        val initiatorId: UUID,
        val initiatorName: String,
        val targetId: UUID,
        val expiresAt: Long,
    )

    class TradeSessionData(
        val initiatorId: UUID,
        val targetId: UUID,
        val initiatorOffers: MutableSet<String> = ConcurrentHashMap.newKeySet(),
        val targetOffers: MutableSet<String> = ConcurrentHashMap.newKeySet(),
        @Volatile var initiatorConfirmed: Boolean = false,
        @Volatile var targetConfirmed: Boolean = false,
        val locked: AtomicBoolean = AtomicBoolean(false),
    ) {
        fun offersOf(playerId: UUID): MutableSet<String> =
            if (playerId == initiatorId) initiatorOffers else targetOffers

        fun otherOffersOf(playerId: UUID): Set<String> =
            if (playerId == initiatorId) targetOffers else initiatorOffers

        fun otherId(playerId: UUID): UUID =
            if (playerId == initiatorId) targetId else initiatorId

        fun isConfirmedBy(playerId: UUID): Boolean =
            if (playerId == initiatorId) initiatorConfirmed else targetConfirmed

        fun setConfirmed(playerId: UUID, value: Boolean) {
            if (playerId == initiatorId) initiatorConfirmed = value else targetConfirmed = value
        }

        fun resetConfirmations() {
            initiatorConfirmed = false
            targetConfirmed = false
        }
    }

    fun isTrading(playerId: UUID): Boolean = activeSessions.containsKey(playerId)

    fun sendRequest(initiator: Player, target: Player) {
        if (initiator.uuid == target.uuid) {
            initiator.sendMessage(initiator.translate("orbit.trade.cannot_trade_self"))
            return
        }
        if (isTrading(initiator.uuid)) {
            initiator.sendMessage(initiator.translate("orbit.trade.already_trading"))
            return
        }
        if (isTrading(target.uuid)) {
            initiator.sendMessage(initiator.translate("orbit.trade.player_busy", "player" to target.username))
            return
        }
        if (pendingRequests.containsKey(target.uuid)) {
            initiator.sendMessage(initiator.translate("orbit.trade.player_busy", "player" to target.username))
            return
        }
        if (pendingRequests.values.any { it.initiatorId == initiator.uuid }) {
            initiator.sendMessage(initiator.translate("orbit.trade.already_sent"))
            return
        }

        val request = TradeRequest(initiator.uuid, initiator.username, target.uuid, System.currentTimeMillis() + REQUEST_TIMEOUT_TICKS * 50L)
        pendingRequests[target.uuid] = request

        initiator.sendMessage(initiator.translate("orbit.trade.request_sent", "player" to target.username))
        target.sendMessage(target.translate("orbit.trade.request_received", "player" to initiator.username))

        delay(REQUEST_TIMEOUT_TICKS) {
            val pending = pendingRequests.remove(target.uuid)
            if (pending != null && pending.initiatorId == initiator.uuid) {
                val initiatorPlayer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(initiator.uuid)
                initiatorPlayer?.sendMessage(initiatorPlayer.translate("orbit.trade.request_expired"))
            }
        }
    }

    fun acceptRequest(target: Player) {
        val request = pendingRequests.remove(target.uuid) ?: return

        val initiator = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(request.initiatorId)
        if (initiator == null) {
            target.sendMessage(target.translate("orbit.trade.request_expired"))
            return
        }

        val session = TradeSessionData(request.initiatorId, target.uuid)
        activeSessions[request.initiatorId] = session
        activeSessions[target.uuid] = session

        TradeMenu.openForBoth(session, initiator, target)
    }

    fun denyRequest(target: Player) {
        val request = pendingRequests.remove(target.uuid) ?: return
        val initiator = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(request.initiatorId)
        initiator?.sendMessage(initiator.translate("orbit.trade.request_denied", "player" to target.username))
        target.sendMessage(target.translate("orbit.trade.request_denied_self"))
    }

    fun hasPendingRequest(targetId: UUID): Boolean = pendingRequests.containsKey(targetId)

    fun toggleOffer(player: Player, cosmeticId: String) {
        val session = activeSessions[player.uuid] ?: return
        if (session.locked.get()) return

        val offers = session.offersOf(player.uuid)
        if (cosmeticId in offers) {
            offers.remove(cosmeticId)
        } else {
            offers.add(cosmeticId)
        }

        session.resetConfirmations()

        val initiator = resolvePlayer(session.initiatorId) ?: return
        val target = resolvePlayer(session.targetId) ?: return
        TradeMenu.openForBoth(session, initiator, target)
    }

    fun confirm(player: Player) {
        val session = activeSessions[player.uuid] ?: return
        if (session.locked.get()) return

        session.setConfirmed(player.uuid, true)

        if (session.initiatorConfirmed && session.targetConfirmed) {
            executeSwap(session)
        } else {
            val initiator = resolvePlayer(session.initiatorId)
            val target = resolvePlayer(session.targetId)
            if (initiator != null && target != null) TradeMenu.openForBoth(session, initiator, target)
        }
    }

    private fun executeSwap(session: TradeSessionData) {
        if (!session.locked.compareAndSet(false, true)) return

        val initiator = resolvePlayer(session.initiatorId)
        val target = resolvePlayer(session.targetId)

        if (initiator == null || target == null) {
            session.locked.set(false)
            cancelSession(session, "orbit.trade.cancelled")
            return
        }

        val initiatorOfferIds = session.initiatorOffers.toSet()
        val targetOfferIds = session.targetOffers.toSet()

        if (initiatorOfferIds.isEmpty() && targetOfferIds.isEmpty()) {
            session.locked.set(false)
            cancelSession(session, "orbit.trade.nothing_to_trade")
            return
        }

        val initiatorFee = initiatorOfferIds.sumOf { CosmeticRegistry[it]?.price ?: 0 } * TRADE_FEE
        val targetFee = targetOfferIds.sumOf { CosmeticRegistry[it]?.price ?: 0 } * TRADE_FEE

        if (initiatorFee > 0) {
            val paid = EconomyStore.executeOnKey(session.initiatorId, PurchaseCosmeticProcessor("coins", initiatorFee))
            if (!paid) {
                session.locked.set(false)
                initiator.sendMessage(initiator.translate("orbit.trade.insufficient_fee", "amount" to initiatorFee.toInt().toString()))
                cancelSession(session, "orbit.trade.cancelled")
                return
            }
        }

        if (targetFee > 0) {
            val paid = EconomyStore.executeOnKey(session.targetId, PurchaseCosmeticProcessor("coins", targetFee))
            if (!paid) {
                if (initiatorFee > 0) EconomyStore.executeOnKey(session.initiatorId, AddBalanceProcessor("coins", initiatorFee))
                session.locked.set(false)
                target.sendMessage(target.translate("orbit.trade.insufficient_fee", "amount" to targetFee.toInt().toString()))
                cancelSession(session, "orbit.trade.cancelled")
                return
            }
        }

        val initiatorRemoved = if (initiatorOfferIds.isNotEmpty()) {
            CosmeticStore.executeOnKey(session.initiatorId, BulkRemoveCosmeticsProcessor(initiatorOfferIds))
        } else emptyMap()

        if (initiatorRemoved.isEmpty() && initiatorOfferIds.isNotEmpty()) {
            if (initiatorFee > 0) EconomyStore.executeOnKey(session.initiatorId, AddBalanceProcessor("coins", initiatorFee))
            if (targetFee > 0) EconomyStore.executeOnKey(session.targetId, AddBalanceProcessor("coins", targetFee))
            session.locked.set(false)
            cancelSession(session, "orbit.trade.cosmetic_unavailable")
            return
        }

        val targetRemoved = if (targetOfferIds.isNotEmpty()) {
            CosmeticStore.executeOnKey(session.targetId, BulkRemoveCosmeticsProcessor(targetOfferIds))
        } else emptyMap()

        if (targetRemoved.isEmpty() && targetOfferIds.isNotEmpty()) {
            if (initiatorRemoved.isNotEmpty()) CosmeticStore.executeOnKey(session.initiatorId, BulkAddCosmeticsProcessor(initiatorRemoved))
            if (initiatorFee > 0) EconomyStore.executeOnKey(session.initiatorId, AddBalanceProcessor("coins", initiatorFee))
            if (targetFee > 0) EconomyStore.executeOnKey(session.targetId, AddBalanceProcessor("coins", targetFee))
            session.locked.set(false)
            cancelSession(session, "orbit.trade.cosmetic_unavailable")
            return
        }

        if (targetRemoved.isNotEmpty()) CosmeticStore.executeOnKey(session.initiatorId, BulkAddCosmeticsProcessor(targetRemoved))
        if (initiatorRemoved.isNotEmpty()) CosmeticStore.executeOnKey(session.targetId, BulkAddCosmeticsProcessor(initiatorRemoved))

        for (cosmeticId in initiatorOfferIds) {
            val def = CosmeticRegistry[cosmeticId] ?: continue
            CosmeticMenu.despawnCategory(session.initiatorId, def.category, initiator)
        }
        for (cosmeticId in targetOfferIds) {
            val def = CosmeticRegistry[cosmeticId] ?: continue
            CosmeticMenu.despawnCategory(session.targetId, def.category, target)
        }

        CosmeticDataCache.invalidate(session.initiatorId)
        CosmeticDataCache.invalidate(session.targetId)

        AuditStore.log(
            actorId = session.initiatorId,
            actorName = initiator.username,
            action = AuditAction.TRADE_COMPLETE,
            targetId = session.targetId,
            targetName = target.username,
            details = "Gave: ${initiatorOfferIds.joinToString()}, Received: ${targetOfferIds.joinToString()}",
            source = "orbit",
        )

        initiator.closeInventory()
        target.closeInventory()
        initiator.sendMessage(initiator.translate("orbit.trade.completed"))
        target.sendMessage(target.translate("orbit.trade.completed"))

        activeSessions.remove(session.initiatorId)
        activeSessions.remove(session.targetId)
        session.locked.set(false)

        logger.info { "Trade completed: ${initiator.username} <-> ${target.username}" }
    }

    fun cancel(player: Player) {
        val session = activeSessions[player.uuid] ?: return
        if (session.locked.get()) return
        cancelSession(session, "orbit.trade.cancelled")
    }

    private fun cancelSession(session: TradeSessionData, messageKey: String) {
        activeSessions.remove(session.initiatorId)
        activeSessions.remove(session.targetId)

        val initiator = resolvePlayer(session.initiatorId)
        val target = resolvePlayer(session.targetId)

        initiator?.closeInventory()
        target?.closeInventory()
        initiator?.sendMessage(initiator.translate(messageKey))
        target?.sendMessage(target.translate(messageKey))
    }

    fun onDisconnect(player: Player) {
        pendingRequests.remove(player.uuid)
        pendingRequests.entries.removeIf { it.value.initiatorId == player.uuid }
        val session = activeSessions[player.uuid]
        if (session != null && !session.locked.get()) {
            cancelSession(session, "orbit.trade.cancelled")
        }
    }

    private fun resolvePlayer(uuid: UUID): Player? =
        MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
}
