package com.daniel.mood

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "listening_events",
    indices = [Index("startedAt"), Index("songId"), Index("startedAt", "songId")],
    foreignKeys = [ForeignKey(entity = Song::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)]
)
data class ListeningEvent(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val songId: Long,
    val startedAt: Long,
    val listenedMs: Long,
    val trackDurationMs: Long,
    val completionRatio: Float,
    val skipped: Boolean,
    val source: String,
    val interaction: String = "none"
)

@Entity(tableName = "taste_profiles")
data class TasteProfile(
    @PrimaryKey val profileId: Int = 1,
    val featureSums: String,
    val moodSums: String,
    val totalWeight: Float,
    val eventCount: Long,
    val version: Int,
    val updatedAt: Long
)

@Entity(tableName = "mood_journeys")
data class MoodJourney(
    @PrimaryKey(autoGenerate = true) val journeyId: Long = 0,
    val name: String,
    val startingMood: Int,
    val targetMood: Int,
    val requestedMinutes: Int,
    val energy: Float,
    val familiarity: Float,
    val songIds: String,
    val generatedAt: Long,
    val favorite: Boolean = true
)

@Entity(tableName = "recommendations", indices = [Index("contextKey"), Index("songId")])
data class Recommendation(
    @PrimaryKey(autoGenerate = true) val recommendationId: Long = 0,
    val songId: Long,
    val contextKey: String,
    val reason: String,
    val score: Float,
    val feedback: Int = 0,
    val generatedAt: Long
)

@Entity(tableName = "dj_sessions")
data class DjSession(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val mode: String,
    val startedAt: Long,
    val listenedMs: Long
)

@Entity(tableName = "ai_processing_logs", indices = [Index("createdAt"), Index("status")])
data class AiProcessingLog(
    @PrimaryKey(autoGenerate = true) val operationId: Long = 0,
    val provider: String,
    val model: String,
    val processingMode: String,
    val operation: String,
    val status: String,
    val createdAt: Long,
    val details: String = ""
)

@Entity(tableName = "online_wishlist", indices = [Index("addedAt")])
data class OnlineWishlist(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val artwork: String,
    val externalUrl: String,
    val addedAt: Long
)

data class DailyListening(val day: String, val listenedMs: Long, val events: Int)
data class SongListening(val id: Long, val title: String, val artist: String, val listenedMs: Long, val plays: Int)
data class ArtistListening(val artist: String, val listenedMs: Long, val plays: Int)
data class MoodListening(val mood: Int, val listenedMs: Long)
data class PeriodListening(val listenedMs: Long, val events: Int, val skips: Int)

@Dao
interface IntelligenceDao {
    @Insert suspend fun addEvent(event: ListeningEvent): Long
    @Query("SELECT * FROM listening_events ORDER BY startedAt DESC LIMIT :limit") suspend fun recentEvents(limit: Int = 100): List<ListeningEvent>
    @Query("SELECT * FROM listening_events WHERE startedAt BETWEEN :start AND :end ORDER BY startedAt DESC") suspend fun eventsBetween(start: Long, end: Long): List<ListeningEvent>
    @Query("DELETE FROM listening_events") suspend fun clearHistory()
    @Query("DELETE FROM listening_events WHERE startedAt < :before") suspend fun pruneHistory(before: Long)

