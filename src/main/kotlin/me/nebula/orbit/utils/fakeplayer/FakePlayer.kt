package me.nebula.orbit.utils.fakeplayer

import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.scheduler.delay
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.packet.server.configuration.SelectKnownPacksPacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class FakePlayerConnection : PlayerConnection() {

    private val fakeAddress: SocketAddress = InetSocketAddress("127.0.0.1", 25565)

    override fun sendPacket(packet: SendablePacket) {}

    override fun getRemoteAddress(): SocketAddress = fakeAddress

    override fun disconnect() {
        runCatching {
            val field = PlayerConnection::class.java.getDeclaredField("online")
            field.isAccessible = true
            field.setBoolean(this, false)
        }
    }
}

enum class BotBehavior {
    IDLE,
    WANDER,
    FOLLOW_NEAREST,
    CIRCLE,
    SPRINT_RANDOM,
    LOOK_AROUND,
}

data class BotProfile(
    val skin: PlayerSkin? = null,
    val gameMode: GameMode = GameMode.ADVENTURE,
    val equipment: Map<EquipmentSlot, ItemStack> = emptyMap(),
    val hotbarItems: Map<Int, ItemStack> = emptyMap(),
    val behavior: BotBehavior = BotBehavior.IDLE,
    val sneaking: Boolean = false,
    val sprinting: Boolean = false,
    val onReady: ((Player) -> Unit)? = null,
)

val BOT_TAG: Tag<Boolean> = Tag.Boolean("nebula:fake_player").defaultValue(false)

private val BOT_BEHAVIOR_TAG: Tag<String> = Tag.String("nebula:bot_behavior")
private val BOT_ANGLE_TAG: Tag<Float> = Tag.Float("nebula:bot_angle")
private val BOT_ORIGIN_X_TAG: Tag<Double> = Tag.Double("nebula:bot_origin_x")
private val BOT_ORIGIN_Z_TAG: Tag<Double> = Tag.Double("nebula:bot_origin_z")

object FakePlayerManager {

    private val activeBots = ConcurrentHashMap<UUID, Player>()
    private val botIndex = AtomicInteger(0)
    private var behaviorTask: Task? = null
    private val readyCallbacks = CopyOnWriteArrayList<Pair<UUID, (Player) -> Unit>>()

    val botCount: Int get() = activeBots.size

    fun spawn(
        instance: Instance,
        spawnPos: Pos,
        count: Int = 1,
        profile: BotProfile = BotProfile(),
        staggerDelayTicks: Int = 2,
    ): List<UUID> {
        val uuids = mutableListOf<UUID>()
        val connectionManager = MinecraftServer.getConnectionManager()

        for (i in 0 until count) {
            val index = botIndex.getAndIncrement()
            val uuid = UUID(0x00FA0EB0_00000000L or index.toLong(), index.toLong())
            val name = generateBotName(index)
            uuids.add(uuid)

            val delayTicks = i * staggerDelayTicks
            delay(delayTicks.coerceAtLeast(1)) {
                Thread.startVirtualThread {
                    val connection = FakePlayerConnection()
                    val skinProperties = profile.skin?.let {
                        listOf(GameProfile.Property("textures", it.textures(), it.signature()))
                    } ?: emptyList()
                    val gameProfile = GameProfile(uuid, name, skinProperties)

                    val player = connectionManager.createPlayer(connection, gameProfile)
                    connection.setServerState(ConnectionState.CONFIGURATION)
                    connection.setClientState(ConnectionState.CONFIGURATION)
                    connection.receiveKnownPacksResponse(listOf(SelectKnownPacksPacket.MINECRAFT_CORE))
                    connectionManager.doConfiguration(player, true)
                    connectionManager.transitionConfigToPlay(player)

                    player.setTag(BOT_TAG, true)
                    player.setTag(BOT_BEHAVIOR_TAG, profile.behavior.name)
                    player.setTag(BOT_ORIGIN_X_TAG, spawnPos.x())
                    player.setTag(BOT_ORIGIN_Z_TAG, spawnPos.z())
                    player.setTag(BOT_ANGLE_TAG, Random.nextFloat() * 360f)
                    player.gameMode = profile.gameMode
                    player.isSneaking = profile.sneaking
                    player.isSprinting = profile.sprinting

                    profile.equipment.forEach { (slot, item) -> player.setEquipment(slot, item) }
                    profile.hotbarItems.forEach { (slot, item) -> player.inventory.setItemStack(slot, item) }

                    activeBots[uuid] = player

                    profile.onReady?.let { callback ->
                        delay(5) { callback(player) }
                    }
                }
            }
        }

        ensureBehaviorTask()
        return uuids
    }

