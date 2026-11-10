package com.daniel.mood

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.*

// Signal-only analysis: no metadata reaches this class. Scores are acoustic estimates,
// not clinically or culturally validated emotion recognition.
object AudioAnalysis {
    private const val RATE = 11025
    private const val N = 1024
    private val window = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / (N - 1))).toFloat() }
    private val cosTable = FloatArray(N / 2) { cos(-2 * PI * it / N).toFloat() }
    private val sinTable = FloatArray(N / 2) { sin(-2 * PI * it / N).toFloat() }
    private fun fft(re: FloatArray, im: FloatArray) {
        var j = 0
        for (i in 1 until N) {
            var bit = N shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= N) {
            val half = len / 2; val step = N / len
            for (base in 0 until N step len) for (k in 0 until half) {
                val p = base + k; val q = p + half; val c = cosTable[k * step]; val s = sinTable[k * step]
                val tr = re[q] * c - im[q] * s; val ti = re[q] * s + im[q] * c
                re[q] = re[p] - tr; im[q] = im[p] - ti; re[p] += tr; im[p] += ti
            }
            len *= 2
        }
    }
    fun analyze(context: Context, uri: Uri, durationMs: Long, accurate: Boolean, profiles: List<LearningProfile> = emptyList()): Result {
        val seconds = if (accurate) 9 else 5
        val samples = FloatArray(seconds * RATE * 3)
        var count = 0
        for (part in 0..2) {
            val center = durationMs * (part + 1) / 4
            val start = max(0L, center - seconds * 500L)
            count += decode(context, uri, start, seconds, samples, count)
        }
        if (count < RATE * 2) throw IllegalArgumentException("short")
        val f = extract(samples, count)
        if (f.rms < 0.0008f) throw IllegalArgumentException("silent")
        return MoodClassifier.classify(f, profiles)
    }
    private fun decode(context: Context, uri: Uri, startMs: Long, seconds: Int, dest: FloatArray, offset: Int): Int {
        val extractor = MediaExtractor(); var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            for (i in 0 until extractor.trackCount) if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
            if (track < 0) throw IllegalArgumentException("unsupported")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IllegalArgumentException("unsupported")
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0); codec.start()
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = 2 // PCM 16-bit
            var phase = 0; var written = 0; var inputDone = false; var outputDone = false; var idle = 0
            val info = MediaCodec.BufferInfo(); val target = min(seconds * RATE, dest.size - offset)
            while (!outputDone && written < target && idle < 250) {
                var progress = false
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val input = codec.getInputBuffer(index) ?: throw IllegalStateException("decode")
                        input.clear(); val size = extractor.readSampleData(input, 0)
                        if (size < 0) { codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { val time = extractor.sampleTime.coerceAtLeast(0); codec.queueInputBuffer(index, 0, size, time, 0); extractor.advance() }
                        progress = true
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outFormat = codec.outputFormat
                    rate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    encoding = if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else 2
                    if (channels !in 1..8 || rate !in 8000..384000 || (encoding != 2 && encoding != 4)) throw IllegalArgumentException("unsupported")
                    progress = true
                } else if (outIndex >= 0) {
                    val out = codec.getOutputBuffer(outIndex)
                    if (out != null && info.size > 0 && info.presentationTimeUs >= startMs * 1000) {
                        out.order(ByteOrder.LITTLE_ENDIAN); out.position(info.offset); out.limit(info.offset + info.size)
                        val frameBytes = channels * if (encoding == 4) 4 else 2
                        while (out.remaining() >= frameBytes && written < target) {
                            var sum = 0f
                            for (c in 0 until channels) sum += if (encoding == 4) out.float else out.short / 32768f
                            phase += RATE
                            if (phase >= rate) { dest[offset + written++] = (sum / channels).coerceIn(-1f, 1f); phase -= rate }
                        }
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    codec.releaseOutputBuffer(outIndex, false); progress = true
                }
                idle = if (progress) 0 else idle + 1
            }
            return written
        } finally { try { codec?.stop() } catch (_: Exception) {} ; try { codec?.release() } catch (_: Exception) {}; extractor.release() }
    }
    private fun extract(x: FloatArray, count: Int): Features {
        var power = 0.0; var crossings = 0
        for (i in 0 until count) { power += x[i]*x[i]; if (i>0 && (x[i]>=0) != (x[i-1]>=0)) crossings++ }
        val rms = sqrt(power / count).toFloat(); val re = FloatArray(N); val im = FloatArray(N); val previous = FloatArray(N/2)
        val frames = max(1, (count-N)/(N/2)+1); val envelope = FloatArray(frames); val chroma = FloatArray(12)
        var centroid = 0.0; var rolloff = 0.0; var bass = 0.0; var flux = 0.0; var midBand = 0.0; var flatness = 0.0; var rmsMin = Float.MAX_VALUE; var rmsMax = 0f; var f = 0
        var pos = 0
        while (pos + N <= count) {
            var local = 0f
            for (i in 0 until N) { val v = x[pos+i]; local += v*v; re[i] = v * window[i]; im[i] = 0f }
            val frameRms = sqrt(local/N); rmsMin = min(rmsMin, frameRms); rmsMax = max(rmsMax, frameRms)
            fft(re, im); var total = 0.0; var weighted = 0.0; var low = 0.0; var mid = 0.0; var diff = 0.0
            for (i in 1 until N/2) {
                val mag = sqrt(re[i]*re[i]+im[i]*im[i]); val hz = i.toFloat()*RATE/N
                total += mag; weighted += mag*hz; if (hz < 250) low += mag
                if (hz in 300f..3400f) mid += mag
                diff += max(0f, mag-previous[i]); previous[i] = mag
                if (hz in 65f..4000f) { val midi = (69 + 12 * log2(hz / 440f)).roundToInt(); chroma[((midi%12)+12)%12] += mag }
            }
            var cumulative = 0.0; var edge = 0
            for (i in 1 until N/2) { cumulative += previous[i]; if (cumulative >= total*.85) { edge = i; break } }
            if (total > 1e-9) {
                centroid += weighted/total; bass += low/total; midBand += mid/total; flux += (diff/total).coerceIn(0.0, 1.0)
                var logSum = 0.0; var bins = 0
                for (i in 1 until N/2) { logSum += ln(previous[i].toDouble() + 1e-8); bins++ }
                val geometric = exp(logSum / bins)
                val arithmetic = total / bins
                flatness += (geometric / (arithmetic + 1e-8)).coerceIn(0.0, 1.0)
            }
            rolloff += edge.toDouble()*RATE/N; envelope[f++] = diff.toFloat(); pos += N/2
        }
        val actual = max(1, f); val mean = envelope.take(f).average().let { if (it.isNaN()) 0.0 else it }
        var onset = 0; for (i in 1 until f-1) if (envelope[i] > mean*1.5 && envelope[i] > envelope[i-1] && envelope[i] >= envelope[i+1]) onset++
        var best = 0.0; var bestLag = 0; var totalCorr = 0.0
        val minLag = max(1, (60.0*RATE/(N/2)/200).roundToInt()); val maxLag = min(f-1, (60.0*RATE/(N/2)/55).roundToInt())
        for (lag in minLag..maxLag) { var corr = 0.0; for (i in lag until f) corr += envelope[i].toDouble()*envelope[i-lag]; if (lag == minLag) totalCorr = corr; if (corr > best) { best = corr; bestLag = lag } }
        val beat = if (totalCorr > 0) (best/totalCorr).toFloat().coerceIn(0f,1f) else 0f
        val majorProfile = floatArrayOf(6.35f,2.23f,3.48f,2.33f,4.38f,4.09f,2.52f,5.19f,2.39f,3.66f,2.29f,2.88f)
        val minorProfile = floatArrayOf(6.33f,2.68f,3.52f,5.38f,2.60f,3.53f,2.54f,4.75f,3.98f,2.69f,3.34f,3.17f)
        var major = 0f; var minor = 0f
        for (root in 0..11) { var a=0f; var b=0f; for(i in 0..11) { a += chroma[(root+i)%12]*majorProfile[i]; b += chroma[(root+i)%12]*minorProfile[i] }; major=max(major,a); minor=max(minor,b) }
        val spectralFlatness = (flatness / actual).toFloat().coerceIn(0f, 1f)
        val tonal = (1f - spectralFlatness * 2.2f).coerceIn(0f, 1f)
        val normalizedMid = (midBand / actual).toFloat().coerceIn(0f, 1f)
        val vocalProxy = (normalizedMid * .53f + spectralFlatness * .27f + (flux / actual).toFloat().coerceIn(0f,1f) * .20f).coerceIn(0f, 1f)
        return Features(if(bestLag>0) (60f*RATE/(N/2)/bestLag) else 0f, beat, rms, (rmsMax-rmsMin).coerceAtLeast(0f), (centroid/actual).toFloat(), (rolloff/actual).toFloat(), (flux/actual).toFloat(), crossings.toFloat()/count, if(minor+major>0) (minor/(minor+major)) else .5f, (bass/actual).toFloat(), onset.toFloat()*RATE/((N/2)*actual), vocalProxy, tonal)
    }
}
