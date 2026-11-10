package com.daniel.mood

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * V2 schema. The V1 table is migrated in place: every existing song, its manual
 * correction and its cached analysis are preserved. Automatic prediction and
 * manual correction are stored in separate columns, so a rescan can never
 * silently overwrite a user's choice.
 */
@Entity(
    tableName = "songs",
    indices = [
        Index("mood"), Index("artist"), Index("title"), Index("seen"),
        Index(value = ["uri"], unique = true)
    ]
)
data class Song(
    @PrimaryKey val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(defaultValue = "0") val albumId: Long = 0L,
    val duration: Long,
    val size: Long,
    val modified: Long,
    val added: Long,
    /** Effective mood shown in the UI: manualMood when set, otherwise predictedMood. */
    val mood: Int,
    @ColumnInfo(defaultValue = "12") val predictedMood: Int = MoodCatalog.REVIEW,
    val manualMood: Int? = null,
    val secondary: Int,
    val confidence: Float,
    val bpm: Float,
    val energy: Float,
    val reason: String,
    /** Legacy V1 flag, kept for compatibility and reads. */
    val manual: Boolean,
    val seen: Long,
    /** Cached acoustic features; re-scoring needs no audio decode. */
    @ColumnInfo(defaultValue = "''") val featuresJson: String = "",
    @ColumnInfo(defaultValue = "0") val analysisVersion: Int = 0
)

data class MoodCount(val mood: Int, val count: Int)

/** One manual correction per song (with history metadata). Separate from predictions. */
@Entity(tableName = "mood_corrections", indices = [Index(value = ["songId"], unique = true)])
data class MoodCorrection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val uri: String,
    val predictedMood: Int,
    val correctedMood: Int,
    val modelConfidence: Float?,
    val correctedAt: Long,
    val featuresJson: String = ""
)

/** Centroid learned from corrections. Recomputed whenever corrections change. */
@Entity(tableName = "mood_prototypes")
data class MoodPrototype(
    @PrimaryKey val mood: Int,
    val centroidJson: String,
    val samples: Int,
    val updatedAt: Long
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("songId")]
)
data class PlaylistSong(
    val playlistId: Long,
    val songId: Long,
    val addedAt: Long
)

/** Persisted queue so playback state survives process recreation. */
@Entity(tableName = "queue_items")
data class QueueItem(
    @PrimaryKey val position: Int,
    val songId: Long,
    val uri: String,
    val title: String,
    val artist: String
)

@Entity(tableName = "queue_state")
data class QueueState(
    @PrimaryKey val id: Int = 0,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    val updatedAt: Long
)

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun get(id: Long): Song?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(song: Song)

    @Update
    suspend fun updateAll(songs: List<Song>)

    @Query("UPDATE songs SET seen = :seen WHERE id = :id")
    suspend fun touch(id: Long, seen: Long)

    @Query("DELETE FROM songs WHERE seen != :seen")
    suspend fun prune(seen: Long)

    @Query("SELECT mood, COUNT(*) AS count FROM songs GROUP BY mood")
    fun counts(): Flow<List<MoodCount>>

    @Query("SELECT COUNT(*) FROM songs")
    fun total(): Flow<Int>

    @Query(
        """SELECT * FROM songs
           WHERE (:mood = -1 OR mood = :mood)
             AND (title LIKE :query ESCAPE '!' OR artist LIKE :query ESCAPE '!' OR album LIKE :query ESCAPE '!')
           ORDER BY CASE WHEN :sort = 0 THEN title END COLLATE NOCASE,
                    CASE WHEN :sort = 1 THEN artist END COLLATE NOCASE,
                    CASE WHEN :sort = 2 THEN added END DESC,
                    CASE WHEN :sort = 3 THEN bpm END DESC,
                    CASE WHEN :sort = 4 THEN energy END DESC,
                    CASE WHEN :sort = 5 THEN duration END DESC,
                    id
           LIMIT :limit OFFSET :offset"""
    )
    fun page(mood: Int, query: String, sort: Int, limit: Int, offset: Int): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE (:mood = -1 OR mood = :mood) ORDER BY title COLLATE NOCASE")
    suspend fun playlist(mood: Int): List<Song>

    @Query("SELECT * FROM songs WHERE featuresJson != ''")
    suspend fun withFeatures(): List<Song>

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<Song>

    @Query("UPDATE songs SET mood = :mood, manualMood = :mood, manual = 1 WHERE id = :id")
    suspend fun correct(id: Long, mood: Int)

    @Query("UPDATE songs SET mood = predictedMood, manualMood = NULL, manual = 0 WHERE id = :id")
    suspend fun restoreAuto(id: Long)

    @Query("UPDATE songs SET modified = -1 WHERE id = :id")
    suspend fun invalidate(id: Long)

    @Query("UPDATE songs SET modified = -1")
    suspend fun invalidateAll()

    @Query("UPDATE songs SET analysisVersion = 0 WHERE featuresJson != ''")
    suspend fun markForRescore()

    @Query("UPDATE songs SET featuresJson = :json WHERE id = :id")
    suspend fun setFeatures(id: Long, json: String)

    @Query("SELECT COUNT(*) FROM songs WHERE analysisVersion != :version AND featuresJson != ''")
    suspend fun staleCount(version: Int): Int
}

