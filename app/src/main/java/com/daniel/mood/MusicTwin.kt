package com.daniel.mood

import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter

sealed class TwinAction {
    data class PlayMood(val mood: Int) : TwinAction()
    data class Search(val query: String) : TwinAction()
    data class Similar(val songId: Long?) : TwinAction()
    data class Discover(val unheardOnly: Boolean) : TwinAction()
    data class StartDj(val mode: String) : TwinAction()
    data class Journey(val start: Int, val end: Int, val minutes: Int) : TwinAction()
    data class ShowHistory(val period: String) : TwinAction()
    object ExplainDna : TwinAction()
    object CompareMonths : TwinAction()
    object ResetDna : TwinAction()
    object DeleteHistory : TwinAction()
    data class Unknown(val reason: String = "local_fallback") : TwinAction()
}

object TwinParser {
    private val moodWords = listOf(
        Moods.HAPPY to listOf("شاد", "happy"), Moods.SAD to listOf("غمگین", "غم", "sad"),
        Moods.CALM to listOf("آرام", "آروم", "calm"), Moods.RAINY to listOf("بارونی", "بارانی", "rainy"),
        Moods.NOSTALGIC to listOf("نوستالژی", "نوستالژیک", "nostalgic"), Moods.ROMANTIC to listOf("عاشقانه", "رمانتیک", "romantic"),
        Moods.SPORT to listOf("ورزشی", "ورزش", "workout", "sport"), Moods.ENERGETIC to listOf("پرانرژی", "انرژی", "energetic"),
        Moods.EMOTIONAL to listOf("احساسی", "emotional"), Moods.INSTRUMENTAL to listOf("بی کلام", "بی‌کلام", "instrumental")
    )
    private fun mood(text: String, default: Int? = null): Int? = moodWords.firstOrNull { (_, words) -> words.any { text.contains(it) } }?.first ?: default

    fun parse(input: String, currentSongId: Long?): TwinAction {
        val text = input.trim().lowercase()
        if (text.isBlank()) return TwinAction.Unknown()
        if (text.contains("حذف تاریخچه") || text.contains("delete history")) return TwinAction.DeleteHistory
        if (text.contains("ریست") && (text.contains("dna") || text.contains("سلایق") || text.contains("سلایق")) || text.contains("reset music dna")) return TwinAction.ResetDna
        if (text.contains("مقایسه") && (text.contains("ماه") || text.contains("month"))) return TwinAction.CompareMonths
        if (text.contains("این ماه") || text.contains("this month")) return TwinAction.ShowHistory("month")
        if (text.contains("هفته") || text.contains("this week")) return TwinAction.ShowHistory("week")
        if (text.contains("تاریخچه") || text.contains("history") || text.contains("گذشته")) return TwinAction.ShowHistory("month")
        if (text.contains("سلایق") || text.contains("سلایق") || text.contains("music dna") || text.contains("taste")) return TwinAction.ExplainDna
        if (text.contains("مشابه") || text.contains("شبیه") || text.contains("similar")) return TwinAction.Similar(currentSongId)
        if (text.contains("فراموش") || text.contains("forgotten")) return TwinAction.Discover(false)
        if (text.contains("نشنیده") || text.contains("جدید") || text.contains("never heard") || text.contains("discover")) return TwinAction.Discover(true)
        if (text.contains("سفر") || text.contains("journey")) {
            val start = moodWords.firstOrNull { (_, words) -> words.any { text.contains("از $it") || text.contains("from $it") } }?.first ?: Moods.SAD
            val end = moodWords.lastOrNull { (_, words) -> words.any { text.contains("به $it") || text.contains("to $it") } }?.first ?: Moods.HAPPY
            val minutes = Regex("(\\d{1,3})").find(text)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(10, 240) ?: 45
            return TwinAction.Journey(start, end, minutes)
        }
        if (text.contains("دی جی") || text.contains("دیجی") || text.contains("dj")) {
            val mode = when {
                text.contains("شب") || text.contains("night") -> "night"
                text.contains("ورزش") || text.contains("workout") -> "workout"
                text.contains("آرام") || text.contains("chill") -> "chill"
                text.contains("احساسی") -> "emotional"
                text.contains("نوستالژی") -> "nostalgic"
                else -> "party"
            }
            return TwinAction.StartDj(mode)
        }
        mood(text)?.let { if (text.contains("پخش") || text.contains("play") || text.contains("پلی") || text.contains("بساز")) return TwinAction.PlayMood(it) }
        if (text.contains("رانندگی شب") || text.contains("night drive")) return TwinAction.StartDj("night")
        val query = text.removePrefix("جستجو").removePrefix("search").removePrefix("پیدا کن").trim()
        if (query.isNotBlank()) return TwinAction.Search(query)
        return TwinAction.Unknown()
    }

