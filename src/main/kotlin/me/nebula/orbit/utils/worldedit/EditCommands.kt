package me.nebula.orbit.utils.worldedit

import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.commandbuilder.command
import net.minestom.server.command.CommandManager
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import me.nebula.gravity.translation.Keys

private const val WAND_TAG = "nebula:wand"

fun installEditCommands(commandManager: CommandManager) {
    commandManager.register(command("/wand") {
        permission("nebula.worldedit")
        onPlayerExecute {
            val wand = ItemStack.of(Material.WOODEN_AXE)
            player.inventory.addItemStack(wand)
            player.sendMessage(player.translate(Keys.Orbit.Build.WandGiven))
        }
    })

    commandManager.register(command("/pos1") {
        permission("nebula.worldedit")
        onPlayerExecute {
            val session = EditSessionManager.get(player)
            session.pos1 = player.position
            SelectionRenderer.update(player, session.selection())
            player.sendMessage(player.translate(Keys.Orbit.Build.Pos1Set, "x" to player.position.blockX().toString(), "y" to player.position.blockY().toString(), "z" to player.position.blockZ().toString()))
        }
    })

    commandManager.register(command("/pos2") {
        permission("nebula.worldedit")
        onPlayerExecute {
            val session = EditSessionManager.get(player)
            session.pos2 = player.position
            SelectionRenderer.update(player, session.selection())
            player.sendMessage(player.translate(Keys.Orbit.Build.Pos2Set, "x" to player.position.blockX().toString(), "y" to player.position.blockY().toString(), "z" to player.position.blockZ().toString()))
        }
    })

    commandManager.register(command("/set") {
        permission("nebula.worldedit")
        wordArgument("pattern")
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            val sel = EditSessionManager.get(player).selection() ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.NoSelection)); return@onPlayerExecute }
            val pattern = Patterns.parse(requireArg("pattern") ?: return@onPlayerExecute) ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.UnknownBlock)); return@onPlayerExecute }
            Thread.startVirtualThread {
                val (result, cs) = EditOperations.set(instance, sel, pattern, player)
                EditSessionManager.get(player).pushHistory(cs)
                player.sendMessage(player.translate(Keys.Orbit.Build.BlocksChanged, "count" to result.blocksChanged.toString(), "time" to result.durationMs.toString()))
            }
        }
    })

    commandManager.register(command("/replace") {
        permission("nebula.worldedit")
        wordArgument("mask")
        wordArgument("pattern")
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            val sel = EditSessionManager.get(player).selection() ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.NoSelection)); return@onPlayerExecute }
            val mask = Masks.parse(requireArg("mask") ?: return@onPlayerExecute)
            val pattern = Patterns.parse(requireArg("pattern") ?: return@onPlayerExecute) ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.UnknownBlock)); return@onPlayerExecute }
            Thread.startVirtualThread {
                val (result, cs) = EditOperations.replace(instance, sel, mask, pattern, player)
                EditSessionManager.get(player).pushHistory(cs)
                player.sendMessage(player.translate(Keys.Orbit.Build.BlocksReplaced, "count" to result.blocksChanged.toString(), "time" to result.durationMs.toString()))
            }
        }
    })

    commandManager.register(command("/copy") {
        permission("nebula.worldedit")
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            val session = EditSessionManager.get(player)
            val sel = session.selection() ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.NoSelection)); return@onPlayerExecute }
            session.clipboard = EditOperations.copy(instance, sel, player.position)
            session.clipboardOrigin = player.position
            player.sendMessage(player.translate(Keys.Orbit.Build.Copied, "volume" to sel.volume.toString()))
        }
    })

    commandManager.register(command("/cut") {
        permission("nebula.worldedit")
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            val session = EditSessionManager.get(player)
            val sel = session.selection() ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.NoSelection)); return@onPlayerExecute }
            session.clipboard = EditOperations.copy(instance, sel, player.position)
            session.clipboardOrigin = player.position
            Thread.startVirtualThread {
                val (result, cs) = EditOperations.set(instance, sel, Patterns.single(Block.AIR), player)
                session.pushHistory(cs)
                player.sendMessage(player.translate(Keys.Orbit.Build.Copied, "volume" to sel.volume.toString()))
            }
        }
    })

    commandManager.register(command("/paste") {
        permission("nebula.worldedit")
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            val session = EditSessionManager.get(player)
            val clipboard = session.clipboard ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.NoClipboard)); return@onPlayerExecute }
            Thread.startVirtualThread {
                val (result, cs) = EditOperations.paste(instance, clipboard, player.position)
                session.pushHistory(cs)
                player.sendMessage(player.translate(Keys.Orbit.Build.Pasted, "count" to result.blocksChanged.toString(), "time" to result.durationMs.toString()))
            }
        }
    })

    commandManager.register(command("/undo") {
        permission("nebula.worldedit")
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            val session = EditSessionManager.get(player)
            if (session.undo(instance)) player.sendMessage(player.translate(Keys.Orbit.Build.Undone))
            else player.sendMessage(player.translate(Keys.Orbit.Build.NothingToUndo))
        }
    })

    commandManager.register(command("/redo") {
        permission("nebula.worldedit")
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            val session = EditSessionManager.get(player)
            if (session.redo(instance)) player.sendMessage(player.translate(Keys.Orbit.Build.Redone))
            else player.sendMessage(player.translate(Keys.Orbit.Build.NothingToRedo))
        }
    })

    commandManager.register(command("/walls") {
        permission("nebula.worldedit")
        wordArgument("pattern")
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            val sel = EditSessionManager.get(player).selection() ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.NoSelection)); return@onPlayerExecute }
            val pattern = Patterns.parse(requireArg("pattern") ?: return@onPlayerExecute) ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.UnknownBlock)); return@onPlayerExecute }
            Thread.startVirtualThread {
                val (result, cs) = EditOperations.walls(instance, sel, pattern, player)
                EditSessionManager.get(player).pushHistory(cs)
                player.sendMessage(player.translate(Keys.Orbit.Build.BlocksChanged, "count" to result.blocksChanged.toString(), "time" to "0"))
            }
        }
    })

    commandManager.register(command("/outline") {
        permission("nebula.worldedit")
        wordArgument("pattern")
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            val sel = EditSessionManager.get(player).selection() ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.NoSelection)); return@onPlayerExecute }
            val pattern = Patterns.parse(requireArg("pattern") ?: return@onPlayerExecute) ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.UnknownBlock)); return@onPlayerExecute }
            Thread.startVirtualThread {
                val (result, cs) = EditOperations.outline(instance, sel, pattern, player)
                EditSessionManager.get(player).pushHistory(cs)
                player.sendMessage(player.translate(Keys.Orbit.Build.BlocksChanged, "count" to result.blocksChanged.toString(), "time" to "0"))
            }
        }
    })

    commandManager.register(command("/drain") {
        permission("nebula.worldedit")
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            val sel = EditSessionManager.get(player).selection() ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.NoSelection)); return@onPlayerExecute }
            Thread.startVirtualThread {
                val (result, cs) = EditOperations.drain(instance, sel, player)
                EditSessionManager.get(player).pushHistory(cs)
                player.sendMessage(player.translate(Keys.Orbit.Build.Drained, "count" to result.blocksChanged.toString()))
            }
        }
    })

    commandManager.register(command("/smooth") {
        permission("nebula.worldedit")
        intArgument("iterations")
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            val sel = EditSessionManager.get(player).selection() ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.NoSelection)); return@onPlayerExecute }
            val iterations = argOrNull("iterations")?.toIntOrNull() ?: 1
            Thread.startVirtualThread {
                val (result, cs) = EditOperations.smooth(instance, sel, iterations, player)
                EditSessionManager.get(player).pushHistory(cs)
                player.sendMessage(player.translate(Keys.Orbit.Build.Smoothed, "count" to result.blocksChanged.toString(), "iterations" to iterations.toString()))
            }
        }
    })

    commandManager.register(command("/naturalize") {
        permission("nebula.worldedit")
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            val sel = EditSessionManager.get(player).selection() ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.NoSelection)); return@onPlayerExecute }
            Thread.startVirtualThread {
                val (result, cs) = EditOperations.naturalize(instance, sel, player)
                EditSessionManager.get(player).pushHistory(cs)
                player.sendMessage(player.translate(Keys.Orbit.Build.Naturalized, "count" to result.blocksChanged.toString()))
            }
        }
    })

    commandManager.register(command("/sphere") {
        permission("nebula.worldedit")
        wordArgument("pattern")
        intArgument("radius")
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            val pattern = Patterns.parse(requireArg("pattern") ?: return@onPlayerExecute) ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.UnknownBlock)); return@onPlayerExecute }
            val radius = requireArg("radius")?.toDoubleOrNull() ?: return@onPlayerExecute
            Thread.startVirtualThread {
                val (result, cs) = EditOperations.sphere(instance, player.position, radius, pattern, false, player)
                EditSessionManager.get(player).pushHistory(cs)
                player.sendMessage(player.translate(Keys.Orbit.Build.BlocksChanged, "count" to result.blocksChanged.toString(), "time" to result.durationMs.toString()))
            }
        }
    })

    commandManager.register(command("/hsphere") {
        permission("nebula.worldedit")
        wordArgument("pattern")
        intArgument("radius")
        onPlayerExecute {
            val instance = player.instance ?: return@onPlayerExecute
            val pattern = Patterns.parse(requireArg("pattern") ?: return@onPlayerExecute) ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.UnknownBlock)); return@onPlayerExecute }
            val radius = requireArg("radius")?.toDoubleOrNull() ?: return@onPlayerExecute
            Thread.startVirtualThread {
                val (result, cs) = EditOperations.sphere(instance, player.position, radius, pattern, true, player)
                EditSessionManager.get(player).pushHistory(cs)
                player.sendMessage(player.translate(Keys.Orbit.Build.BlocksChanged, "count" to result.blocksChanged.toString(), "time" to result.durationMs.toString()))
            }
        }
    })

    commandManager.register(command("/size") {
        permission("nebula.worldedit")
        onPlayerExecute {
            val sel = EditSessionManager.get(player).selection() ?: run { player.sendMessage(player.translate(Keys.Orbit.Build.NoSelection)); return@onPlayerExecute }
            player.sendMessage(player.translate(Keys.Orbit.Build.SelectionSize, "w" to sel.width.toString(), "h" to sel.height.toString(), "l" to sel.length.toString(), "volume" to sel.volume.toString()))
        }
    })
}

