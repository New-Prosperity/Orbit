package me.nebula.orbit.config

import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.config.ConfigDraft
import me.nebula.gravity.config.ConfigDrafts
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.AnvilInput
import me.nebula.orbit.utils.gui.AnvilResult
import me.nebula.orbit.utils.gui.confirmGui
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.gui.paginatedGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object ConfigDraftsMenu {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
        .withZone(ZoneOffset.UTC)

    fun open(player: Player) {
        val drafts = runCatching { ConfigDrafts.list().toList() }.getOrDefault(emptyList())
        val title = player.translateRaw("orbit.config.drafts.title".asTranslationKey())
        val paginated = paginatedGui(title, rows = 6) {
            borderDefault()
            clickSound(SoundEvent.UI_BUTTON_CLICK)
            key("config-drafts")

            items<ConfigDraft>(drafts, transform = ::draftItem) { p, draft ->
                openInspect(p, draft.name)
            }

            staticSlot(45, itemStack(Material.WRITABLE_BOOK) {
                name("<green><bold>New Draft")
                lore("<gray>Click to create a new draft")
                clean()
            }) { p ->
                AnvilInput.open(
                    player = p,
                    title = p.translateRaw("orbit.config.drafts.new_title".asTranslationKey()),
                    default = "new-draft",
                    maxLength = 32,
                ) { result ->
                    when (result) {
                        is AnvilResult.Submitted -> {
                            val name = result.text.trim()
                            if (name.isBlank()) {
                                open(p)
                                return@open
                            }
                            runCatching {
                                ConfigDrafts.create(name, owner = p.uuid, ownerName = p.username)
                            }.onFailure {
                                p.sendMessage(Component.text("Failed to create draft: ${it.message}"))
                            }
                            open(p)
                        }
                        AnvilResult.Cancelled -> open(p)
                    }
                }
            }

            staticSlot(49, itemStack(Material.ARROW) { name("<gray>Back"); clean() }) { p ->
                ConfigMainMenu.open(p)
            }
        }
        paginated.openForPlayer(player)
    }

    private fun openInspect(player: Player, draftName: String) {
        val draft = try { ConfigDrafts.get(draftName) } catch (_: Throwable) { null }
        if (draft == null) {
            open(player)
            return
        }
        val diff = runCatching { ConfigDrafts.diff(draftName) }.getOrDefault(emptyList())
        val title = player.translateRaw(
            "orbit.config.drafts.inspect_title".asTranslationKey(),
            "name" to draft.name,
        )
        val paginated = paginatedGui(title, rows = 6) {
            borderDefault()
            clickSound(SoundEvent.UI_BUTTON_CLICK)
            key("config-draft-$draftName")

            diff.forEach { diffEntry ->
                item(itemStack(Material.PAPER) {
                    name("<yellow>${diffEntry.entryKey}")
                    diffEntry.qualifier?.let { lore("<gray>Qualifier: <white>$it") }
                    val prev = diffEntry.previousRaw
                    if (prev != null) {
                        lore("<gray>Was: <red>${prev.take(60)}")
                    } else {
                        lore("<gray>Was: <dark_gray>(unset)")
                    }
                    val next = diffEntry.newRaw
                    if (next != null) {
                        lore("<gray>Will be: <green>${next.take(60)}")
                    } else {
                        lore("<gray>Will be: <dark_red>(deleted)")
                    }
                    clean()
                })
            }

            staticSlot(45, itemStack(Material.LIME_DYE) {
                name("<green><bold>Apply Draft")
                lore("<gray>${diff.size} changes")
                clean()
            }) { p ->
                val confirm = confirmGui(
                    title = p.translateRaw("orbit.config.drafts.apply_title".asTranslationKey()),
                    message = p.translateRaw(
                        "orbit.config.drafts.apply_message".asTranslationKey(),
                        "name" to draft.name,
                    ),
                    onConfirm = { pp ->
                        val result = runCatching {
                            ConfigDrafts.apply(draft.name, actor = pp.uuid, actorName = pp.username)
                        }
                        result.onFailure {
                            pp.sendMessage(Component.text("Apply failed: ${it.message}"))
                        }
                        open(pp)
                    },
                    onCancel = { pp -> openInspect(pp, draft.name) },
                )
                p.openGui(confirm)
            }

            staticSlot(47, itemStack(Material.RED_DYE) {
                name("<red><bold>Discard Draft")
                lore("<gray>Delete without applying")
                clean()
            }) { p ->
                val confirm = confirmGui(
                    title = p.translateRaw("orbit.config.drafts.discard_title".asTranslationKey()),
                    message = p.translateRaw(
                        "orbit.config.drafts.discard_message".asTranslationKey(),
                        "name" to draft.name,
                    ),
                    onConfirm = { pp ->
                        runCatching { ConfigDrafts.discard(draft.name) } // noqa: dangling runCatching
                        open(pp)
                    },
                    onCancel = { pp -> openInspect(pp, draft.name) },
                )
                p.openGui(confirm)
            }

            staticSlot(49, itemStack(Material.ARROW) { name("<gray>Back"); clean() }) { p ->
                open(p)
            }
        }
        paginated.openForPlayer(player)
    }

    private fun draftItem(draft: ConfigDraft): ItemStack = itemStack(Material.WRITABLE_BOOK) {
        name("<gold>${draft.name}")
        draft.description?.let { lore("<gray>$it") }
        lore("<gray>Changes: <white>${draft.size}")
        lore("<gray>Created: <white>${timeFormatter.format(Instant.ofEpochMilli(draft.createdAt))}")
        draft.ownerName?.let { lore("<gray>Owner: <yellow>$it") }
        emptyLoreLine()
        lore("<yellow>Click to inspect")
        clean()
    }
}
