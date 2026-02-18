# Weather Control

Per-instance weather override DSL with optional timed duration.

## WeatherState

`SUNNY`, `RAINY`, `THUNDERING`

## WeatherController

| Method | Description |
|---|---|
| `setWeather(instance, state, durationTicks)` | Set weather, optionally auto-revert to sunny |
| `getWeather(instance)` | Get current controlled weather state |
| `clearWeather(instance)` | Remove weather control and set sunny |
| `clearAll()` | Clear all weather controls |

## DSL

```kotlin
instanceWeather(instance) {
    rainy()
    duration(6000)
}

instanceWeather(instance) {
    thundering()
}

instanceWeather(instance) {
    sunny()
}
```

## Extension Functions

| Function | Description |
|---|---|
| `instance.setWeather(state, durationTicks)` | Set weather state |
| `instance.clearControlledWeather()` | Remove weather control |
| `instance.weatherState` | Get current weather state |

## Example

```kotlin
instance.setWeather(WeatherState.THUNDERING, durationTicks = 2400)

val current = instance.weatherState
```
