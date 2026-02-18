package me.nebula.orbit.utils.deathmessage

import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.tag.Tag
import java.util.UUID

private val miniMessage = MiniMessage.miniMessage()
private val LAST_DAMAGE_TYPE_TAG = Tag.String("util:death_msg:last_damage_type")
private val LAST_ATTACKER_UUID_TAG = Tag.String("util:death_msg:last_attacker_uuid")

enum class DeathCause { PVP, FALL, VOID, FIRE, DROWNING, EXPLOSION, PROJECTILE, GENERIC }

enum class BroadcastScope { ALL, INSTANCE }

data class DeathMessageConfig(
    val messages: Map<DeathCause, String>,
    val broadcastScope: BroadcastScope,
)

class DeathMessageHandler(private val config: DeathMessageConfig) {

    private val eventNode = EventNode.all("death-messages")

    fun install() {
        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            player.setTag(LAST_DAMAGE_TYPE_TAG, event.damage.type.name())
            val damage = event.damage
            if (damage is EntityDamage) {
                val source = damage.source
                if (source is Player) {
                    player.setTag(LAST_ATTACKER_UUID_TAG, source.uuid.toString())
                } else {
                    player.removeTag(LAST_ATTACKER_UUID_TAG)
                }
            } else {
                player.removeTag(LAST_ATTACKER_UUID_TAG)
            }
        }

        eventNode.addListener(PlayerDeathEvent::class.java) { event ->
            val player = event.player
            val cause = resolveCause(player)
            val template = config.messages[cause] ?: config.messages[DeathCause.GENERIC] ?: return@addListener

            val killerUuid = player.getTag(LAST_ATTACKER_UUID_TAG)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val killer = killerUuid?.let { uuid ->
                MinecraftServer.getConnectionManager().onlinePlayers.firstOrNull { it.uuid == uuid }
            }
            val weapon = killer?.itemInMainHand ?: ItemStack.AIR
            val weaponName = weapon.material().key().value()

            val resolved = template
                .replace("{victim}", player.username)
                .replace("{killer}", killer?.username ?: "")
                .replace("{weapon}", weaponName)

            val component = miniMessage.deserialize(resolved)
            event.deathText = component

            when (config.broadcastScope) {
                BroadcastScope.ALL ->
                    MinecraftServer.getConnectionManager().onlinePlayers.forEach { it.sendMessage(component) }
                BroadcastScope.INSTANCE ->
                    player.instance?.players?.forEach { it.sendMessage(component) }
            }
        }

        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
    }

    fun uninstall() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
    }

    private fun resolveCause(player: Player): DeathCause {
        val attackerUuid = player.getTag(LAST_ATTACKER_UUID_TAG)
        if (attackerUuid != null) return DeathCause.PVP

        val typeName = player.getTag(LAST_DAMAGE_TYPE_TAG) ?: return DeathCause.GENERIC

        return when {
            typeName == DamageType.FALL.name() -> DeathCause.FALL
            typeName == DamageType.OUT_OF_WORLD.name() -> DeathCause.VOID
            typeName == DamageType.ON_FIRE.name() || typeName == DamageType.IN_FIRE.name() || typeName == DamageType.LAVA.name() -> DeathCause.FIRE
            typeName == DamageType.DROWN.name() -> DeathCause.DROWNING
            typeName == DamageType.EXPLOSION.name() || typeName == DamageType.PLAYER_EXPLOSION.name() -> DeathCause.EXPLOSION
            typeName == DamageType.ARROW.name() || typeName == DamageType.TRIDENT.name() -> DeathCause.PROJECTILE
            else -> DeathCause.GENERIC
        }
    }
}

class DeathMessageBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val messages = mutableMapOf<DeathCause, String>()
    @PublishedApi internal var broadcastScope: BroadcastScope = BroadcastScope.ALL

    fun pvp(template: String) { messages[DeathCause.PVP] = template }
    fun fall(template: String) { messages[DeathCause.FALL] = template }
    fun void(template: String) { messages[DeathCause.VOID] = template }
    fun fire(template: String) { messages[DeathCause.FIRE] = template }
    fun drowning(template: String) { messages[DeathCause.DROWNING] = template }
    fun explosion(template: String) { messages[DeathCause.EXPLOSION] = template }
    fun projectile(template: String) { messages[DeathCause.PROJECTILE] = template }
    fun generic(template: String) { messages[DeathCause.GENERIC] = template }
    fun broadcastAll() { broadcastScope = BroadcastScope.ALL }
    fun broadcastInstance() { broadcastScope = BroadcastScope.INSTANCE }

    @PublishedApi internal fun build(): DeathMessageConfig = DeathMessageConfig(
        messages = messages.toMap(),
        broadcastScope = broadcastScope,
    )
}

inline fun deathMessages(block: DeathMessageBuilder.() -> Unit): DeathMessageHandler {
    val config = DeathMessageBuilder().apply(block).build()
    return DeathMessageHandler(config)
}
