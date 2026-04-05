# Metrics Publisher

Publishes `ServerMetrics` to the shared Hazelcast `ReplicatedMap("metrics")` every 10 seconds.

## Usage

```kotlin
MetricsPublisher.initialize()  // start publishing
MetricsPublisher.shutdown()    // stop publishing, await termination (5s), remove entry
```

## Metrics Collected

| Field | Source |
|---|---|
| `name` | `Orbit.serverName` |
| `type` | `Orbit.gameMode` or `"hub"` |
| `tps` | `TPSMonitor.averageTPS` |
| `playerCount` | `MinecraftServer.getConnectionManager().onlinePlayers.size` |
| `entityCount` | Sum of entities across all instances |
| `memoryUsedMb` | `Runtime.totalMemory() - Runtime.freeMemory()` |
| `memoryMaxMb` | `Runtime.maxMemory()` |
| `uptime` | Time since initialization |

## Requirements

- `TPSMonitor.install()` must be called before `MetricsPublisher.initialize()`
- Hazelcast must be connected
