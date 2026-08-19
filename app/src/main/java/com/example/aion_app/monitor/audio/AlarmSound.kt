package com.example.aion_app.monitor.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * 알람 비프음. HTML 원본과 동일하게 880-660-880Hz 3음 패턴을 재생하며 1초 스로틀.
 * AudioTrack(정적 모드)으로 생성한 PCM 버퍼를 별도 스레드에서 재생한다.
 */
class AlarmSound {
    private val sampleRate = 44100
    private val pattern: ShortArray by lazy { buildPattern() }
    @Volatile private var lastBeepMs = 0L

    fun beep() {
        val now = SystemClock.uptimeMillis()
        if (now - lastBeepMs < 1000) return
        lastBeepMs = now
        Thread { play(pattern) }.start()
    }

    private fun buildPattern(): ShortArray {
        val tones = listOf(880.0, 660.0, 880.0)
        val toneN = (sampleRate * 0.13).toInt()
        val gapN = (sampleRate * 0.04).toInt()
        val total = tones.size * toneN + (tones.size - 1) * gapN
        val out = ShortArray(total)
        var idx = 0
        val ramp = (sampleRate * 0.006).toInt() // 클릭 방지 페이드
        for ((ti, freq) in tones.withIndex()) {
            for (i in 0 until toneN) {
                val env = min(1.0, min(i.toDouble() / ramp, (toneN - i).toDouble() / ramp))
                val v = sin(2 * PI * freq * i / sampleRate) * env * 0.28
                out[idx++] = (v * Short.MAX_VALUE).toInt().toShort()
            }
            if (ti < tones.size - 1) idx += gapN // 무음 구간(0 유지)
        }
        return out
    }

    private fun play(buf: ShortArray) {
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buf.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (e: Exception) {
            return
        }
        try {
            track.write(buf, 0, buf.size)
            track.play()
            val durationMs = (buf.size * 1000L / sampleRate) + 60
            Thread.sleep(durationMs)
        } catch (_: Exception) {
        } finally {
            try { track.stop() } catch (_: Exception) {}
            track.release()
        }
    }
}
