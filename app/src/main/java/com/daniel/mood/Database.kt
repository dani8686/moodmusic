package com.daniel.mood

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "songs", indices = [Index("mood"), Index("artist"), Index("title"), Index("seen")])
data class Song(
    @PrimaryKey val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val size: Long,
    val modified: Long,
    val added: Long,
    val mood: Int,
    val secondary: Int,
    val confidence: Float,
    val bpm: Float,
    val energy: Float,
    val reason: String,
    val manual: Boolean,
    val seen: Long,
    @ColumnInfo(defaultValue = "10") val autoMood: Int = mood,
    @ColumnInfo(defaultValue = "-1") val albumId: Long = -1L,
    @ColumnInfo(defaultValue = "''") val featureData: String = "",
    @ColumnInfo(defaultValue = "2") val analysisVersion: Int = 2,
    @ColumnInfo(defaultValue = "0") val feedback: Int = 0,
    @ColumnInfo(defaultValue = "'local'") val analysisSource: String = "local",
    @ColumnInfo(defaultValue = "-1") val localMood: Int = -1,
    @ColumnInfo(defaultValue = "0") val localConfidence: Float = 0f,
    @ColumnInfo(defaultValue = "''") val onlineExplanation: String = ""
)

@Entity(tableName = "mood_corrections", indices = [Index("correctedMood"), Index("audioUri")])
data class MoodCorrection(
    @PrimaryKey val trackId: Long,
    val audioUri: String,
    val predictedMood: Int,
    val correctedMood: Int,
    val modelConfidence: Float,
    val features: String,
    val correctedAt: Long
)

@Entity(tableName = "mood_profiles")
data class MoodProfile(@PrimaryKey val mood: Int, val count: Int, val sums: String) {
    fun learningProfile(): LearningProfile? {
        val values = Features.vector(sums) ?: return null
        return LearningProfile(mood, count, values)
    }
}

@Entity(tableName = "playlists")
data class Playlist(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val createdAt: Long)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Song::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("songId")]
)
data class PlaylistSong(val playlistId: Long, val songId: Long, val position: Long, val addedAt: Long)

data class MoodCount(val mood: Int, val count: Int)
data class PlaylistWithCount(val id: Long, val name: String, val createdAt: Long, val songCount: Int)

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE id=:id") suspend fun get(id: Long): Song?
    @Upsert suspend fun put(song: Song)
    @Query("UPDATE songs SET feedback=:feedback WHERE id=:id") suspend fun feedback(id: Long, feedback: Int)
    @Query("UPDATE songs SET seen=:seen WHERE id=:id") suspend fun touch(id: Long, seen: Long)
    @Query("DELETE FROM songs WHERE seen != :seen") suspend fun prune(seen: Long)
    @Query("SELECT mood, COUNT(*) AS count FROM songs GROUP BY mood") fun counts(): Flow<List<MoodCount>>
    @Query("SELECT * FROM songs WHERE (:mood = -1 OR mood = :mood) AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%') ORDER BY CASE WHEN :sort = 0 THEN title END COLLATE NOCASE, CASE WHEN :sort = 1 THEN artist END COLLATE NOCASE, CASE WHEN :sort = 2 THEN added END DESC, CASE WHEN :sort = 3 THEN bpm END DESC, CASE WHEN :sort = 4 THEN energy END DESC, id LIMIT :limit OFFSET :offset")
    fun page(mood: Int, query: String, sort: Int, limit: Int, offset: Int): Flow<List<Song>>
    @Query("SELECT * FROM songs WHERE (:mood = -1 OR mood = :mood) ORDER BY title COLLATE NOCASE") suspend fun playlist(mood: Int): List<Song>
    @Query("SELECT * FROM mood_profiles") suspend fun profiles(): List<MoodProfile>
    @Query("SELECT * FROM mood_profiles WHERE mood=:mood") suspend fun profile(mood: Int): MoodProfile?
    @Upsert suspend fun putProfile(profile: MoodProfile)
    @Query("SELECT * FROM mood_corrections WHERE trackId=:trackId") suspend fun correction(trackId: Long): MoodCorrection?
    @Upsert suspend fun putCorrection(correction: MoodCorrection)
    @Query("DELETE FROM mood_corrections WHERE trackId=:trackId") suspend fun deleteCorrection(trackId: Long)
    @Query("SELECT COUNT(*) FROM mood_corrections") fun correctionCount(): Flow<Int>
    @Query("UPDATE songs SET modified=-1 WHERE id=:id") suspend fun invalidate(id: Long)
    @Query("UPDATE songs SET modified=-1") suspend fun invalidateAll()
    @Query("UPDATE songs SET feedback=:feedback WHERE id=:id") suspend fun setFeedback(id: Long, feedback: Int)
    @Query("UPDATE songs SET analysisSource='local', mood=CASE WHEN manual=1 THEN mood ELSE localMood END, autoMood=CASE WHEN localMood>=0 THEN localMood ELSE autoMood END, confidence=CASE WHEN localMood>=0 THEN localConfidence ELSE confidence END WHERE analysisSource != 'local'") suspend fun clearOnlineAnalysis()

    @Transaction
    suspend fun correct(trackId: Long, newMood: Int) {
        val song = get(trackId) ?: return
        val features = Features.vector(song.featureData)
        val previous = correction(trackId)
        if (features != null && previous != null) adjustProfile(previous.correctedMood, features, -1)
        if (features != null) adjustProfile(newMood, features, 1)
        putCorrection(MoodCorrection(trackId, song.uri, song.autoMood, newMood, song.confidence, song.featureData, System.currentTimeMillis()))
        put(song.copy(mood = newMood, manual = true))
    }

    @Transaction
    suspend fun restoreAutomatic(trackId: Long) {
        val song = get(trackId) ?: return
        val previous = correction(trackId)
        val features = Features.vector(song.featureData)
        if (previous != null && features != null) adjustProfile(previous.correctedMood, features, -1)
        deleteCorrection(trackId)
        put(song.copy(mood = song.autoMood, manual = false))
    }

    private suspend fun adjustProfile(mood: Int, vector: FloatArray, direction: Int) {
        val old = profile(mood)
        val count = (if (old == null) 0 else old.count) + direction
        val sums = old?.let { Features.vector(it.sums) } ?: FloatArray(vector.size)
        if (sums.size != vector.size) return
        for (i in sums.indices) sums[i] = (sums[i] + direction * vector[i]).coerceAtLeast(0f)
        if (count <= 0) putProfile(MoodProfile(mood, 0, FloatArray(vector.size).joinToString(",")))
        else putProfile(MoodProfile(mood, count, sums.joinToString(",")))
    }
}

