package me.nebula.orbit.mode.game.battleroyale.spawn

import me.nebula.orbit.mode.game.battleroyale.SpawnMode
import java.util.concurrent.ConcurrentHashMap

object SpawnModeRegistry {

    private val providers = ConcurrentHashMap<String, SpawnModeProvider>()

    init {
        register(EnumBackedSpawnProvider("hunger_games", SpawnMode.HUNGER_GAMES))
        register(EnumBackedSpawnProvider("extended_hunger_games", SpawnMode.EXTENDED_HUNGER_GAMES))
        register(EnumBackedSpawnProvider("random", SpawnMode.RANDOM))
        register(EnumBackedSpawnProvider("battle_royale_bus", SpawnMode.BATTLE_ROYALE))
    }

    fun register(provider: SpawnModeProvider) {
        providers[provider.id] = provider
    }

    fun resolve(id: String): SpawnModeProvider? = providers[id]

    fun all(): Collection<SpawnModeProvider> = providers.values

    fun ids(): Set<String> = providers.keys.toSet()
}
