# Game State

Generic enum-based state machine with allowed transitions, enter/exit callbacks, timed transitions, and listener support.

## Key Classes

- **`GameStateMachine<S>`** -- state machine parameterized by an enum type
- **`GameStateMachineBuilder<S>`** -- DSL builder

## Usage

```kotlin
enum class Phase { WAITING, COUNTDOWN, PLAYING, ENDING }

val machine = gameStateMachine(Phase.WAITING) {
    allow(Phase.WAITING, Phase.COUNTDOWN)
    allow(Phase.COUNTDOWN, Phase.PLAYING, Phase.WAITING)
    allow(Phase.PLAYING, Phase.ENDING)

    timedTransition(Phase.ENDING, Phase.WAITING, ticks = 200)

    onEnter(Phase.PLAYING) { startGame() }
    onExit(Phase.PLAYING) { cleanupGame() }
}

machine.startTicking()

machine.transition(Phase.COUNTDOWN)
machine.forceTransition(Phase.PLAYING)

machine.onTransition { from, to -> log("$from -> $to") }

machine.is_(Phase.PLAYING)
machine.isAny(Phase.WAITING, Phase.COUNTDOWN)
machine.ticksInCurrentState()

machine.destroy()
```

## API

| Method | Description |
|--------|-------------|
| `transition(to)` | Transition if allowed, returns success |
| `forceTransition(to)` | Bypass allowed-transitions check |
| `onTransition(callback)` | Register a state change listener |
| `startTicking()` | Begin tick counter and timed transitions |
| `stopTicking()` | Pause tick counter |
| `is_(state)` | Check current state |
| `isAny(vararg states)` | Check if current state is any of the given |
| `ticksInCurrentState()` | Ticks elapsed in current state |
| `destroy()` | Stop ticking and clear listeners |
