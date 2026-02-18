package me.nebula.orbit.mechanic.weather

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Instance
import net.minestom.server.instance.Weather
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import kotlin.random.Random

private val WEATHER_STATE_TAG = Tag.Byte("mechanic:weather:state").defaultValue(0)
private val WEATHER_DURATION_TAG = Tag.Integer("mechanic:weather:duration").defaultValue(0)

private const val CLEAR: Byte = 0
private const val RAIN: Byte = 1
private const val THUNDER: Byte = 2

class WeatherModule : OrbitModule("weather") {

    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()
        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.seconds(1))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        super.onDisable()
    }

    private fun tick() {
        MinecraftServer.getInstanceManager().instances.forEach { instance ->
            val remaining = instance.getTag(WEATHER_DURATION_TAG) - 1
            if (remaining <= 0) {
                cycleWeather(instance)
            } else {
                instance.setTag(WEATHER_DURATION_TAG, remaining)
            }
        }
    }

    private fun cycleWeather(instance: Instance) {
        val current = instance.getTag(WEATHER_STATE_TAG)
        val next = when (current) {
            CLEAR -> if (Random.nextFloat() < 0.3f) RAIN else CLEAR
            RAIN -> when {
                Random.nextFloat() < 0.15f -> THUNDER
                Random.nextFloat() < 0.5f -> CLEAR
                else -> RAIN
            }
            THUNDER -> if (Random.nextFloat() < 0.6f) RAIN else CLEAR
            else -> CLEAR
        }
        instance.setTag(WEATHER_STATE_TAG, next)
        instance.setTag(WEATHER_DURATION_TAG, Random.nextInt(300, 900))
        applyWeather(instance, next)
    }

    private fun applyWeather(instance: Instance, state: Byte) {
        instance.weather = when (state) {
            RAIN -> Weather(1f, 0f)
            THUNDER -> Weather(1f, 1f)
            else -> Weather(0f, 0f)
        }
    }
}
