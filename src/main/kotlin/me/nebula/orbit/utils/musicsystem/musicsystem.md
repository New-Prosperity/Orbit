# MusicSystem

Note block music player using Minestom scheduler for tick-based note playback.

## Key Classes

- **`Instrument`** -- enum mapping to Minecraft note block sound events (16 instruments)
- **`Note`** -- single note with tick, instrument, pitch, volume
- **`Song`** -- collection of notes with BPM, play/stop control
- **`SongBuilder`** -- DSL builder for composing songs
- **`SongManager`** -- per-instance active song tracking

## Usage

```kotlin
val mySong = song("victory") {
    bpm(140)
    note(0, Instrument.HARP, 1.0f)
    note(1, Instrument.HARP, 1.2f)
    note(2, Instrument.HARP, 1.4f)
    chord(3, Instrument.BELL, 1.0f, 1.5f, 2.0f)
    note(4, Instrument.PLING, 2.0f)
}

instance.playSong(Pos(0.0, 65.0, 0.0), mySong)
instance.stopSong()

mySong.isPlaying
```

## Instruments

HARP, BASS, SNARE, HAT, BASS_DRUM, BELL, FLUTE, CHIME, GUITAR, XYLOPHONE, IRON_XYLOPHONE, COW_BELL, DIDGERIDOO, BIT, BANJO, PLING

## Extension Functions

```kotlin
instance.playSong(pos, song)
instance.stopSong()
```

## Details

- BPM controls tick interval: `ticksPerBeat = 1200 / bpm`
- Notes grouped by tick for efficient playback
- Sound sent via `SoundEffectPacket` to all players in instance
- One song per instance at a time (previous song auto-stopped)
- Song auto-stops when all notes have played
- `chord()` helper adds multiple notes at the same tick
- Pitch range: 0.5 (F#3) to 2.0 (F#5), matching Minecraft note block range
