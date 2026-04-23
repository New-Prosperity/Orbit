package me.nebula.orbit.mode.game.battleroyale.spawn

import java.util.concurrent.ConcurrentHashMap

object SpawnModeRegistry {

    private val providers = ConcurrentHashMap<String, SpawnModeProvider>()

    init {
        register(HungerGamesProvider)
        register(ExtendedHungerGamesProvider)
        register(RandomSpawnProvider)
        register(BattleBusProvider)
        register(PodDropProvider)
        register(TeamClusterProvider)
        register(ThemedRingProvider)
    }

    fun register(provider: SpawnModeProvider) {
        providers[provider.id] = provider
    }

    fun resolve(id: String): SpawnModeProvider? = providers[id]

    fun all(): Collection<SpawnModeProvider> = providers.values

    fun ids(): Set<String> = providers.keys.toSet()
}