fun installWandListeners(eventNode: EventNode<Event>) {
    eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
        if (event.player.gameMode != GameMode.CREATIVE) return@addListener
        val heldItem = event.player.itemInMainHand
        if (heldItem.material() != Material.WOODEN_AXE) return@addListener
        event.isCancelled = true
        val session = EditSessionManager.get(event.player)
        session.pos1 = Pos(event.blockPosition.blockX().toDouble(), event.blockPosition.blockY().toDouble(), event.blockPosition.blockZ().toDouble())
        SelectionRenderer.update(event.player, session.selection())
        event.player.sendMessage(event.player.translate(Keys.Orbit.Build.Pos1Set, "x" to event.blockPosition.blockX().toString(), "y" to event.blockPosition.blockY().toString(), "z" to event.blockPosition.blockZ().toString()))
    }

    eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
        if (event.hand != PlayerHand.MAIN) return@addListener
        if (event.player.gameMode != GameMode.CREATIVE) return@addListener
        val heldItem = event.player.itemInMainHand
        if (heldItem.material() != Material.WOODEN_AXE) return@addListener
        val session = EditSessionManager.get(event.player)
        session.pos2 = Pos(event.blockPosition.blockX().toDouble(), event.blockPosition.blockY().toDouble(), event.blockPosition.blockZ().toDouble())
        SelectionRenderer.update(event.player, session.selection())
        event.player.sendMessage(event.player.translate(Keys.Orbit.Build.Pos2Set, "x" to event.blockPosition.blockX().toString(), "y" to event.blockPosition.blockY().toString(), "z" to event.blockPosition.blockZ().toString()))
    }

    eventNode.addListener(PlayerDisconnectEvent::class.java) { event ->
        EditSessionManager.remove(event.player)
        SelectionRenderer.clear(event.player)
    }
}