@Dao
interface CorrectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: MoodCorrection)

    @Query("DELETE FROM mood_corrections WHERE songId = :songId")
    suspend fun deleteForSong(songId: Long)

    @Query("SELECT * FROM mood_corrections ORDER BY correctedAt DESC")
    suspend fun all(): List<MoodCorrection>

    @Query("SELECT * FROM mood_corrections WHERE songId = :songId")
    suspend fun forSong(songId: Long): MoodCorrection?

    @Query("SELECT COUNT(*) FROM mood_corrections")
    fun count(): Flow<Int>
}

@Dao
interface PrototypeDao {
    @Query("SELECT * FROM mood_prototypes")
    suspend fun all(): List<MoodPrototype>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MoodPrototype>)

    @Query("DELETE FROM mood_prototypes")
    suspend fun clear()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun all(): Flow<List<Playlist>>

    @Insert
    suspend fun create(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun clearSongs(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(entry: PlaylistSong)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun remove(playlistId: Long, songId: Long)

    @Query(
        """SELECT songs.* FROM songs
           INNER JOIN playlist_songs ON songs.id = playlist_songs.songId
           WHERE playlist_songs.playlistId = :playlistId
           ORDER BY playlist_songs.addedAt DESC"""
    )
    fun songs(playlistId: Long): Flow<List<Song>>

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :id")
    fun count(id: Long): Flow<Int>

    @Query("SELECT playlistId FROM playlist_songs WHERE songId = :songId")
    suspend fun playlistsForSong(songId: Long): List<Long>
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items ORDER BY position")
    suspend fun items(): List<QueueItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QueueItem>)

    @Query("DELETE FROM queue_items")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: QueueState)

    @Query("SELECT * FROM queue_state WHERE id = 0")
    suspend fun state(): QueueState?
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // --- extend the V1 songs table without dropping a single row ---------
        db.execSQL("ALTER TABLE songs ADD COLUMN albumId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE songs ADD COLUMN predictedMood INTEGER NOT NULL DEFAULT 12")
        db.execSQL("UPDATE songs SET predictedMood = mood")
        db.execSQL("ALTER TABLE songs ADD COLUMN manualMood INTEGER")
        db.execSQL("UPDATE songs SET manualMood = mood WHERE manual = 1")
        db.execSQL("ALTER TABLE songs ADD COLUMN featuresJson TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE songs ADD COLUMN analysisVersion INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_songs_uri ON songs(uri)")

        // --- new V2 tables --------------------------------------------------
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS mood_corrections (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                songId INTEGER NOT NULL,
                uri TEXT NOT NULL,
                predictedMood INTEGER NOT NULL,
                correctedMood INTEGER NOT NULL,
                modelConfidence REAL,
                correctedAt INTEGER NOT NULL,
                featuresJson TEXT NOT NULL
            )"""
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_mood_corrections_songId ON mood_corrections(songId)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS mood_prototypes (
                mood INTEGER PRIMARY KEY NOT NULL,
                centroidJson TEXT NOT NULL,
                samples INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS playlists (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS playlist_songs (
                playlistId INTEGER NOT NULL,
                songId INTEGER NOT NULL,
                addedAt INTEGER NOT NULL,
                PRIMARY KEY(playlistId, songId)
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_songId ON playlist_songs(songId)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS queue_items (
                position INTEGER PRIMARY KEY NOT NULL,
                songId INTEGER NOT NULL,
                uri TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS queue_state (
                id INTEGER PRIMARY KEY NOT NULL,
                currentIndex INTEGER NOT NULL,
                positionMs INTEGER NOT NULL,
                shuffle INTEGER NOT NULL,
                repeatMode INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )"""
        )
    }
}

@Database(
    entities = [
        Song::class, MoodCorrection::class, MoodPrototype::class,
        Playlist::class, PlaylistSong::class, QueueItem::class, QueueState::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MoodDatabase : RoomDatabase() {
    abstract fun songs(): SongDao
    abstract fun corrections(): CorrectionDao
    abstract fun prototypes(): PrototypeDao
    abstract fun playlists(): PlaylistDao
    abstract fun queue(): QueueDao

    companion object {
        @Volatile
        private var instance: MoodDatabase? = null

        fun get(context: Context): MoodDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MoodDatabase::class.java,
                "moods.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
