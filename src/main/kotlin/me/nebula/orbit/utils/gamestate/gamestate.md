# Game State

Generic enum-based state machine with allowed transitions, guards, enter/exit callbacks, timed transitions, state history, and listener support.

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

    guard(Phase.WAITING, Phase.COUNTDOWN) { playerCount >= minPlayers }
    guard(Phase.COUNTDOWN, Phase.PLAYING) { allPlayersReady() }

    onEnter(Phase.PLAYING) { startGame() }
    onExit(Phase.PLAYING) { cleanupGame() }
}

machine.startTicking()

machine.transition(Phase.COUNTDOWN)
machine.canTransition(Phase.PLAYING)
machine.forceTransition(Phase.PLAYING)

machine.onTransition { from, to -> log("$from -> $to") }

machine.stateHistory
machine.is_(Phase.PLAYING)
machine.isAny(Phase.WAITING, Phase.COUNTDOWN)
machine.ticksInCurrentState()

machine.destroy()
```

## API

| Method | Description |
|--------|-------------|
| `transition(to)` | Transition if allowed and guard passes, returns success |
| `forceTransition(to)` | Bypass allowed-transitions and guards |
| `canTransition(to)` | Check if transition is allowed AND guard passes (no side effects) |
| `guard(from, to, predicate)` | Register a guard predicate for a specific transition |
| `onTransition(callback)` | Register a state change listener |
| `startTicking()` | Begin tick counter and timed transitions |
| `stopTicking()` | Pause tick counter |
| `is_(state)` | Check current state |
| `isAny(vararg states)` | Check if current state is any of the given |
| `ticksInCurrentState()` | Ticks elapsed in current state |
| `stateHistory` | Last 20 state changes as `List<Pair<S, Long>>` (state, timestamp) |
| `destroy()` | Stop ticking, clear listeners, clear history |