    fun get(uuid: UUID): Player? = activeBots[uuid]

    fun isBot(player: Player): Boolean = player.getTag(BOT_TAG)

    fun remove(uuid: UUID) {
        val player = activeBots.remove(uuid) ?: return
        player.playerConnection.disconnect()
        MinecraftServer.getConnectionManager().removePlayer(player.playerConnection)
    }

    fun removeAll() {
        activeBots.keys.toList().forEach { remove(it) }
        behaviorTask?.cancel()
        behaviorTask = null
        botIndex.set(0)
    }

    fun removeRandom(count: Int) {
        activeBots.keys.toList().shuffled().take(count).forEach { remove(it) }
    }

    fun all(): Collection<Player> = activeBots.values.toList()

    fun setBehavior(player: Player, behavior: BotBehavior) {
        player.setTag(BOT_BEHAVIOR_TAG, behavior.name)
        if (behavior == BotBehavior.CIRCLE) {
            player.setTag(BOT_ORIGIN_X_TAG, player.position.x())
            player.setTag(BOT_ORIGIN_Z_TAG, player.position.z())
        }
    }

    fun setBehaviorAll(behavior: BotBehavior) {
        activeBots.values.forEach { setBehavior(it, behavior) }
    }

    fun equipAll(slot: EquipmentSlot, item: ItemStack) {
        activeBots.values.forEach { it.setEquipment(slot, item) }
    }

    fun equipRandomArmor() {
        val helmets = listOf(Material.LEATHER_HELMET, Material.IRON_HELMET, Material.DIAMOND_HELMET, Material.NETHERITE_HELMET)
        val chestplates = listOf(Material.LEATHER_CHESTPLATE, Material.IRON_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE)
        val leggings = listOf(Material.LEATHER_LEGGINGS, Material.IRON_LEGGINGS, Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS)
        val boots = listOf(Material.LEATHER_BOOTS, Material.IRON_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS)

        activeBots.values.forEach { bot ->
            bot.setEquipment(EquipmentSlot.HELMET, ItemStack.of(helmets.random()))
            bot.setEquipment(EquipmentSlot.CHESTPLATE, ItemStack.of(chestplates.random()))
            bot.setEquipment(EquipmentSlot.LEGGINGS, ItemStack.of(leggings.random()))
            bot.setEquipment(EquipmentSlot.BOOTS, ItemStack.of(boots.random()))
        }
    }

    fun giveRandomItems() {
        val weapons = listOf(Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD)
        val tools = listOf(Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.SHIELD)
        val food = listOf(Material.GOLDEN_APPLE, Material.COOKED_BEEF, Material.BREAD, Material.GOLDEN_CARROT)

        activeBots.values.forEach { bot ->
            bot.inventory.setItemStack(0, ItemStack.of(weapons.random()))
            if (Random.nextBoolean()) bot.inventory.setItemStack(1, ItemStack.of(tools.random()))
            bot.inventory.setItemStack(8, ItemStack.of(food.random(), Random.nextInt(1, 16)))
        }
    }

    fun applySkin(uuid: UUID, skin: PlayerSkin) {
        val player = activeBots[uuid] ?: return
        player.skin = skin
    }

    fun applySkinAll(skin: PlayerSkin) {
        activeBots.values.forEach { it.skin = skin }
    }

    fun applyRandomSkins(skins: List<PlayerSkin>) {
        if (skins.isEmpty()) return
        activeBots.values.forEach { it.skin = skins.random() }
    }

