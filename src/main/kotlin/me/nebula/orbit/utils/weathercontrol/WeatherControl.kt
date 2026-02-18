package me.nebula.orbit.utils.weathercontrol

import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Instance
import net.minestom.server.instance.Weather
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

enum class WeatherState(val rainLevel: Float, val thunderLevel: Float) {
    SUNNY(0f, 0f),
    RAINY(1f, 0f),
    THUNDERING(1f, 1f),
}

data class WeatherEntry(
    val state: WeatherState,
    val remainingTicks: Int,
    val task: Task?,
)

object WeatherController {

    private val weatherStates = ConcurrentHashMap<Instance, WeatherEntry>()

    fun setWeather(instance: Instance, state: WeatherState, durationTicks: Int = -1) {
        weatherStates[instance]?.task?.cancel()
        instance.weather = Weather(state.rainLevel, state.thunderLevel)

        if (durationTicks > 0) {
            val task = MinecraftServer.getSchedulerManager()
                .buildTask {
                    setWeather(instance, WeatherState.SUNNY)
                }
                .delay(TaskSchedule.tick(durationTicks))
                .schedule()
            weatherStates[instance] = WeatherEntry(state, durationTicks, task)
        } else {
            weatherStates[instance] = WeatherEntry(state, -1, null)
        }
    }

    fun getWeather(instance: Instance): WeatherState =
        weatherStates[instance]?.state ?: WeatherState.SUNNY

    fun clearWeather(instance: Instance) {
        weatherStates.remove(instance)?.task?.cancel()
        instance.weather = Weather(0f, 0f)
    }

    fun clearAll() {
        weatherStates.values.forEach { it.task?.cancel() }
        weatherStates.clear()
    }
}

class WeatherBuilder @PublishedApi internal constructor(private val instance: Instance) {

    @PublishedApi internal var state: WeatherState = WeatherState.SUNNY
    @PublishedApi internal var durationTicks: Int = -1

    fun sunny() { state = WeatherState.SUNNY }
    fun rainy() { state = WeatherState.RAINY }
    fun thundering() { state = WeatherState.THUNDERING }
    fun duration(ticks: Int) { durationTicks = ticks }

    @PublishedApi internal fun build() {
        WeatherController.setWeather(instance, state, durationTicks)
    }
}

inline fun instanceWeather(instance: Instance, block: WeatherBuilder.() -> Unit) {
    WeatherBuilder(instance).apply(block).build()
}

fun Instance.setWeather(state: WeatherState, durationTicks: Int = -1) =
    WeatherController.setWeather(this, state, durationTicks)

fun Instance.clearControlledWeather() =
    WeatherController.clearWeather(this)

val Instance.weatherState: WeatherState get() =
    WeatherController.getWeather(this)
