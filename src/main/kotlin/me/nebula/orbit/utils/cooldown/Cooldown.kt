package me.nebula.orbit.utils.cooldown

import me.nebula.orbit.utils.chat.mm
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration as KDuration
import kotlin.time.Duration.Companion.seconds

class Cooldown<K : Any>(private val duration: Duration) {

    private val cooldowns = ConcurrentHashMap<K, Long>()

    fun isReady(key: K): Boolean {
        val expiry = cooldowns[key] ?: return true
        if (System.currentTimeMillis() >= expiry) {
            cooldowns.remove(key)
            return true
        }
        return false
    }

    fun use(key: K) {
        cooldowns[key] = System.currentTimeMillis() + duration.toMillis()
    }

    fun tryUse(key: K): Boolean {
        if (!isReady(key)) return false
        use(key)
        return true
    }

    fun remaining(key: K): Duration {
        val expiry = cooldowns[key] ?: return Duration.ZERO
        val remaining = expiry - System.currentTimeMillis()
        return if (remaining > 0) Duration.ofMillis(remaining) else Duration.ZERO
    }

    fun reset(key: K) { cooldowns.remove(key) }

    fun resetAll() = cooldowns.clear()

    fun cleanup() {
        val now = System.currentTimeMillis()
        cooldowns.entries.removeIf { it.value <= now }
    }
}

private val miniMessage = MiniMessage.miniMessage()

private data class NamedKey(val uuid: UUID, val name: String)

object NamedCooldown {

    private val cooldowns = ConcurrentHashMap<NamedKey, Long>()
    private val configs = ConcurrentHashMap<String, NamedCooldownConfig>()

    fun register(config: NamedCooldownConfig) {
        configs[config.name] = config
    }

    fun check(player: Player, name: String): Boolean {
        val config = configs[name] ?: return true
        val key = NamedKey(player.uuid, name)
        val expiry = cooldowns[key] ?: return true
        val now = System.currentTimeMillis()
        if (now >= expiry) {
            cooldowns.remove(key)
            return true
        }
        config.warningMessage?.let { template ->
            val remainingSec = "%.1f".format((expiry - now) / 1000.0)
            player.sendMessage(miniMessage.deserialize(template.replace("{remaining}", remainingSec)))
        }
        return false
    }

    fun use(player: Player, name: String) {
        val config = configs[name] ?: return
        cooldowns[NamedKey(player.uuid, name)] = System.currentTimeMillis() + config.duration.toMillis()
    }

    fun tryUse(player: Player, name: String): Boolean {
        if (!check(player, name)) return false
        use(player, name)
        return true
    }

    fun remaining(player: Player, name: String): Duration {
        val expiry = cooldowns[NamedKey(player.uuid, name)] ?: return Duration.ZERO
        val remaining = expiry - System.currentTimeMillis()
        return if (remaining > 0) Duration.ofMillis(remaining) else Duration.ZERO
    }

    fun reset(player: Player, name: String) {
        cooldowns.remove(NamedKey(player.uuid, name))
    }

    fun resetAll(player: Player) {
        cooldowns.keys.removeAll { it.uuid == player.uuid }
    }

    fun cleanup() {
        val now = System.currentTimeMillis()
        cooldowns.entries.removeIf { it.value <= now }
    }
}

data class NamedCooldownConfig(
    val name: String,
    val duration: Duration,
    val warningMessage: String?,
)

class NamedCooldownBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal var duration: Duration = Duration.ofSeconds(5)
    @PublishedApi internal var warningMessage: String? = null

    fun duration(duration: Duration) { this.duration = duration }
    fun message(template: String) { warningMessage = template }

    @PublishedApi internal fun build(): NamedCooldownConfig =
        NamedCooldownConfig(name, duration, warningMessage)
}

inline fun namedCooldown(name: String, block: NamedCooldownBuilder.() -> Unit): NamedCooldownConfig {
    val config = NamedCooldownBuilder(name).apply(block).build()
    NamedCooldown.register(config)
    return config
}

