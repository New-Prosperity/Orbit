package me.nebula.orbit.commands

import me.nebula.orbit.utils.commandbuilder.CommandBuilderDsl
import me.nebula.orbit.utils.mapgen.GeneratedMap
import me.nebula.orbit.utils.mapgen.planet.MinimapRenderer
import me.nebula.orbit.utils.mapgen.planet.PlanetGenerator
import me.nebula.orbit.utils.mapgen.planet.PlanetMapGenerator
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.instance.Instance
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

private val sessions = ConcurrentHashMap<UUID, PlanetSession>()

private val knownPlanets = listOf("rhexor")

private class PlanetSession(
    val planetId: String,
    val map: GeneratedMap,
    val generator: PlanetGenerator,
    val originInstance: Instance,
    val originPos: Pos,
)

internal fun CommandBuilderDsl.installPlanetSubcommands() {
    subCommand("planet") {
        subCommand("generate") {
            wordArgument("id") {
                knownPlanets.filter { it.startsWith(partial, ignoreCase = true) }
            }
            onPlayerExecute {
                val id = argOrNull("id")?.lowercase()
                if (id.isNullOrBlank()) {
                    replyMM("<red>Usage: /orbit planet generate <id> — known: ${knownPlanets.joinToString(", ")}")
                    return@onPlayerExecute
                }
                if (id !in knownPlanets) {
                    replyMM("<red>Unknown planet '$id'. Known: ${knownPlanets.joinToString(", ")}")
                    return@onPlayerExecute
                }

                sessions[player.uuid]?.let { cleanupSession(it) }

                replyMM("<gray>Generating <white>$id</white>…")
                Thread.startVirtualThread {
                    val planet = try {
                        PlanetMapGenerator.build(id)
                    } catch (e: Throwable) {
                        player.sendMessage(Component.text("Build failed: ${e.message}"))
                        return@startVirtualThread
                    }
                    val generated = try {
                        PlanetMapGenerator.generate(planet)
                    } catch (e: Throwable) {
                        player.sendMessage(Component.text("Generation failed: ${e.message}"))
                        return@startVirtualThread
                    }

                    val originInstance = player.instance
                    val originPos = player.position
                    if (originInstance == null) {
                        player.sendMessage(Component.text("No origin instance — refusing to teleport."))
                        MinecraftServer.getInstanceManager().unregisterInstance(generated.instance)
                        return@startVirtualThread
                    }

                    val session = PlanetSession(id, generated, planet, originInstance, originPos)
                    sessions[player.uuid] = session

                    player.setInstance(generated.instance, generated.center).thenRun {
                        player.gameMode = GameMode.CREATIVE
                        val st = planet.stats()
                        player.sendMessage(Component.text(
                            "Generated '$id' (seed=${planet.spec.seed}) — chunks=${st.chunksGenerated} slow=${st.slowChunks} failed=${st.chunksFailed}. /orbit planet delete to clean up."
                        ))
                    }
                }
            }
        }

        subCommand("tp") {
            onPlayerExecute {
                val s = sessions[player.uuid]
                if (s == null) {
                    replyMM("<red>No active planet for you. Use /orbit planet generate <id>.")
                    return@onPlayerExecute
                }
                player.setInstance(s.map.instance, s.map.center)
                replyMM("<green>Teleported to <white>${s.planetId}</white>.")
            }
        }

        subCommand("delete") {
            onPlayerExecute {
                val s = sessions.remove(player.uuid)
                if (s == null) {
                    replyMM("<red>No active planet to delete.")
                    return@onPlayerExecute
                }
                cleanupSession(s)
                player.setInstance(s.originInstance, s.originPos)
                replyMM("<green>Unloaded planet <white>${s.planetId}</white> and returned you to origin.")
            }
        }

        subCommand("stats") {
            onPlayerExecute {
                val s = sessions[player.uuid]
                if (s == null) {
                    replyMM("<red>No active planet.")
                    return@onPlayerExecute
                }
                val st = s.generator.stats()
                replyMM("<gold><bold>Planet</bold></gold> <white>${s.planetId}</white> <dark_gray>seed=${s.generator.spec.seed}")
                replyMM("<white> chunks generated: <gold>${st.chunksGenerated}</gold>  failed: <red>${st.chunksFailed}</red>  slow: <yellow>${st.slowChunks}</yellow>")
                replyMM("<white> ground cache: <gray>${st.groundLevelCacheSize}</gray>  ctx cache: <gray>${st.chunkContextCacheSize}</gray>  spawned structures: <gray>${st.spawnedStructureCount}</gray>")
            }
        }

        subCommand("minimap") {
            onPlayerExecute {
                val s = sessions[player.uuid]
                if (s == null) {
                    replyMM("<red>No active planet.")
                    return@onPlayerExecute
                }
                Thread.startVirtualThread {
                    val image = MinimapRenderer.render(s.generator)
                    val outDir = Path.of("data/minimaps")
                    Files.createDirectories(outDir)
                    val outFile = outDir.resolve("${s.planetId}-${s.generator.spec.seed}.png")
                    ImageIO.write(image, "png", outFile.toFile())
                    player.sendMessage(Component.text("Minimap rendered → $outFile"))
                }
            }
        }

        onPlayerExecute {
            replyMM("<gold><bold>/orbit planet</bold></gold> <dark_gray>- debug planet generation")
            replyMM("<white> /orbit planet generate <id> <dark_gray>- Generate a planet + teleport (known: ${knownPlanets.joinToString(", ")})")
            replyMM("<white> /orbit planet tp <dark_gray>- Teleport to your active planet")
            replyMM("<white> /orbit planet delete <dark_gray>- Unload your active planet and return to origin")
            replyMM("<white> /orbit planet stats <dark_gray>- Generator counters + cache sizes")
            replyMM("<white> /orbit planet minimap <dark_gray>- Render top-down PNG to data/minimaps/")
        }
    }
}

private fun cleanupSession(session: PlanetSession) {
    runCatching { MinecraftServer.getInstanceManager().unregisterInstance(session.map.instance) }
    session.generator.clearCaches()
}