@Dao
interface PlaylistDao {
    @Query("SELECT p.*, COUNT(ps.songId) AS songCount FROM playlists p LEFT JOIN playlist_songs ps ON p.id=ps.playlistId GROUP BY p.id ORDER BY p.createdAt DESC")
    fun all(): Flow<List<PlaylistWithCount>>
    @Insert suspend fun create(playlist: Playlist): Long
    @Query("UPDATE playlists SET name=:name WHERE id=:id") suspend fun rename(id: Long, name: String)
    @Query("DELETE FROM playlists WHERE id=:id") suspend fun delete(id: Long)
    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id=ps.songId WHERE ps.playlistId=:playlistId AND (s.title LIKE '%' || :query || '%' OR s.artist LIKE '%' || :query || '%') ORDER BY ps.position, ps.addedAt")
    fun songs(playlistId: Long, query: String): Flow<List<Song>>
    @Query("SELECT s.* FROM songs s INNER JOIN playlist_songs ps ON s.id=ps.songId WHERE ps.playlistId=:playlistId ORDER BY ps.position, ps.addedAt")
    suspend fun songsNow(playlistId: Long): List<Song>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun add(entry: PlaylistSong)
    @Query("DELETE FROM playlist_songs WHERE playlistId=:playlistId AND songId=:songId") suspend fun remove(playlistId: Long, songId: Long)
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId=:playlistId") suspend fun nextPosition(playlistId: Long): Long
    @Transaction suspend fun addSong(playlistId: Long, songId: Long) { add(PlaylistSong(playlistId, songId, nextPosition(playlistId), System.currentTimeMillis())) }
}

@Database(entities = [Song::class, MoodCorrection::class, MoodProfile::class, Playlist::class, PlaylistSong::class, ListeningEvent::class, TasteProfile::class, MoodJourney::class, Recommendation::class, DjSession::class, AiProcessingLog::class, OnlineWishlist::class], version = 3, exportSchema = true)
abstract class MoodDatabase : RoomDatabase() {
    abstract fun songs(): SongDao
    abstract fun playlists(): PlaylistDao
    abstract fun intelligence(): IntelligenceDao