fun Player.isOnCooldown(name: String): Boolean = !NamedCooldown.check(this, name)
fun Player.useCooldown(name: String): Boolean = NamedCooldown.tryUse(this, name)
fun Player.cooldownRemaining(name: String): Duration = NamedCooldown.remaining(this, name)

private data class MaterialKey(val uuid: UUID, val material: Material)

object MaterialCooldown {

    private val cooldowns = ConcurrentHashMap<MaterialKey, Long>()
    @Volatile private var cleanupInstalled = false

    fun installCleanup() {
        if (cleanupInstalled) return
        cleanupInstalled = true
        MinecraftServer.getSchedulerManager()
            .buildTask { cleanup() }
            .repeat(TaskSchedule.tick(100))
            .schedule()
    }

    fun set(player: Player, material: Material, ticks: Int) {
        require(ticks > 0) { "Ticks must be positive" }
        cooldowns[MaterialKey(player.uuid, material)] = System.currentTimeMillis() + (ticks * 50L)
    }

    fun has(player: Player, material: Material): Boolean {
        val key = MaterialKey(player.uuid, material)
        val expiry = cooldowns[key] ?: return false
        if (System.currentTimeMillis() >= expiry) {
            cooldowns.remove(key)
            return false
        }
        return true
    }

    fun remaining(player: Player, material: Material): Int {
        val expiry = cooldowns[MaterialKey(player.uuid, material)] ?: return 0
        val remainingMs = expiry - System.currentTimeMillis()
        return if (remainingMs > 0) (remainingMs / 50).toInt() else 0
    }

    fun clear(player: Player, material: Material) {
        cooldowns.remove(MaterialKey(player.uuid, material))
    }

    fun clearAll(player: Player) {
        cooldowns.keys.removeAll { it.uuid == player.uuid }
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        cooldowns.entries.removeIf { it.value <= now }
    }
}

fun Player.setItemCooldown(material: Material, ticks: Int) = MaterialCooldown.set(this, material, ticks)
fun Player.hasItemCooldown(material: Material): Boolean = MaterialCooldown.has(this, material)
fun Player.itemCooldownRemaining(material: Material): Int = MaterialCooldown.remaining(this, material)
fun Player.clearItemCooldown(material: Material) = MaterialCooldown.clear(this, material)

enum class CooldownIndicator { BOSS_BAR, ACTION_BAR, ITEM_COOLDOWN, NONE }

data class SkillConfig(
    val name: String,
    val duration: KDuration,
    val indicator: CooldownIndicator,
    val onReady: ((Player) -> Unit)?,
)

private data class SkillEntry(
    val expiresAt: Long,
    val skill: SkillConfig,
    @Volatile var displayTask: Task? = null,
    @Volatile var bossBar: BossBar? = null,
)

private data class PlayerSkillKey(val uuid: UUID, val skillName: String)

object SkillCooldown {

    private val skills = ConcurrentHashMap<String, SkillConfig>()
    private val cooldowns = ConcurrentHashMap<PlayerSkillKey, SkillEntry>()

    fun register(config: SkillConfig) {
        skills[config.name] = config
    }

    fun unregister(name: String) {
        skills.remove(name)
        cooldowns.entries.removeIf { it.key.skillName == name }
    }

    operator fun get(name: String): SkillConfig? = skills[name]

    fun use(player: Player, skillName: String): Boolean {
        val skill = requireNotNull(skills[skillName]) { "Skill '$skillName' not registered" }
        val key = PlayerSkillKey(player.uuid, skillName)
        val existing = cooldowns[key]
        if (existing != null && System.currentTimeMillis() < existing.expiresAt) return false
        val expiresAt = System.currentTimeMillis() + skill.duration.inWholeMilliseconds
        val entry = SkillEntry(expiresAt, skill)
        cooldowns[key] = entry
        startIndicator(player, entry, key)
        return true
    }

