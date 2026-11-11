package com.daniel.mood

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

data class OnlineMoodResult(val mood: Int, val confidence: Float, val explanation: String, val provider: String, val model: String)
data class OnlineTrack(
    val id: String, val title: String, val artist: String, val album: String,
    val artwork: String, val externalUrl: String, val previewUrl: String
)
data class LyricsResult(val title: String, val artist: String, val lyrics: String, val sourceUrl: String)

interface AudioAnalysisProvider {
    val providerName: String
    val modelName: String
    suspend fun analyze(song: Song): OnlineMoodResult
    suspend fun testConnection(): String
}
interface ConversationalAIProvider { suspend fun complete(systemPrompt: String, userPrompt: String): String }
interface MusicCatalogProvider { val providerName: String; suspend fun search(query: String, limit: Int = 20): List<OnlineTrack> }
interface MusicMetadataProvider { suspend fun metadata(trackId: String): OnlineTrack? }
interface MusicDiscoveryProvider { suspend fun discover(artist: String, mood: Int): List<OnlineTrack> }
interface LyricsProvider { val providerName: String; suspend fun lyrics(title: String, artist: String): LyricsResult? }

/** User-supplied credentials are AES/GCM encrypted with a non-exportable Android Keystore key. */
class CredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("secure_online", Context.MODE_PRIVATE)
    private fun cipher(forEncryption: Boolean, iv: ByteArray? = null): Cipher {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias("mood-online-key")) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder("mood-online-key", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generator.generateKey()
        }
        val secret = store.getKey("mood-online-key", null)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            if (forEncryption) init(Cipher.ENCRYPT_MODE, secret) else init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, iv))
        }
    }
    fun put(name: String, value: String) {
        if (value.isBlank()) { remove(name); return }
        val c = cipher(true)
        prefs.edit().putString(name, Base64.encodeToString(c.iv + c.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)).apply()
    }
    fun get(name: String): String = try {
        val bytes = Base64.decode(prefs.getString(name, ""), Base64.NO_WRAP)
        String(cipher(false, bytes.copyOfRange(0, 12)).doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    } catch (_: Exception) { "" }
    fun remove(name: String) { prefs.edit().remove(name).apply() }
}

object AiSettings {
    const val OFFLINE = "offline"
    const val ONLINE = "online"
    const val HYBRID = "hybrid"
    fun mode(context: Context) = prefs(context).getString("aiMode", HYBRID) ?: HYBRID
    fun enabled(context: Context) = mode(context) != OFFLINE
    fun askBeforeUpload(context: Context) = prefs(context).getBoolean("askUpload", true)
    fun automatic(context: Context) = prefs(context).getBoolean("autoOnline", false)
    fun maxUploadBytes(context: Context) = prefs(context).getInt("maxUploadMb", 15) * 1024L * 1024L
    fun dailyLimit(context: Context) = prefs(context).getInt("dailyLimit", 20)
    fun model(context: Context) = prefs(context).getString("model", "gemini-3.5-flash") ?: "gemini-3.5-flash"
    fun endpoint(context: Context) = prefs(context).getString("secureEndpoint", "") ?: ""
    fun prefs(context: Context) = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
}

private object Http {
    suspend fun request(
        url: String, method: String = "GET", body: ByteArray? = null,
        headers: Map<String, String> = emptyMap(), retries: Int = 2
    ): String = withContext(Dispatchers.IO) {
        var last: Exception? = null
        repeat(retries + 1) { attempt ->
            var connection: HttpURLConnection? = null
            try {
                if (!url.startsWith("https://")) throw IOException("Only HTTPS endpoints are allowed")
                connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.connectTimeout = 15_000
                connection.readTimeout = if (body != null) 60_000 else 25_000
                connection.setRequestProperty("User-Agent", "MoodMusicSorter/3.0 (Android; +https://github.com/dani8686/moodmusic)")
                connection.setRequestProperty("Accept", "application/json")
                headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
                if (body != null) {
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { it.write(body) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code in 200..299) return@withContext text
                val error = IOException("HTTP $code: ${text.take(600)}")
                if (code == 429 || code >= 500) {
                    last = error
                    if (attempt < retries) {
                        val retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull() ?: (attempt + 1L)
                        delay(retryAfter.coerceIn(1, 5) * 1000)
                        return@repeat
                    }
                }
                throw error
            } catch (e: Exception) {
                last = e
                val nonRetryableHttp = e is IOException && e.message?.startsWith("HTTP 4") == true && e.message?.startsWith("HTTP 429") != true
                if (attempt >= retries || nonRetryableHttp) throw e
                delay((attempt + 1L) * 800)
            } finally { connection?.disconnect() }
        }
        throw last ?: IOException("Network request failed")
    }
}

class GeminiProvider(private val context: Context) : AudioAnalysisProvider, ConversationalAIProvider {
    private val credentials = CredentialStore(context)
    override val providerName: String get() = if (AiSettings.endpoint(context).isNotBlank()) "Secure backend / Gemini" else "Gemini personal API"
    override val modelName: String get() = AiSettings.model(context)

