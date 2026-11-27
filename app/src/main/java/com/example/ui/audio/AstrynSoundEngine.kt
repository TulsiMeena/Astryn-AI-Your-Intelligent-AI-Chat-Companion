package com.example.ui.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.example.data.repository.UserPreferencesRepository
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Tactile futuristic UI sound cues for Astryn AI.
 */
enum class AstrynSoundEffect(
  val fileName: String,
  val baseVolume: Float,
) {
  TAP("astryn_tap_v1.wav", 0.34f),
  TOGGLE_ON("astryn_toggle_on_v1.wav", 0.42f),
  TOGGLE_OFF("astryn_toggle_off_v1.wav", 0.38f),
  MESSAGE_SEND("astryn_msg_send_v1.wav", 0.46f),
  MESSAGE_RECEIVE("astryn_msg_recv_v1.wav", 0.44f),
  VOICE_START("astryn_voice_start_v1.wav", 0.50f),
  VOICE_STOP("astryn_voice_stop_v1.wav", 0.46f),
  SUCCESS("astryn_success_v1.wav", 0.48f),
  ERROR("astryn_error_v1.wav", 0.42f),
}

/**
 * Lightweight, zero-latency procedural UI Sound Engine backed by [SoundPool] and cached PCM16 WAV
 * buffers in `cacheDir/astryn_sfx/`.
 *
 * - Respects user preference (`UserPreferences.uiSoundsEnabled`)
 * - Respects system silent ringer mode (`AudioManager.RINGER_MODE_SILENT`)
 * - Suppresses ambient clicks/chimes when Live Voice or SpeechRecognizer is actively capturing/speaking
 *   (`setVoiceBusy(true)`) so UI feedback never leaks into voice recognition or triggers false barge-in
 * - Safely no-ops under Robolectric/JVM unit tests
 */
object AstrynSoundEngine {
  private const val SAMPLE_RATE = 44100
  private const val MIN_PLAY_GAP_MS = 32L