    companion object {
        @Volatile private var instance: MoodDatabase? = null
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN autoMood INTEGER NOT NULL DEFAULT 10")
                db.execSQL("ALTER TABLE songs ADD COLUMN albumId INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE songs ADD COLUMN featureData TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE songs ADD COLUMN analysisVersion INTEGER NOT NULL DEFAULT 2")
                db.execSQL("UPDATE songs SET analysisVersion=1")
                val mapping = "CASE mood WHEN 0 THEN 0 WHEN 1 THEN 1 WHEN 2 THEN 2 WHEN 3 THEN 7 WHEN 4 THEN 5 WHEN 5 THEN 4 WHEN 6 THEN 2 WHEN 7 THEN 2 WHEN 8 THEN 0 WHEN 9 THEN 7 WHEN 10 THEN 7 WHEN 11 THEN 2 ELSE 10 END"
                db.execSQL("UPDATE songs SET autoMood=$mapping, mood=$mapping")
                db.execSQL("CREATE TABLE IF NOT EXISTS mood_corrections (trackId INTEGER NOT NULL, audioUri TEXT NOT NULL, predictedMood INTEGER NOT NULL, correctedMood INTEGER NOT NULL, modelConfidence REAL NOT NULL, features TEXT NOT NULL, correctedAt INTEGER NOT NULL, PRIMARY KEY(trackId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mood_corrections_correctedMood ON mood_corrections(correctedMood)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mood_corrections_audioUri ON mood_corrections(audioUri)")
                db.execSQL("CREATE TABLE IF NOT EXISTS mood_profiles (mood INTEGER NOT NULL, count INTEGER NOT NULL, sums TEXT NOT NULL, PRIMARY KEY(mood))")
                db.execSQL("CREATE TABLE IF NOT EXISTS playlists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS playlist_songs (playlistId INTEGER NOT NULL, songId INTEGER NOT NULL, position INTEGER NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(playlistId, songId), FOREIGN KEY(playlistId) REFERENCES playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(songId) REFERENCES songs(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_songId ON playlist_songs(songId)")
                db.execSQL("INSERT INTO mood_corrections (trackId,audioUri,predictedMood,correctedMood,modelConfidence,features,correctedAt) SELECT id,uri,autoMood,mood,confidence,'',strftime('%s','now')*1000 FROM songs WHERE manual=1")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN feedback INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN analysisSource TEXT NOT NULL DEFAULT 'local'")
                db.execSQL("ALTER TABLE songs ADD COLUMN localMood INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE songs ADD COLUMN localConfidence REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN onlineExplanation TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE songs SET localMood=autoMood, localConfidence=confidence")
                db.execSQL("CREATE TABLE IF NOT EXISTS listening_events (eventId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, songId INTEGER NOT NULL, startedAt INTEGER NOT NULL, listenedMs INTEGER NOT NULL, trackDurationMs INTEGER NOT NULL, completionRatio REAL NOT NULL, skipped INTEGER NOT NULL, source TEXT NOT NULL, interaction TEXT NOT NULL DEFAULT 'none', FOREIGN KEY(songId) REFERENCES songs(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_events_startedAt ON listening_events(startedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_events_songId ON listening_events(songId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_events_startedAt_songId ON listening_events(startedAt,songId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS taste_profiles (profileId INTEGER NOT NULL, featureSums TEXT NOT NULL, moodSums TEXT NOT NULL, totalWeight REAL NOT NULL, eventCount INTEGER NOT NULL, version INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(profileId))")
                db.execSQL("CREATE TABLE IF NOT EXISTS mood_journeys (journeyId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, startingMood INTEGER NOT NULL, targetMood INTEGER NOT NULL, requestedMinutes INTEGER NOT NULL, energy REAL NOT NULL, familiarity REAL NOT NULL, songIds TEXT NOT NULL, generatedAt INTEGER NOT NULL, favorite INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS recommendations (recommendationId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, songId INTEGER NOT NULL, contextKey TEXT NOT NULL, reason TEXT NOT NULL, score REAL NOT NULL, feedback INTEGER NOT NULL, generatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recommendations_contextKey ON recommendations(contextKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recommendations_songId ON recommendations(songId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS dj_sessions (sessionId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, mode TEXT NOT NULL, startedAt INTEGER NOT NULL, listenedMs INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS ai_processing_logs (operationId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, provider TEXT NOT NULL, model TEXT NOT NULL, processingMode TEXT NOT NULL, operation TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, details TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_processing_logs_createdAt ON ai_processing_logs(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_processing_logs_status ON ai_processing_logs(status)")
                db.execSQL("CREATE TABLE IF NOT EXISTS online_wishlist (trackId TEXT NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, artwork TEXT NOT NULL, externalUrl TEXT NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(trackId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_online_wishlist_addedAt ON online_wishlist(addedAt)")
            }
        }

        fun get(context: Context): MoodDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MoodDatabase::class.java, "moods.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}