    private suspend fun generate(parts: JSONArray): String {
        val root = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("temperature", .2).put("maxOutputTokens", 1000).put("responseMimeType", "application/json"))
        val endpoint = AiSettings.endpoint(context)
        val headers = mutableMapOf("Content-Type" to "application/json")
        val url = if (endpoint.isNotBlank()) {
            val token = credentials.get("backendToken")
            if (token.isNotBlank()) headers["Authorization"] = "Bearer $token"
            endpoint
        } else {
            val key = credentials.get("geminiKey")
            if (key.isBlank()) throw IOException("Provider is not connected")
            headers["x-goog-api-key"] = key
            "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
        }
        val response = JSONObject(Http.request(url, "POST", root.toString().toByteArray(), headers))
        return response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0).getString("text")
    }

    override suspend fun complete(systemPrompt: String, userPrompt: String): String {
        val parts = JSONArray().put(JSONObject().put("text", "$systemPrompt\n$userPrompt"))
        return generate(parts)
    }

    override suspend fun analyze(song: Song): OnlineMoodResult {
        if (song.size > AiSettings.maxUploadBytes(context)) throw IOException("Audio exceeds the configured upload limit")
        val bytes = context.contentResolver.openInputStream(Uri.parse(song.uri))?.use { it.readBytes() }
            ?: throw IOException("Cannot read audio")
        if (bytes.size > AiSettings.maxUploadBytes(context)) throw IOException("Audio exceeds the configured upload limit")
        val mime = context.contentResolver.getType(Uri.parse(song.uri)) ?: "audio/mpeg"
        val prompt = """Listen to the actual attached music, including Persian/Iranian arrangements. Do not use its title or artist. Classify acoustic mood into exactly one ID: 0 Happy, 1 Sad, 2 Calm, 3 Rainy, 4 Nostalgic, 5 Romantic, 6 Sport, 7 Energetic, 8 Emotional, 9 Instrumental. Return JSON only: {"mood":0,"confidence":0.0,"explanation":"short acoustic evidence"}. Be conservative: rainy/nostalgic/instrumental need strong evidence."""
        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", mime).put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))))
        val text = generate(parts).removePrefix("```json").removeSuffix("```").trim()
        val result = JSONObject(text)
        val mood = result.getInt("mood")
        if (mood !in Moods.all) throw IOException("Model returned an invalid mood")
        return OnlineMoodResult(mood, result.optDouble("confidence", .6).toFloat().coerceIn(0f, 1f), result.optString("explanation"), providerName, modelName)
    }

    override suspend fun testConnection(): String = complete("Return JSON {status:\"ok\"} only.", "Connection test")
}

