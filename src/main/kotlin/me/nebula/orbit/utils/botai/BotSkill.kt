package me.nebula.orbit.utils.botai

data class BotSkillLevel(
    val aimAccuracy: Float = 0.7f,
    val reactionTimeTicks: Int = 8,
    val criticalHitChance: Float = 0.5f,
    val blockChance: Float = 0.3f,
    val movementJitter: Float = 0.1f,
    val decisionDelay: Int = 5,
    val miningEfficiency: Float = 0.8f,
    val bridgingSpeed: Float = 0.6f,
)

object BotSkillLevels {
    val BEGINNER = BotSkillLevel(
        aimAccuracy = 0.4f,
        reactionTimeTicks = 15,
        criticalHitChance = 0.2f,
        blockChance = 0.1f,
        movementJitter = 0.25f,
        decisionDelay = 15,
        miningEfficiency = 0.6f,
        bridgingSpeed = 0.3f,
    )
    val CASUAL = BotSkillLevel(
        aimAccuracy = 0.6f,
        reactionTimeTicks = 10,
        criticalHitChance = 0.4f,
        blockChance = 0.25f,
        movementJitter = 0.15f,
        decisionDelay = 8,
        miningEfficiency = 0.75f,
        bridgingSpeed = 0.5f,
    )
    val AVERAGE = BotSkillLevel()
    val SKILLED = BotSkillLevel(
        aimAccuracy = 0.85f,
        reactionTimeTicks = 5,
        criticalHitChance = 0.7f,
        blockChance = 0.5f,
        movementJitter = 0.05f,
        decisionDelay = 3,
        miningEfficiency = 0.95f,
        bridgingSpeed = 0.8f,
    )
    val EXPERT = BotSkillLevel(
        aimAccuracy = 0.95f,
        reactionTimeTicks = 3,
        criticalHitChance = 0.9f,
        blockChance = 0.7f,
        movementJitter = 0.02f,
        decisionDelay = 1,
        miningEfficiency = 1.0f,
        bridgingSpeed = 0.95f,
    )

    fun forRating(rating: Float): BotSkillLevel {
        val r = rating.coerceIn(0f, 1f)
        return BotSkillLevel(
            aimAccuracy = lerp(BEGINNER.aimAccuracy, EXPERT.aimAccuracy, r),
            reactionTimeTicks = lerp(BEGINNER.reactionTimeTicks, EXPERT.reactionTimeTicks, r),
            criticalHitChance = lerp(BEGINNER.criticalHitChance, EXPERT.criticalHitChance, r),
            blockChance = lerp(BEGINNER.blockChance, EXPERT.blockChance, r),
            movementJitter = lerp(BEGINNER.movementJitter, EXPERT.movementJitter, r),
            decisionDelay = lerp(BEGINNER.decisionDelay, EXPERT.decisionDelay, r),
            miningEfficiency = lerp(BEGINNER.miningEfficiency, EXPERT.miningEfficiency, r),
            bridgingSpeed = lerp(BEGINNER.bridgingSpeed, EXPERT.bridgingSpeed, r),
        )
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()
}
