package com.daniel.mood

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseTest {
    private lateinit var context: Context
    private lateinit var db: MoodDatabase
    private var name = "test-moods.db"

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        name = "test-${System.nanoTime()}.db"
        db = Room.databaseBuilder(context, MoodDatabase::class.java, name)
            .addMigrations(MoodDatabase.MIGRATION_1_2, MoodDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
    }
    @After fun tearDown() { db.close(); context.deleteDatabase(name) }

    private fun song(): Song {
        val f = Features(100f, .5f, .08f, .08f, 2200f, 3000f, .3f, .1f, .5f, .3f, 3f, .5f, .6f)
        return Song(42, "content://audio/42", "Title", "Artist", "Album", 200000, 1000, 1, 1,
            Moods.CALM, Moods.SAD, .62f, 100f, .08f, "", false, 1,
            Moods.CALM, 9, f.serialize(), 2)
    }

    @Test fun correctionLearningAndRestoreArePersistent() = runBlocking {
        val dao = db.songs()
        dao.put(song())
        dao.correct(42, Moods.ROMANTIC)
        assertTrue(dao.get(42)!!.manual)
        assertEquals(Moods.ROMANTIC, dao.get(42)!!.mood)
        assertEquals(Moods.CALM, dao.get(42)!!.autoMood)
        assertEquals(1, dao.profile(Moods.ROMANTIC)!!.count)

        dao.correct(42, Moods.RAINY)
        assertEquals(0, dao.profile(Moods.ROMANTIC)!!.count)
        assertEquals(1, dao.profile(Moods.RAINY)!!.count)

        db.close()
        db = Room.databaseBuilder(context, MoodDatabase::class.java, name)
            .addMigrations(MoodDatabase.MIGRATION_1_2, MoodDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        assertEquals(Moods.RAINY, db.songs().get(42)!!.mood)
        assertEquals(1, db.songs().profile(Moods.RAINY)!!.count)

        db.songs().restoreAutomatic(42)
        assertFalse(db.songs().get(42)!!.manual)
        assertEquals(Moods.CALM, db.songs().get(42)!!.mood)
        assertEquals(0, db.songs().profile(Moods.RAINY)!!.count)
        assertNull(db.songs().correction(42))
    }

    @Test fun playlistsSurviveSongUpdatesAndRemoveCascade() = runBlocking {
        val songs = db.songs(); val playlists = db.playlists()
        songs.put(song())
        val id = playlists.create(Playlist(name = "Persian", createdAt = 1))
        playlists.addSong(id, 42)
        playlists.addSong(id, 42)
        assertEquals(1, playlists.songsNow(id).size)
        songs.put(song().copy(title = "Updated")) // Upsert must not cascade-delete playlist membership.
        assertEquals("Updated", playlists.songsNow(id).single().title)
        playlists.remove(id, 42)
        assertTrue(playlists.songsNow(id).isEmpty())
        playlists.addSong(id, 42)
        playlists.delete(id)
        assertTrue(playlists.songsNow(id).isEmpty())
    }

    @Test fun migrationV1PreservesManualMoodAndMapsOldCategories() = runBlocking {
        db.close()
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(path, null)
        legacy.execSQL("CREATE TABLE songs (id INTEGER NOT NULL, uri TEXT NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, duration INTEGER NOT NULL, size INTEGER NOT NULL, modified INTEGER NOT NULL, added INTEGER NOT NULL, mood INTEGER NOT NULL, secondary INTEGER NOT NULL, confidence REAL NOT NULL, bpm REAL NOT NULL, energy REAL NOT NULL, reason TEXT NOT NULL, manual INTEGER NOT NULL, seen INTEGER NOT NULL, PRIMARY KEY(id))")
        legacy.execSQL("CREATE INDEX index_songs_mood ON songs(mood)")
        legacy.execSQL("CREATE INDEX index_songs_artist ON songs(artist)")
        legacy.execSQL("CREATE INDEX index_songs_title ON songs(title)")
        legacy.execSQL("CREATE INDEX index_songs_seen ON songs(seen)")
        legacy.execSQL("INSERT INTO songs VALUES (7,'content://audio/7','Old','Artist','',100000,1000,1,1,3,3,.7,130,.1,'',1,1)")
        legacy.version = 1
        legacy.close()

        db = Room.databaseBuilder(context, MoodDatabase::class.java, name)
            .addMigrations(MoodDatabase.MIGRATION_1_2, MoodDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        val migrated = db.songs().get(7)!!
        assertEquals(Moods.ENERGETIC, migrated.mood)
        assertEquals(Moods.ENERGETIC, migrated.autoMood)
        assertTrue(migrated.manual)
        assertEquals(1, migrated.analysisVersion)
        assertEquals(7, db.songs().correction(7)!!.trackId)
    }

    @Test fun migrationV2PreservesSongsCorrectionsPlaylistsAndAddsIntelligence() = runBlocking {
        db.close(); context.deleteDatabase(name)
        val path = context.getDatabasePath(name); path.parentFile?.mkdirs()
        val old = SQLiteDatabase.openOrCreateDatabase(path, null)
        old.execSQL("CREATE TABLE songs (id INTEGER NOT NULL, uri TEXT NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, duration INTEGER NOT NULL, size INTEGER NOT NULL, modified INTEGER NOT NULL, added INTEGER NOT NULL, mood INTEGER NOT NULL, secondary INTEGER NOT NULL, confidence REAL NOT NULL, bpm REAL NOT NULL, energy REAL NOT NULL, reason TEXT NOT NULL, manual INTEGER NOT NULL, seen INTEGER NOT NULL, autoMood INTEGER NOT NULL, analysisVersion INTEGER NOT NULL, features TEXT NOT NULL, schemaVersion INTEGER NOT NULL DEFAULT 2, PRIMARY KEY(id))")
        old.execSQL("CREATE TABLE mood_profile (mood INTEGER NOT NULL, n INTEGER NOT NULL, count INTEGER NOT NULL, sums TEXT NOT NULL, PRIMARY KEY(mood))")
        old.execSQL("CREATE TABLE corrections (trackId INTEGER NOT NULL, fromMood INTEGER NOT NULL, toMood INTEGER NOT NULL, features TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY(trackId))")
        old.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        old.execSQL("CREATE TABLE playlist_songs (playlistId INTEGER NOT NULL, songId INTEGER NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlistId,songId), FOREIGN KEY(playlistId) REFERENCES playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(songId) REFERENCES songs(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        old.execSQL("CREATE INDEX index_playlist_songs_songId ON playlist_songs(songId)")
        old.execSQL("INSERT INTO songs(id,uri,title,artist,album,duration,size,modified,added,mood,secondary,confidence,bpm,energy,reason,manual,seen,autoMood,analysisVersion,features,schemaVersion) VALUES(42,'content://42','Kept','Artist','',180000,1000,1,1,1,1,.8,100,.2,'',1,1,1,2,'',2)")
        old.execSQL("INSERT INTO corrections VALUES(42,0,1,'',1)")
        old.execSQL("INSERT INTO playlists(id,name,createdAt) VALUES(5,'Kept playlist',1)")
        old.execSQL("INSERT INTO playlist_songs VALUES(5,42,0)")
        old.version = 2; old.close()
        db = Room.databaseBuilder(context, MoodDatabase::class.java, name)
            .addMigrations(MoodDatabase.MIGRATION_1_2, MoodDatabase.MIGRATION_2_3).allowMainThreadQueries().build()
        assertEquals("Kept", db.songs().get(42)!!.title)
        assertTrue(db.songs().get(42)!!.manual)
        assertEquals("local", db.songs().get(42)!!.analysisSource)
        assertEquals(1, db.songs().correction(42)!!.toMood)
        assertEquals("Kept playlist", db.playlists().all().first().single().name)
        assertEquals(42L, db.playlists().songsNow(5).single().id)
        db.intelligence().addEvent(ListeningEvent(songId = 42, startedAt = 1, listenedMs = 1000, trackDurationMs = 180000, completionRatio = .1f, skipped = true))
        assertEquals(1, db.intelligence().recentEvents(10).size)
    }

}
