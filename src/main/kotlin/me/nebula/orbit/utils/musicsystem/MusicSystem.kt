package me.nebula.orbit.utils.musicsystem

import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

enum class Instrument(val soundEvent: SoundEvent) {
    HARP(SoundEvent.BLOCK_NOTE_BLOCK_HARP),
    BASS(SoundEvent.BLOCK_NOTE_BLOCK_BASS),
    SNARE(SoundEvent.BLOCK_NOTE_BLOCK_SNARE),
    HAT(SoundEvent.BLOCK_NOTE_BLOCK_HAT),
    BASS_DRUM(SoundEvent.BLOCK_NOTE_BLOCK_BASEDRUM),
    BELL(SoundEvent.BLOCK_NOTE_BLOCK_BELL),
    FLUTE(SoundEvent.BLOCK_NOTE_BLOCK_FLUTE),
    CHIME(SoundEvent.BLOCK_NOTE_BLOCK_CHIME),
    GUITAR(SoundEvent.BLOCK_NOTE_BLOCK_GUITAR),
    XYLOPHONE(SoundEvent.BLOCK_NOTE_BLOCK_XYLOPHONE),
    IRON_XYLOPHONE(SoundEvent.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE),
    COW_BELL(SoundEvent.BLOCK_NOTE_BLOCK_COW_BELL),
    DIDGERIDOO(SoundEvent.BLOCK_NOTE_BLOCK_DIDGERIDOO),
    BIT(SoundEvent.BLOCK_NOTE_BLOCK_BIT),
    BANJO(SoundEvent.BLOCK_NOTE_BLOCK_BANJO),
    PLING(SoundEvent.BLOCK_NOTE_BLOCK_PLING),
}

data class Note(
    val tick: Int,
    val instrument: Instrument,
    val pitch: Float,
    val volume: Float = 1.0f,
)

class Song internal constructor(
    val name: String,
    val bpm: Int,
    val notes: List<Note>,
) {
    @Volatile private var playbackTask: Task? = null
    @Volatile private var currentTick: Int = 0

    val isPlaying: Boolean get() = playbackTask != null
    val ticksPerBeat: Int get() = (1200 / bpm).coerceAtLeast(1)

    fun play(instance: Instance, pos: Pos) {
        stop()
        currentTick = 0
        val maxTick = notes.maxOfOrNull { it.tick } ?: return
        val notesByTick = notes.groupBy { it.tick }

        playbackTask = MinecraftServer.getSchedulerManager().buildTask {
            notesByTick[currentTick]?.forEach { note ->
                val sound = Sound.sound(
                    note.instrument.soundEvent.key(),
                    Sound.Source.RECORD,
                    note.volume,
                    note.pitch,
                )
                instance.playSound(sound, pos.x(), pos.y(), pos.z())
            }
            currentTick++
            if (currentTick > maxTick) stop()
        }.repeat(TaskSchedule.tick(ticksPerBeat)).schedule()

        SongManager.register(instance, this)
    }

    fun stop() {
        playbackTask?.cancel()
        playbackTask = null
        currentTick = 0
    }
}

class SongBuilder @PublishedApi internal constructor(
    @PublishedApi internal val name: String,
) {
    @PublishedApi internal var bpm: Int = 120
    @PublishedApi internal val notes = mutableListOf<Note>()

    fun bpm(bpm: Int) { this.bpm = bpm }

    fun note(tick: Int, instrument: Instrument, pitch: Float, volume: Float = 1.0f) {
        notes += Note(tick, instrument, pitch, volume)
    }

    fun rest(@Suppress("UNUSED_PARAMETER") ticks: Int) {}

    fun chord(tick: Int, instrument: Instrument, vararg pitches: Float) {
        pitches.forEach { pitch -> notes += Note(tick, instrument, pitch) }
    }

    @PublishedApi internal fun build(): Song = Song(name, bpm, notes.toList())
}

object SongManager {

    private val activeSongs = ConcurrentHashMap<Int, Song>()

    fun register(instance: Instance, song: Song) {
        activeSongs[System.identityHashCode(instance)]?.stop()
        activeSongs[System.identityHashCode(instance)] = song
    }

    fun stopForInstance(instance: Instance) {
        activeSongs.remove(System.identityHashCode(instance))?.stop()
    }

    fun isPlaying(instance: Instance): Boolean =
        activeSongs[System.identityHashCode(instance)]?.isPlaying == true

    fun activeSong(instance: Instance): Song? =
        activeSongs[System.identityHashCode(instance)]
}

inline fun song(name: String, block: SongBuilder.() -> Unit): Song =
    SongBuilder(name).apply(block).build()

fun Instance.playSong(pos: Pos, song: Song) = song.play(this, pos)

fun Instance.stopSong() = SongManager.stopForInstance(this)