    fun forEach(action: (Player) -> Unit) {
        activeBots.values.forEach { if (it.isOnline) action(it) }
    }

    private fun ensureBehaviorTask() {
        if (behaviorTask != null) return
        behaviorTask = repeat(4) { tickBehaviors() }
    }

    private fun tickBehaviors() {
        val dead = mutableListOf<UUID>()
        for ((uuid, bot) in activeBots) {
            if (!bot.isOnline) {
                dead.add(uuid)
                continue
            }
            val behavior = runCatching { BotBehavior.valueOf(bot.getTag(BOT_BEHAVIOR_TAG) ?: "IDLE") }
                .getOrDefault(BotBehavior.IDLE)
            when (behavior) {
                BotBehavior.IDLE -> {}
                BotBehavior.WANDER -> tickWander(bot)
                BotBehavior.FOLLOW_NEAREST -> tickFollowNearest(bot)
                BotBehavior.CIRCLE -> tickCircle(bot)
                BotBehavior.SPRINT_RANDOM -> tickSprintRandom(bot)
                BotBehavior.LOOK_AROUND -> tickLookAround(bot)
            }
        }
        dead.forEach { activeBots.remove(it) }
    }

    private fun tickWander(bot: Player) {
        val pos = bot.position
        val angle = Random.nextDouble() * Math.PI * 2
        val dist = Random.nextDouble() * 1.5
        val target = pos.add(cos(angle) * dist, 0.0, sin(angle) * dist)
        val yaw = (-Math.toDegrees(Math.atan2(target.x() - pos.x(), target.z() - pos.z()))).toFloat()
        bot.teleport(target.withView(yaw, 0f))
    }

    private fun tickFollowNearest(bot: Player) {
        val instance = bot.instance ?: return
        val nearest = instance.players
            .filter { it !== bot && !isBot(it) }
            .minByOrNull { it.position.distanceSquared(bot.position) } ?: return
        val dir = nearest.position.sub(bot.position)
        val dist = bot.position.distance(nearest.position)
        if (dist < 3.0) return
        val speed = 0.3.coerceAtMost(dist * 0.1)
        val norm = Vec(dir.x() / dist, 0.0, dir.z() / dist)
        val target = bot.position.add(norm.x() * speed, 0.0, norm.z() * speed)
        val yaw = (-Math.toDegrees(Math.atan2(norm.x(), norm.z()))).toFloat()
        bot.teleport(target.withView(yaw, 0f))
    }

    private fun tickCircle(bot: Player) {
        val originX = bot.getTag(BOT_ORIGIN_X_TAG) ?: bot.position.x()
        val originZ = bot.getTag(BOT_ORIGIN_Z_TAG) ?: bot.position.z()
        val angle = (bot.getTag(BOT_ANGLE_TAG) ?: 0f) + 3f
        bot.setTag(BOT_ANGLE_TAG, angle % 360f)
        val rad = Math.toRadians(angle.toDouble())
        val radius = 5.0
        val x = originX + cos(rad) * radius
        val z = originZ + sin(rad) * radius
        val yaw = (-Math.toDegrees(Math.atan2(cos(rad), -sin(rad)))).toFloat()
        bot.teleport(Pos(x, bot.position.y(), z, yaw, 0f))
    }

    private fun tickSprintRandom(bot: Player) {
        bot.isSprinting = true
        tickWander(bot)
    }

    private fun tickLookAround(bot: Player) {
        val yaw = bot.position.yaw() + Random.nextFloat() * 10f - 5f
        val pitch = (Random.nextFloat() * 30f - 15f).coerceIn(-45f, 45f)
        bot.teleport(bot.position.withView(yaw, pitch))
    }

    private fun generateBotName(index: Int): String {
        val prefixes = listOf("Cool", "Dark", "Epic", "Fast", "Pro", "Big", "Red", "Ice", "Sky", "Neo")
        val suffixes = listOf("Player", "Gamer", "King", "Star", "Wolf", "Fox", "Cat", "Ace", "Rex", "Max")
        return "${prefixes[index % prefixes.size]}${suffixes[(index / prefixes.size) % suffixes.size]}${index}"
    }
}

