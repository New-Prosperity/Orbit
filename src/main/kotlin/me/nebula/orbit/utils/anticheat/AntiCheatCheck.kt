package me.nebula.orbit.utils.anticheat

import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.util.UUID

interface AntiCheatCheck {
    val id: String
    fun install(node: EventNode<in Event>)
    fun cleanup(uuid: UUID) {}
    fun clearAll() {}
}

object AntiCheatRegistry {

    private val checks = LinkedHashMap<String, AntiCheatCheck>()

    fun register(check: AntiCheatCheck) {
        require(check.id !in checks) { "Anti-cheat check already registered: ${check.id}" }
        checks[check.id] = check
    }

    fun all(): Collection<AntiCheatCheck> = checks.values

    fun get(id: String): AntiCheatCheck? = checks[id]
}
