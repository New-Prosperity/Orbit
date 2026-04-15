package me.nebula.orbit.mode.game.battleroyale.script

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.mode.game.battleroyale.BorderConfig
import me.nebula.orbit.mode.game.battleroyale.BorderPhaseConfig
import me.nebula.orbit.mode.game.battleroyale.Season
import me.nebula.orbit.mode.game.battleroyale.StarterKitConfig
import me.nebula.orbit.script.GameContext
import me.nebula.orbit.script.ScriptAction
import me.nebula.orbit.script.ScriptTrigger
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class BattleRoyaleScriptTest {

    private fun season(
        border: BorderConfig = BorderConfig(500.0, 20.0, 0.0, 0.0, 120, 300),
        phases: List<BorderPhaseConfig> = emptyList(),
    ): Season = Season(
        id = 1,
        kits = emptyList(),
        xpRewards = emptyMap(),
        starterKit = StarterKitConfig(),
        border = border,
        borderPhases = phases,
    )

    @Test
    fun `buildBorderSteps derives one step per phase with announce, damage, shrink`() {
        val phases = listOf(
            BorderPhaseConfig(startAfterSeconds = 60, targetDiameter = 100.0, shrinkDurationSeconds = 30, damagePerSecond = 2f),
            BorderPhaseConfig(startAfterSeconds = 180, targetDiameter = 40.0, shrinkDurationSeconds = 20, damagePerSecond = 4f),
        )
        val steps = buildBorderSteps(season(phases = phases))
        assertEquals(2, steps.size)
        val first = steps[0]
        assertEquals("br_border_phase_60", first.id)
        assertEquals(ScriptTrigger.AtTime(60.seconds), first.trigger)
        assertEquals(3, first.actions.size)
        assertTrue(first.actions[0] is ScriptAction.Announce)
        assertEquals(SetBorderDamage(2.0), first.actions[1])
        assertEquals(ShrinkBorder(100.0, 30.0), first.actions[2])
    }

    @Test
    fun `buildBorderSteps applies speed multiplier to phase timings`() {
        val phases = listOf(BorderPhaseConfig(100, 50.0, 40, 1f))
        val steps = buildBorderSteps(season(phases = phases), speedMultiplier = 2.0)
        assertEquals(ScriptTrigger.AtTime(200.seconds), steps.single().trigger)
        assertEquals(ShrinkBorder(50.0, 80.0), steps.single().actions[2])
    }

    @Test
    fun `buildBorderSteps falls back to legacy border with delayed shrink`() {
        val border = BorderConfig(500.0, 20.0, 0.0, 0.0, shrinkStartSeconds = 90, shrinkDurationSeconds = 60)
        val steps = buildBorderSteps(season(border = border))
        val step = steps.single()
        assertEquals("br_border_legacy", step.id)
        assertEquals(ScriptTrigger.AtTime(90.seconds), step.trigger)
        assertEquals(ShrinkBorder(20.0, 60.0), step.actions.last())
    }

    @Test
    fun `buildBorderSteps emits immediate shrink when shrinkStartSeconds is zero`() {
        val border = BorderConfig(500.0, 20.0, 0.0, 0.0, shrinkStartSeconds = 0, shrinkDurationSeconds = 60)
        val steps = buildBorderSteps(season(border = border))
        val step = steps.single()
        assertEquals(ScriptTrigger.AtTime(0.seconds), step.trigger)
        assertEquals(listOf(ShrinkBorder(20.0, 60.0)), step.actions)
    }

    @Test
    fun `ShrinkBorder delegates to BorderController on the game mode`() {
        val controller = mockk<BrControllerMode>(relaxed = true)
        ShrinkBorder(75.0, 20.0).execute(ctx(controller))
        verify { controller.shrinkBorderTo(75.0, 20.0) }
    }

    @Test
    fun `SetBorderDamage delegates to BorderController on the game mode`() {
        val controller = mockk<BrControllerMode>(relaxed = true)
        SetBorderDamage(3.5).execute(ctx(controller))
        verify { controller.setBorderDamage(3.5) }
    }

    @Test
    fun `StartDeathmatch delegates to DeathmatchController on the game mode`() {
        val controller = mockk<BrControllerMode>(relaxed = true)
        StartDeathmatch.execute(ctx(controller))
        verify { controller.startDeathmatch() }
    }

    @Test
    fun `BR actions silently no-op when game mode does not implement the controller`() {
        val mode = mockk<GameMode>(relaxed = true)
        ShrinkBorder(10.0, 1.0).execute(ctx(mode))
        SetBorderDamage(1.0).execute(ctx(mode))
        StartDeathmatch.execute(ctx(mode))
    }

    private abstract class BrControllerMode : GameMode(), BorderController, DeathmatchController

    private fun ctx(mode: GameMode): GameContext {
        val ctx = mockk<GameContext>(relaxed = true)
        every { ctx.gameMode } returns mode
        return ctx
    }
}
