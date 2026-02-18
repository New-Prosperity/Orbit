package me.nebula.orbit.mechanic.goathorn

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.utils.cooldown.Cooldown
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.time.Duration
import java.util.UUID

private val HORN_SOUNDS = (0..7).map { Key.key("minecraft:item.goat_horn.sound.$it") }

private val HORN_VARIANT_TAG = Tag.Integer("mechanic:goathorn:variant").defaultValue(0)

class GoatHornModule : OrbitModule("goat-horn") {

    private val cooldown = Cooldown<UUID>(Duration.ofSeconds(7))

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            if (event.itemStack.material() != Material.GOAT_HORN) return@addListener

            val player = event.player
            if (!cooldown.tryUse(player.uuid)) return@addListener

            val variant = (event.itemStack.getTag(HORN_VARIANT_TAG)).coerceIn(0, 7)
            val soundKey = HORN_SOUNDS[variant]

            player.instance?.playSound(
                Sound.sound(soundKey, Sound.Source.RECORD, 16f, 1f),
                player.position.x(), player.position.y(), player.position.z(),
            )
        }
    }

    override fun onDisable() {
        cooldown.resetAll()
        super.onDisable()
    }
}