class BotProfileBuilder @PublishedApi internal constructor() {
    @PublishedApi internal var skin: PlayerSkin? = null
    @PublishedApi internal var gameMode: GameMode = GameMode.ADVENTURE
    @PublishedApi internal val equipment = mutableMapOf<EquipmentSlot, ItemStack>()
    @PublishedApi internal val hotbarItems = mutableMapOf<Int, ItemStack>()
    @PublishedApi internal var behavior: BotBehavior = BotBehavior.IDLE
    @PublishedApi internal var sneaking: Boolean = false
    @PublishedApi internal var sprinting: Boolean = false
    @PublishedApi internal var onReady: ((Player) -> Unit)? = null

    fun skin(skin: PlayerSkin) { this.skin = skin }
    fun skin(textures: String, signature: String) { this.skin = PlayerSkin(textures, signature) }
    fun gameMode(mode: GameMode) { gameMode = mode }
    fun behavior(b: BotBehavior) { behavior = b }
    fun sneaking(value: Boolean = true) { sneaking = value }
    fun sprinting(value: Boolean = true) { sprinting = value }

    fun helmet(item: ItemStack) { equipment[EquipmentSlot.HELMET] = item }
    fun helmet(material: Material) { equipment[EquipmentSlot.HELMET] = ItemStack.of(material) }
    fun chestplate(item: ItemStack) { equipment[EquipmentSlot.CHESTPLATE] = item }
    fun chestplate(material: Material) { equipment[EquipmentSlot.CHESTPLATE] = ItemStack.of(material) }
    fun leggings(item: ItemStack) { equipment[EquipmentSlot.LEGGINGS] = item }
    fun leggings(material: Material) { equipment[EquipmentSlot.LEGGINGS] = ItemStack.of(material) }
    fun boots(item: ItemStack) { equipment[EquipmentSlot.BOOTS] = item }
    fun boots(material: Material) { equipment[EquipmentSlot.BOOTS] = ItemStack.of(material) }
    fun mainHand(item: ItemStack) { equipment[EquipmentSlot.MAIN_HAND] = item }
    fun offHand(item: ItemStack) { equipment[EquipmentSlot.OFF_HAND] = item }

    fun hotbarItem(slot: Int, item: ItemStack) { hotbarItems[slot] = item }
    fun hotbarItem(slot: Int, material: Material) { hotbarItems[slot] = ItemStack.of(material) }

    fun onReady(handler: (Player) -> Unit) { onReady = handler }

    @PublishedApi internal fun build(): BotProfile = BotProfile(
        skin, gameMode, equipment.toMap(), hotbarItems.toMap(),
        behavior, sneaking, sprinting, onReady,
    )
}

class FakePlayerSpawnBuilder @PublishedApi internal constructor() {
    @PublishedApi internal lateinit var instance: Instance
    @PublishedApi internal var spawnPos: Pos = Pos(0.0, 64.0, 0.0)
    @PublishedApi internal var count: Int = 1
    @PublishedApi internal var staggerTicks: Int = 2
    @PublishedApi internal var profile: BotProfile = BotProfile()

    fun instance(instance: Instance) { this.instance = instance }
    fun position(pos: Pos) { spawnPos = pos }
    fun count(n: Int) { count = n }
    fun stagger(ticks: Int) { staggerTicks = ticks }

    inline fun profile(block: BotProfileBuilder.() -> Unit) {
        profile = BotProfileBuilder().apply(block).build()
    }

    @PublishedApi internal fun execute(): List<UUID> =
        FakePlayerManager.spawn(instance, spawnPos, count, profile, staggerTicks)
}

inline fun spawnFakePlayers(block: FakePlayerSpawnBuilder.() -> Unit): List<UUID> =
    FakePlayerSpawnBuilder().apply(block).execute()

fun Player.isBot(): Boolean = getTag(BOT_TAG)
