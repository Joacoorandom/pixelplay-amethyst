package gg.pixelplay.android.social

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Process
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/** PCM16 16 kHz mono, 20 ms frames — same wire format as the desktop malgo engine. */
class VoiceEngine(context: Context) {
    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_BYTES = 640
        const val CODEC_PCM16: Byte = 1
        private const val START_FRAMES = 3
        private const val MAX_FRAMES = 15
    }

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val queue = ArrayBlockingQueue<ByteArray>(MAX_FRAMES + 2)
    @Volatile private var running = false
    @Volatile var muted = false
    @Volatile var deafened = false
    @Volatile var level = 0f
        private set
    private var captureThread: Thread? = null
    private var playThread: Thread? = null
    private var effects = mutableListOf<android.media.audiofx.AudioEffect>()
    private var previousMode = AudioManager.MODE_NORMAL

    @SuppressLint("MissingPermission")
    fun start(onFrame: (ByteArray) -> Unit) {
        if (running) return
        running = true
        queue.clear()
        previousMode = audio.mode
        audio.mode = AudioManager.MODE_IN_COMMUNICATION

        val recMin = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(recMin, FRAME_BYTES * 4),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            running = false
            audio.mode = previousMode
            throw IllegalStateException("No se pudo abrir el micrófono")
        }
        val session = record.audioSessionId
        if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(session)?.also { it.enabled = true; effects += it }
        if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(session)?.also { it.enabled = true; effects += it }
        if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(session)?.also { it.enabled = true; effects += it }

        val playMin = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setSessionId(session)
            .setBufferSizeInBytes(maxOf(playMin, FRAME_BYTES * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        captureThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buf = ByteArray(FRAME_BYTES)
            record.startRecording()
            try {
                while (running) {
                    var off = 0
                    while (off < FRAME_BYTES && running) {
                        val n = record.read(buf, off, FRAME_BYTES - off)
                        if (n <= 0) break
                        off += n
                    }
                    if (off < FRAME_BYTES) continue
                    level = rms(buf)
                    if (muted) continue
                    val frame = ByteArray(FRAME_BYTES + 1)
                    frame[0] = CODEC_PCM16
                    System.arraycopy(buf, 0, frame, 1, FRAME_BYTES)
                    onFrame(frame)
                }
            } finally {
                runCatching { record.stop() }
                record.release()
            }
        }, "pixel-voice-capture").also { it.start() }

        playThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val silence = ByteArray(FRAME_BYTES)
            var primed = false
            track.play()
            try {
                while (running) {
                    if (!primed) {
                        if (queue.size < START_FRAMES) { Thread.sleep(10); continue }
                        primed = true
                    }
                    val frame = queue.poll(40, TimeUnit.MILLISECONDS)
                    if (frame == null) {
                        primed = false
                        track.write(silence, 0, silence.size)
                        continue
                    }
                    track.write(if (deafened) silence else frame, 0, FRAME_BYTES)
                }
            } catch (_: InterruptedException) {
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }, "pixel-voice-play").also { it.start() }
    }

    /** Frame from the peer, including the 1-byte codec prefix. */
    fun feed(data: ByteArray) {
        if (!running || data.size != FRAME_BYTES + 1 || data[0] != CODEC_PCM16) return
        val pcm = data.copyOfRange(1, data.size)
        while (queue.size >= MAX_FRAMES) queue.poll()
        queue.offer(pcm)
    }

    fun setSpeaker(on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (on) {
                audio.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let(audio::setCommunicationDevice)
            } else {
                audio.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = on
        }
    }

    fun stop() {
        if (!running) return
        running = false
        playThread?.interrupt()
        captureThread?.join(300)
        playThread?.join(300)
        captureThread = null
        playThread = null
        effects.forEach { runCatching { it.release() } }
        effects.clear()
        queue.clear()
        level = 0f
        setSpeaker(false)
        audio.mode = previousMode
    }

    private fun rms(buf: ByteArray): Float {
        var sum = 0.0
        var i = 0
        while (i < buf.size) {
            val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toDouble() / 32768.0
            sum += s * s
            i += 2
        }
        return sqrt(sum / (buf.size / 2)).toFloat()
    }
}