    suspend fun interpretOnline(context: android.content.Context, input: String, currentSongId: Long?, dnaSummary: String): TwinAction {
        val local = parse(input, currentSongId)
        if (local !is TwinAction.Unknown) return local
        val system = """You are a music assistant. Convert Persian or English requests to one whitelisted JSON action only. Types: play_mood(mood 0..9), search(query), similar, discover(unheardOnly), start_dj(mode party/chill/emotional/workout/night/nostalgic/discovery), journey(start,end,minutes), history(period week/month), explain_dna, compare_months, unknown. Never execute code. Use this real Music DNA summary: $dnaSummary"""
        val text = ProviderRegistry(context).conversational.complete(system, input)
        val json = JSONObject(text.removePrefix("```json").removeSuffix("```").trim())
        return when (json.optString("type")) {
            "play_mood" -> TwinAction.PlayMood(json.optInt("mood").takeIf { it in Moods.all } ?: return TwinAction.Unknown())
            "search" -> TwinAction.Search(json.optString("query").takeIf { it.isNotBlank() } ?: return TwinAction.Unknown())
            "similar" -> TwinAction.Similar(currentSongId)
            "discover" -> TwinAction.Discover(json.optBoolean("unheardOnly", true))
            "start_dj" -> TwinAction.StartDj(json.optString("mode", "party"))
            "journey" -> TwinAction.Journey(json.optInt("start"), json.optInt("end"), json.optInt("minutes", 45).coerceIn(10, 240))
            "history" -> TwinAction.ShowHistory(json.optString("period", "month"))
            "explain_dna" -> TwinAction.ExplainDna
            "compare_months" -> TwinAction.CompareMonths
            else -> TwinAction.Unknown("online_unsupported")
        }
    }
}

object TimeRanges {
    fun day(epoch: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val date = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate()
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to
            date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
    }
    fun daysBack(days: Long): Pair<Long, Long> {
        val end = System.currentTimeMillis()
        return day(end).first - (days - 1).coerceAtLeast(0) * 86400000L to end
    }
    fun thisMonth(): Pair<Long, Long> {
        val now = LocalDate.now()
        return now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to System.currentTimeMillis()
    }
    fun lastMonth(): Pair<Long, Long> {
        val now = LocalDate.now()
        val first = now.withDayOfMonth(1).minusMonths(1)
        val next = first.plusMonths(1)
        return first.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to
            next.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
    }
    fun label(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
}

fun recommendationReason(code: String, fa: Boolean): String = when (code) {
    "similar_audio" -> tr(fa, "Similar rhythm, energy and spectral character", "ریتم، انرژی و ویژگی طیفی مشابه")
    "liked_characteristics" -> tr(fa, "Audio character similar to songs you liked", "ویژگی صوتی مشابه آهنگ‌های لایک‌شده")
    "unheard_dna_match" -> tr(fa, "Underplayed and close to your Music DNA", "کم‌پخش و نزدیک به DNA موسیقی شما")
    "preferred_energy_rhythm" -> tr(fa, "Matches your preferred energy and rhythm", "با انرژی و ریتم محبوب شما هماهنگ است")
    else -> tr(fa, "Compatible mood and audio features", "حال‌وهوا و ویژگی‌های صوتی سازگار")
}