    fun isReady(player: Player, skillName: String): Boolean {
        val key = PlayerSkillKey(player.uuid, skillName)
        val entry = cooldowns[key] ?: return true
        if (System.currentTimeMillis() >= entry.expiresAt) {
            cleanupEntry(key, entry)
            return true
        }
        return false
    }

    fun remaining(player: Player, skillName: String): KDuration {
        val key = PlayerSkillKey(player.uuid, skillName)
        val entry = cooldowns[key] ?: return KDuration.ZERO
        val remaining = entry.expiresAt - System.currentTimeMillis()
        return if (remaining > 0) (remaining / 1000.0).seconds else KDuration.ZERO
    }

    fun reset(player: Player, skillName: String) {
        val key = PlayerSkillKey(player.uuid, skillName)
        val entry = cooldowns.remove(key) ?: return
        entry.displayTask?.cancel()
        entry.bossBar?.let { bar ->
            MinecraftServer.getConnectionManager().onlinePlayers
                .firstOrNull { it.uuid == player.uuid }
                ?.hideBossBar(bar)
        }
    }

    fun clearPlayer(player: Player) {
        cooldowns.keys.filter { it.uuid == player.uuid }.forEach { key ->
            cooldowns.remove(key)?.let { entry ->
                entry.displayTask?.cancel()
                entry.bossBar?.let { bar -> player.hideBossBar(bar) }
            }
        }
    }

    fun clearAll() {
        cooldowns.values.forEach { it.displayTask?.cancel() }
        cooldowns.clear()
    }

    private fun startIndicator(player: Player, entry: SkillEntry, key: PlayerSkillKey) {
        when (entry.skill.indicator) {
            CooldownIndicator.BOSS_BAR -> startBossBarIndicator(player, entry, key)
            CooldownIndicator.ACTION_BAR -> startActionBarIndicator(player, entry, key)
            CooldownIndicator.ITEM_COOLDOWN -> {}
            CooldownIndicator.NONE -> scheduleReadyCallback(player, entry, key)
        }
    }

    private fun startBossBarIndicator(player: Player, entry: SkillEntry, key: PlayerSkillKey) {
        val bar = BossBar.bossBar(mm("<yellow>${entry.skill.name}"), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS)
        entry.bossBar = bar
        player.showBossBar(bar)
        val totalMs = entry.skill.duration.inWholeMilliseconds.toFloat()
        entry.displayTask = MinecraftServer.getSchedulerManager()
            .buildTask {
                val remaining = entry.expiresAt - System.currentTimeMillis()
                if (remaining <= 0) {
                    cleanupEntry(key, entry)
                    player.hideBossBar(bar)
                    entry.skill.onReady?.invoke(player)
                } else {
                    bar.progress((remaining / totalMs).coerceIn(0f, 1f))
                    bar.name(mm("<yellow>${entry.skill.name} <gray>${"%.1f".format(remaining / 1000.0)}s"))
                }
            }
            .repeat(TaskSchedule.tick(2))
            .schedule()
    }

    private fun startActionBarIndicator(player: Player, entry: SkillEntry, key: PlayerSkillKey) {
        val totalMs = entry.skill.duration.inWholeMilliseconds.toFloat()
        entry.displayTask = MinecraftServer.getSchedulerManager()
            .buildTask {
                val remaining = entry.expiresAt - System.currentTimeMillis()
                if (remaining <= 0) {
                    cleanupEntry(key, entry)
                    player.sendActionBar(mm("<green>${entry.skill.name} ready!"))
                    entry.skill.onReady?.invoke(player)
                } else {
                    val progress = remaining / totalMs
                    val barLength = 20
                    val filled = ((1f - progress) * barLength).toInt()
                    val empty = barLength - filled
                    val bar = "<green>${"|".repeat(filled)}<gray>${"|".repeat(empty)}"
                    player.sendActionBar(mm("<yellow>${entry.skill.name} $bar <white>${"%.1f".format(remaining / 1000.0)}s"))
                }
            }
            .repeat(TaskSchedule.tick(2))
            .schedule()
    }

