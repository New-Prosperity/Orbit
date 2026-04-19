package me.nebula.orbit.config

import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.config.ConfigSchedule
import me.nebula.gravity.config.ConfigScheduleRecurrence
import me.nebula.gravity.config.ConfigScheduler
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.confirmGui
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.gui.paginatedGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object ConfigSchedulesMenu {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
        .withZone(ZoneOffset.UTC)

    fun open(player: Player) {
        val schedules = runCatching { ConfigScheduler.list().toList() }.getOrDefault(emptyList())
        val title = player.translateRaw("orbit.config.schedules.title".asTranslationKey())
        val paginated = paginatedGui(title, rows = 6) {
            borderDefault()
            clickSound(SoundEvent.UI_BUTTON_CLICK)
            key("config-schedules")

            items<ConfigSchedule>(schedules, transform = ::scheduleItem) { p, schedule ->
                openInspect(p, schedule)
            }

            staticSlot(49, itemStack(Material.ARROW) { name("<gray>Back"); clean() }) { p ->
                ConfigMainMenu.open(p)
            }
        }
        paginated.openForPlayer(player)
    }

    private fun openInspect(player: Player, schedule: ConfigSchedule) {
        val title = player.translateRaw(
            "orbit.config.schedules.inspect_title".asTranslationKey(),
            "id" to schedule.id.toString(),
        )
        val menu = gui(title, rows = 5) {
            fillDefault()
            clickSound(SoundEvent.UI_BUTTON_CLICK)
            slot(13, scheduleItem(schedule))
            slot(31, itemStack(Material.BARRIER) {
                name("<red><bold>Cancel Schedule")
                lore("<gray>Click to cancel #${schedule.id}")
                clean()
            }) { p ->
                val confirm = confirmGui(
                    title = p.translateRaw("orbit.config.schedules.cancel_title".asTranslationKey()),
                    message = p.translateRaw(
                        "orbit.config.schedules.cancel_message".asTranslationKey(),
                        "id" to schedule.id.toString(),
                    ),
                    onConfirm = { pp ->
                        runCatching { ConfigScheduler.cancel(schedule.id) } // noqa: dangling runCatching
                        open(pp)
                    },
                    onCancel = { pp -> openInspect(pp, schedule) },
                )
                p.openGui(confirm)
            }
            slot(40, itemStack(Material.ARROW) { name("<gray>Back"); clean() }) { p -> open(p) }
        }
        player.openGui(menu)
    }

    private fun scheduleItem(schedule: ConfigSchedule): ItemStack = itemStack(Material.CLOCK) {
        name("<gold>#${schedule.id} <dark_gray>· <yellow>${schedule.entryKey}")
        lore("<gray>Apply at: <white>${timeFormatter.format(Instant.ofEpochMilli(schedule.applyAt))}")
        schedule.revertAfterMs?.let {
            lore("<gray>Auto-revert after: <white>${it}ms")
        }
        schedule.recurrence?.let { rec ->
            lore("<gray>Recurrence: <light_purple>${recurrenceLabel(rec)}")
        }
        schedule.qualifier?.let { lore("<gray>Qualifier: <white>$it") }
        schedule.actorName?.let { lore("<gray>Actor: <yellow>$it") }
        schedule.reason?.let { lore("<gray>Reason: <white>$it") }
        if (schedule.hasPendingRevert) {
            lore("<gold>⏪ Pending revert")
        }
        emptyLoreLine()
        lore("<yellow>Click to inspect")
        clean()
    }

    private fun recurrenceLabel(rec: ConfigScheduleRecurrence): String = when (rec) {
        is ConfigScheduleRecurrence.Every -> "every ${rec.intervalMs}ms"
        is ConfigScheduleRecurrence.DailyAt -> "daily at ${rec.hour}:${rec.minute.toString().padStart(2, '0')} ${rec.zoneId}"
        is ConfigScheduleRecurrence.WeeklyAt -> "weekly day=${rec.dayOfWeek} at ${rec.hour}:${rec.minute.toString().padStart(2, '0')} ${rec.zoneId}"
    }
}
