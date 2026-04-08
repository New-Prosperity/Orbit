package me.nebula.orbit

import me.nebula.ether.utils.storage.StorageClient
import me.nebula.orbit.mode.ServerMode
import java.util.UUID

class OrbitInstance(
    val serverName: String,
    val mode: ServerMode,
    val provisionUuid: String?,
    val gameMode: String?,
    val hostOwner: UUID?,
    val mapName: String?,
    val activeMutatorIds: List<String>,
    val randomMutatorCount: Int,
    val storage: StorageClient?,
)