  private val initStarted = AtomicBoolean(false)
  private val isReleased = AtomicBoolean(false)
  private val isVoiceBusy = AtomicBoolean(false)
  private val lastPlayedAtMs = AtomicLong(0L)
  private val executor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "AstrynSoundPreloadThread").apply { isDaemon = true }
  }

  @Volatile private var soundPool: SoundPool? = null
  private val effectSoundIds = ConcurrentHashMap<AstrynSoundEffect, Int>()
  private val loadedSoundIds = ConcurrentHashMap<Int, Boolean>()

  fun preload(context: Context) {
    ensureInitialized(context)
  }

  fun setVoiceBusy(busy: Boolean) {
    isVoiceBusy.set(busy)
  }

  fun play(
    context: Context,
    effect: AstrynSoundEffect,
    bypassVoiceBusyGuard: Boolean =
      effect == AstrynSoundEffect.VOICE_START || effect == AstrynSoundEffect.VOICE_STOP,
  ) {
    try {
      val prefs = UserPreferencesRepository.getInstance(context).preferencesFlow.value
      if (!prefs.uiSoundsEnabled) return
      if (!bypassVoiceBusyGuard && isVoiceBusy.get()) return

      val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
      if (audioManager?.ringerMode == AudioManager.RINGER_MODE_SILENT) return

      val now = System.currentTimeMillis()
      val prev = lastPlayedAtMs.get()
      if (now - prev < MIN_PLAY_GAP_MS) return
      lastPlayedAtMs.compareAndSet(prev, now)

      ensureInitialized(context)

      val pool = soundPool ?: return
      val soundId = effectSoundIds[effect] ?: return
      if (loadedSoundIds[soundId] != true) return

      pool.play(soundId, effect.baseVolume, effect.baseVolume, 1, 0, 1.0f)
    } catch (_: Throwable) {}
  }

  fun release() {
    isReleased.set(true)
    initStarted.set(false)
    try {
      soundPool?.release()
    } catch (_: Throwable) {}
    soundPool = null
    effectSoundIds.clear()
    loadedSoundIds.clear()
  }

  private fun ensureInitialized(context: Context) {
    if (isReleased.get()) {
      isReleased.set(false)
    }
    if (!initStarted.compareAndSet(false, true)) return

    val appCtx = context.applicationContext
    executor.execute {
      try {
        val attrs =
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pool =
          SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()
        pool.setOnLoadCompleteListener { _, sampleId, status ->
          if (status == 0) {
            loadedSoundIds[sampleId] = true
          }
        }
        soundPool = pool

        val sfxDir = File(appCtx.cacheDir, "astryn_sfx")
        if (!sfxDir.exists()) {
          sfxDir.mkdirs()
        }

        for (effect in AstrynSoundEffect.entries) {
          val wavFile = File(sfxDir, effect.fileName)
          if (!wavFile.exists() || wavFile.length() < 44L) {
            val samples = synthesizeSamples(effect)
            writeWavFile(wavFile, samples)
          }
          val id = pool.load(wavFile.absolutePath, 1)
          if (id != 0) {
            effectSoundIds[effect] = id
          }
        }
      } catch (_: Throwable) {
        initStarted.set(false)
      }
    }
  }

  private fun synthesizeSamples(effect: AstrynSoundEffect): ShortArray {
    return when (effect) {
      AstrynSoundEffect.TAP ->
        generateTone(durationMs = 20) { t, dur ->
          val freq = 1280.0 - 420.0 * (t / dur)
          val env = fastAttackDecay(t, dur, attackSec = 0.002, decayRate = 180.0)
          (0.65 * sin(2.0 * PI * freq * t) + 0.25 * sin(2.0 * PI * (freq * 1.5) * t)) * env
        }

      AstrynSoundEffect.TOGGLE_ON ->
        generateTwoStepTone(
          step1Ms = 42,
          freq1 = 587.33,
          step2Ms = 68,
          freq2 = 880.0,
        )

      AstrynSoundEffect.TOGGLE_OFF ->
        generateTwoStepTone(
          step1Ms = 42,
          freq1 = 739.99,
          step2Ms = 68,
          freq2 = 493.88,
        )

      AstrynSoundEffect.MESSAGE_SEND ->
        generateTone(durationMs = 70) { t, dur ->
          val progress = t / dur
          val freq = 460.0 + 560.0 * progress
          val env = fastAttackDecay(t, dur, attackSec = 0.005, decayRate = 38.0)
          (0.70 * sin(2.0 * PI * freq * t) + 0.22 * sin(2.0 * PI * freq * 2.0 * t)) * env
        }

      AstrynSoundEffect.MESSAGE_RECEIVE ->
        generateTone(durationMs = 125) { t, dur ->
          val env = fastAttackDecay(t, dur, attackSec = 0.006, decayRate = 24.0)
          val note1 = 0.55 * sin(2.0 * PI * 659.25 * t)
          val note2 = 0.45 * sin(2.0 * PI * 987.77 * t)
          (note1 + note2) * env
        }

      AstrynSoundEffect.VOICE_START ->
        generateTone(durationMs = 150) { t, dur ->
          val p = t / dur
          val freq =
            when {
              p < 0.33 -> 523.25
              p < 0.66 -> 659.25
              else -> 1046.50
            }
          val localT =
            when {
              p < 0.33 -> t
              p < 0.66 -> t - dur * 0.33
              else -> t - dur * 0.66
            }
          val env = fastAttackDecay(localT, dur * 0.38, attackSec = 0.004, decayRate = 26.0)
          (0.75 * sin(2.0 * PI * freq * t) + 0.20 * sin(2.0 * PI * freq * 2.0 * t)) * env
        }

      AstrynSoundEffect.VOICE_STOP ->
        generateTwoStepTone(
          step1Ms = 50,
          freq1 = 783.99,
          step2Ms = 75,
          freq2 = 523.25,
        )

      AstrynSoundEffect.SUCCESS ->
        generateTone(durationMs = 155) { t, dur ->
          val p = t / dur
          val freq =
            when {
              p < 0.35 -> 587.33
              p < 0.70 -> 880.0
              else -> 1174.66
            }
          val localT =
            when {
              p < 0.35 -> t
              p < 0.70 -> t - dur * 0.35
              else -> t - dur * 0.70
            }
          val env = fastAttackDecay(localT, dur * 0.40, attackSec = 0.004, decayRate = 22.0)
          (0.72 * sin(2.0 * PI * freq * t) + 0.20 * sin(2.0 * PI * freq * 1.5 * t)) * env
        }

      AstrynSoundEffect.ERROR ->
        generateTwoStepTone(
          step1Ms = 52,
          freq1 = 240.0,
          step2Ms = 68,
          freq2 = 175.0,
        )
    }
  }

  private fun generateTwoStepTone(
    step1Ms: Int,
    freq1: Double,
    step2Ms: Int,
    freq2: Double,
  ): ShortArray {
    val totalMs = step1Ms + step2Ms
    val splitSec = step1Ms / 1000.0
    return generateTone(durationMs = totalMs) { t, dur ->
      if (t < splitSec) {
        val env = fastAttackDecay(t, splitSec, attackSec = 0.003, decayRate = 34.0)
        (0.72 * sin(2.0 * PI * freq1 * t) + 0.18 * sin(2.0 * PI * freq1 * 2.0 * t)) * env
      } else {
        val localT = t - splitSec
        val segDur = dur - splitSec
        val env = fastAttackDecay(localT, segDur, attackSec = 0.003, decayRate = 28.0)
        (0.72 * sin(2.0 * PI * freq2 * t) + 0.18 * sin(2.0 * PI * freq2 * 2.0 * t)) * env
      }
    }
  }

  private inline fun generateTone(
    durationMs: Int,
    sampleGenerator: (timeSec: Double, durationSec: Double) -> Double,
  ): ShortArray {
    val numSamples = ((SAMPLE_RATE * durationMs) / 1000).coerceAtLeast(1)
    val durationSec = durationMs / 1000.0
    val out = ShortArray(numSamples)
    val fadeOutSamples = (SAMPLE_RATE * 0.008).toInt().coerceAtLeast(1)
    for (i in 0 until numSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      var v = sampleGenerator(t, durationSec)
      if (i > numSamples - fadeOutSamples) {
        val remain = (numSamples - i).toDouble() / fadeOutSamples
        v *= remain.coerceIn(0.0, 1.0)
      }
      val clamped = (v * 0.82).coerceIn(-1.0, 1.0)
      out[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
    }
    return out
  }

  private fun fastAttackDecay(
    t: Double,
    durationSec: Double,
    attackSec: Double,
    decayRate: Double,
  ): Double {
    if (t < 0.0 || durationSec <= 0.0) return 0.0
    val attack = if (attackSec > 0.0 && t < attackSec) t / attackSec else 1.0
    val decay = exp(-decayRate * t)
    return (attack * decay).coerceIn(0.0, 1.0)
  }

  private fun writeWavFile(file: File, samples: ShortArray) {
    val pcmByteCount = samples.size * 2
    val totalDataLen = pcmByteCount + 36
    val header =
      ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put('R'.code.toByte())
        put('I'.code.toByte())
        put('F'.code.toByte())
        put('F'.code.toByte())
        putInt(totalDataLen)
        put('W'.code.toByte())
        put('A'.code.toByte())
        put('V'.code.toByte())
        put('E'.code.toByte())
        put('f'.code.toByte())
        put('m'.code.toByte())
        put('t'.code.toByte())
        put(' '.code.toByte())
        putInt(16) // Subchunk1Size for PCM
        putShort(1.toShort()) // PCM format
        putShort(1.toShort()) // Mono
        putInt(SAMPLE_RATE)
        putInt(SAMPLE_RATE * 2) // ByteRate
        putShort(2.toShort()) // BlockAlign
        putShort(16.toShort()) // BitsPerSample
        put('d'.code.toByte())
        put('a'.code.toByte())
        put('t'.code.toByte())
        put('a'.code.toByte())
        putInt(pcmByteCount)
      }

    val pcmBytes = ByteBuffer.allocate(pcmByteCount).order(ByteOrder.LITTLE_ENDIAN)
    for (sample in samples) {
      pcmBytes.putShort(sample)
    }

    FileOutputStream(file).use { fos ->
      fos.write(header.array())
      fos.write(pcmBytes.array())
    }
  }
}
