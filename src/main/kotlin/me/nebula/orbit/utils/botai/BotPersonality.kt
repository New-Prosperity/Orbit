package me.nebula.orbit.utils.botai

import kotlin.random.Random

data class BotPersonality(
    val aggression: Float = 0.5f,
    val caution: Float = 0.5f,
    val resourcefulness: Float = 0.5f,
    val curiosity: Float = 0.5f,
    val teamwork: Float = 0.5f,
)

object BotPersonalities {
    val WARRIOR = BotPersonality(aggression = 0.9f, caution = 0.2f, resourcefulness = 0.3f)
    val SURVIVOR = BotPersonality(aggression = 0.4f, caution = 0.8f, resourcefulness = 0.9f)
    val EXPLORER = BotPersonality(aggression = 0.3f, caution = 0.5f, curiosity = 0.9f)
    val BERSERKER = BotPersonality(aggression = 1.0f, caution = 0.0f, resourcefulness = 0.1f)
    val BUILDER = BotPersonality(aggression = 0.1f, caution = 0.7f, resourcefulness = 1.0f, curiosity = 0.3f)
    val BALANCED = BotPersonality()

    fun random(): BotPersonality = BotPersonality(
        Random.nextFloat(),
        Random.nextFloat(),
        Random.nextFloat(),
        Random.nextFloat(),
        Random.nextFloat(),
    )
}