class ITunesProvider : MusicCatalogProvider, MusicMetadataProvider, MusicDiscoveryProvider {
    override val providerName = "Apple iTunes Search"
    override suspend fun search(query: String, limit: Int): List<OnlineTrack> {
        if (query.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val root = JSONObject(Http.request("https://itunes.apple.com/search?term=$encoded&media=music&entity=song&limit=${limit.coerceIn(1, 50)}"))
        val results = root.getJSONArray("results")
        return List(results.length()) { index -> parse(results.getJSONObject(index)) }
    }
    override suspend fun metadata(trackId: String): OnlineTrack? {
        val root = JSONObject(Http.request("https://itunes.apple.com/lookup?id=${URLEncoder.encode(trackId, "UTF-8")}"))
        val results = root.getJSONArray("results")
        return if (results.length() > 0) parse(results.getJSONObject(0)) else null
    }
    override suspend fun discover(artist: String, mood: Int): List<OnlineTrack> {
        // Catalog search, not a claim that Apple supplies AI mood labels.
        return search(if (artist.isNotBlank()) artist else Moods.english.getOrElse(mood) { "music" }, 25)
    }
    private fun parse(item: JSONObject) = OnlineTrack(
        item.optString("trackId", item.optString("collectionId")), item.optString("trackName"),
        item.optString("artistName"), item.optString("collectionName"),
        item.optString("artworkUrl100").replace("100x100", "600x600"),
        item.optString("trackViewUrl"), item.optString("previewUrl")
    )
}

class LrcLibProvider : LyricsProvider {
    override val providerName = "LRCLIB"
    override suspend fun lyrics(title: String, artist: String): LyricsResult? {
        val query = URLEncoder.encode(title, "UTF-8")
        val performer = URLEncoder.encode(artist, "UTF-8")
        val root = JSONObject(Http.request("https://lrclib.net/api/get?track_name=$query&artist_name=$performer"))
        val plain = root.optString("plainLyrics")
        if (plain.isBlank()) return null
        return LyricsResult(root.optString("trackName", title), root.optString("artistName", artist), plain, root.optString("url", "https://lrclib.net"))
    }
}

class ProviderRegistry(private val context: Context) {
    val audio: AudioAnalysisProvider get() = GeminiProvider(context)
    val conversational: ConversationalAIProvider get() = GeminiProvider(context)
    val catalog: ITunesProvider = ITunesProvider()
    val lyrics: LyricsProvider = LrcLibProvider()
    fun configured(): Boolean = AiSettings.endpoint(context).isNotBlank() || CredentialStore(context).get("geminiKey").isNotBlank()
}

class OnlineGate(private val context: Context) {
    private val prefs = AiSettings.prefs(context)
    @Synchronized fun check(sizeBytes: Long = 0, audioUpload: Boolean = false): String? {
        if (!AiSettings.enabled(context)) return "Online services are disabled"
        if (audioUpload && sizeBytes > AiSettings.maxUploadBytes(context)) return "Audio exceeds the upload size limit"
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork
        val capabilities = manager.getNetworkCapabilities(network)
        val internet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (!internet) return "No internet connection"
        val wifiOnly = prefs.getBoolean("wifiOnly", true)
        val unmetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (wifiOnly && !wifi) return "Wi-Fi only policy blocked this request"
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val used = if (prefs.getString("usageDay", "") == day) prefs.getInt("usageCount", 0) else 0
        if (used >= AiSettings.dailyLimit(context)) return "Daily online request limit reached"
        val elapsed = System.currentTimeMillis() - prefs.getLong("lastOnlineRequest", 0)
        if (elapsed < 700) return "Please wait before the next request"
        return null
    }
    @Synchronized fun record() {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val used = if (prefs.getString("usageDay", "") == day) prefs.getInt("usageCount", 0) else 0
        prefs.edit().putString("usageDay", day).putInt("usageCount", used + 1).putLong("lastOnlineRequest", System.currentTimeMillis()).apply()
    }
}

class OnlineAnalysisCoordinator(private val context: Context) {
    private val registry = ProviderRegistry(context)
    private val gate = OnlineGate(context)
    private val dao = MoodDatabase.get(context).intelligence()
    private val songs = MoodDatabase.get(context).songs()

    suspend fun analyze(song: Song, confirmed: Boolean): Song {
        if (!confirmed && AiSettings.askBeforeUpload(context)) throw IOException("Audio upload consent is required")
        gate.check(song.size, audioUpload = true)?.let { throw IOException(it) }
        if (!registry.configured()) throw IOException("Connect a provider in AI settings first")
        val mode = AiSettings.mode(context)
        val localMood = if (song.localMood >= 0) song.localMood else song.autoMood
        val localConfidence = if (song.localMood >= 0) song.localConfidence else song.confidence
        gate.record()
        return try {
            val remote = registry.audio.analyze(song)
            val agreement = remote.mood == localMood
            val finalMood = if (mode == AiSettings.ONLINE || remote.confidence >= localConfidence || agreement) remote.mood else localMood
            val finalConfidence = if (agreement) (maxOf(remote.confidence, localConfidence) + .08f).coerceAtMost(.96f)
                else if (finalMood == remote.mood) remote.confidence.coerceAtMost(.86f) else localConfidence
            val source = if (mode == AiSettings.ONLINE) "online" else if (agreement) "hybrid_agree" else "hybrid_online"
            val updated = song.copy(
                mood = if (song.manual) song.mood else finalMood,
                autoMood = finalMood, confidence = finalConfidence,
                localMood = localMood, localConfidence = localConfidence,
                analysisSource = source, onlineExplanation = remote.explanation.take(300)
            )
            songs.put(updated)
            dao.log(AiProcessingLog(provider = remote.provider, model = remote.model, processingMode = mode, operation = "audio_mood", status = "success", createdAt = System.currentTimeMillis(), details = "agreement=$agreement"))
            updated
        } catch (e: Exception) {
            dao.log(AiProcessingLog(provider = registry.audio.providerName, model = registry.audio.modelName, processingMode = mode, operation = "audio_mood", status = "error", createdAt = System.currentTimeMillis(), details = e.message.orEmpty().take(300)))
            throw e
        }
    }
}