    private fun scheduleReadyCallback(player: Player, entry: SkillEntry, key: PlayerSkillKey) {
        entry.displayTask = MinecraftServer.getSchedulerManager()
            .buildTask {
                if (System.currentTimeMillis() >= entry.expiresAt) {
                    cleanupEntry(key, entry)
                    entry.skill.onReady?.invoke(player)
                }
            }
            .repeat(TaskSchedule.tick(5))
            .schedule()
    }

    private fun cleanupEntry(key: PlayerSkillKey, entry: SkillEntry) {
        entry.displayTask?.cancel()
        entry.displayTask = null
        cooldowns.remove(key)
    }
}

class SkillCooldownBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal var duration: KDuration = 10.seconds
    @PublishedApi internal var indicator: CooldownIndicator = CooldownIndicator.ACTION_BAR
    @PublishedApi internal var onReadyHandler: ((Player) -> Unit)? = null

    fun duration(duration: KDuration) { this.duration = duration }
    fun indicator(indicator: CooldownIndicator) { this.indicator = indicator }
    fun onReady(handler: (Player) -> Unit) { onReadyHandler = handler }

    @PublishedApi internal fun build(): SkillConfig =
        SkillConfig(name, duration, indicator, onReadyHandler)
}

inline fun skillCooldown(name: String, block: SkillCooldownBuilder.() -> Unit): SkillConfig {
    val config = SkillCooldownBuilder(name).apply(block).build()
    SkillCooldown.register(config)
    return config
}

fun Player.useSkill(skillName: String): Boolean = SkillCooldown.use(this, skillName)
fun Player.isSkillReady(skillName: String): Boolean = SkillCooldown.isReady(this, skillName)
fun Player.skillRemaining(skillName: String): KDuration = SkillCooldown.remaining(this, skillName)

class MessageCooldownManager @PublishedApi internal constructor(
    private val cooldown: Duration,
    private val warningMessage: String?,
) {

    private val timestamps = ConcurrentHashMap<UUID, Long>()

    fun canSend(player: Player): Boolean {
        val last = timestamps[player.uuid] ?: return true
        return System.currentTimeMillis() - last >= cooldown.toMillis()
    }

    fun recordMessage(player: Player) {
        timestamps[player.uuid] = System.currentTimeMillis()
    }

    fun tryUse(player: Player): Boolean {
        if (!canSend(player)) {
            warningMessage?.let { player.sendMessage(miniMessage.deserialize(it)) }
            return false
        }
        recordMessage(player)
        return true
    }

    fun getRemaining(player: Player): Duration {
        val last = timestamps[player.uuid] ?: return Duration.ZERO
        val elapsed = System.currentTimeMillis() - last
        val remaining = cooldown.toMillis() - elapsed
        return if (remaining > 0) Duration.ofMillis(remaining) else Duration.ZERO
    }

    fun reset(player: Player) { timestamps.remove(player.uuid) }
    fun resetAll() { timestamps.clear() }

    fun cleanup() {
        val threshold = System.currentTimeMillis() - cooldown.toMillis()
        timestamps.entries.removeIf { it.value < threshold }
    }

    fun Player.canSendMessage(): Boolean = canSend(this)
    fun Player.tryMessage(): Boolean = tryUse(this)
    fun Player.messageRemaining(): Duration = getRemaining(this)
}

class MessageCooldownBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var cooldown: Duration = Duration.ofSeconds(3)
    @PublishedApi internal var warningMessage: String? = null

    fun cooldown(duration: Duration) { cooldown = duration }
    fun cooldownMillis(millis: Long) { cooldown = Duration.ofMillis(millis) }
    fun cooldownSeconds(seconds: Long) { cooldown = Duration.ofSeconds(seconds) }
    fun warningMessage(message: String) { warningMessage = message }

    @PublishedApi internal fun build(): MessageCooldownManager =
        MessageCooldownManager(cooldown, warningMessage)
}

inline fun messageCooldown(builder: MessageCooldownBuilder.() -> Unit): MessageCooldownManager =
    MessageCooldownBuilder().apply(builder).build()