    @Query("SELECT strftime('%Y-%m-%d', startedAt/1000, 'unixepoch', 'localtime') AS day, SUM(listenedMs) AS listenedMs, COUNT(*) AS events FROM listening_events WHERE startedAt BETWEEN :start AND :end GROUP BY day ORDER BY day DESC")
    suspend fun daily(start: Long, end: Long): List<DailyListening>
    @Query("SELECT s.id, s.title, s.artist, SUM(e.listenedMs) AS listenedMs, COUNT(*) AS plays FROM listening_events e JOIN songs s ON s.id=e.songId WHERE e.startedAt BETWEEN :start AND :end GROUP BY s.id ORDER BY listenedMs DESC LIMIT :limit")
    suspend fun topSongs(start: Long, end: Long, limit: Int = 20): List<SongListening>
    @Query("SELECT s.artist, SUM(e.listenedMs) AS listenedMs, COUNT(*) AS plays FROM listening_events e JOIN songs s ON s.id=e.songId WHERE e.startedAt BETWEEN :start AND :end GROUP BY s.artist ORDER BY listenedMs DESC LIMIT :limit")
    suspend fun topArtists(start: Long, end: Long, limit: Int = 20): List<ArtistListening>
    @Query("SELECT s.mood, SUM(e.listenedMs) AS listenedMs FROM listening_events e JOIN songs s ON s.id=e.songId WHERE e.startedAt BETWEEN :start AND :end GROUP BY s.mood ORDER BY listenedMs DESC")
    suspend fun moods(start: Long, end: Long): List<MoodListening>
    @Query("SELECT COALESCE(SUM(listenedMs),0) AS listenedMs, COUNT(*) AS events, COALESCE(SUM(skipped),0) AS skips FROM listening_events WHERE startedAt BETWEEN :start AND :end")
    suspend fun period(start: Long, end: Long): PeriodListening

    @Query("SELECT * FROM taste_profiles WHERE profileId=1") suspend fun taste(): TasteProfile?
    @Upsert suspend fun putTaste(profile: TasteProfile)
    @Query("DELETE FROM taste_profiles") suspend fun resetTaste()

    @Insert suspend fun addJourney(journey: MoodJourney): Long
    @Update suspend fun updateJourney(journey: MoodJourney)
    @Query("SELECT * FROM mood_journeys ORDER BY generatedAt DESC") fun journeys(): Flow<List<MoodJourney>>
    @Query("DELETE FROM mood_journeys WHERE journeyId=:id") suspend fun deleteJourney(id: Long)

    @Query("SELECT * FROM recommendations WHERE contextKey=:context ORDER BY score DESC") suspend fun recommendations(context: String): List<Recommendation>
    @Insert suspend fun addRecommendations(items: List<Recommendation>)
    @Query("DELETE FROM recommendations WHERE contextKey=:context") suspend fun clearRecommendations(context: String)
    @Query("DELETE FROM recommendations") suspend fun clearAllRecommendations()
    @Query("UPDATE recommendations SET feedback=:feedback WHERE recommendationId=:id") suspend fun recommendationFeedback(id: Long, feedback: Int)
    @Transaction suspend fun replaceRecommendations(context: String, items: List<Recommendation>) {
        clearRecommendations(context); addRecommendations(items)
    }

    @Insert suspend fun addDjSession(session: DjSession)
    @Insert suspend fun log(log: AiProcessingLog)
    @Query("SELECT * FROM ai_processing_logs ORDER BY createdAt DESC LIMIT :limit") suspend fun recentAiLogs(limit: Int = 100): List<AiProcessingLog>
    @Query("SELECT COUNT(*) FROM ai_processing_logs WHERE createdAt BETWEEN :start AND :end") suspend fun aiRequests(start: Long, end: Long): Int
    @Query("SELECT COUNT(*) FROM ai_processing_logs WHERE status='success'") suspend fun successfulAiRequests(): Int
    @Query("SELECT MAX(createdAt) FROM ai_processing_logs WHERE status='success'") suspend fun lastAiSuccess(): Long?
    @Query("DELETE FROM ai_processing_logs") suspend fun clearAiLogs()

    @Upsert suspend fun addWishlist(item: OnlineWishlist)
    @Query("DELETE FROM online_wishlist WHERE trackId=:id") suspend fun removeWishlist(id: String)
    @Query("SELECT * FROM online_wishlist ORDER BY addedAt DESC") fun wishlist(): Flow<List<OnlineWishlist>>
}
