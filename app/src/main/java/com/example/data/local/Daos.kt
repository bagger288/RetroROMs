package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsoleDao {
    @Query("SELECT * FROM consoles ORDER BY sortOrder ASC")
    fun getAllConsoles(): Flow<List<ConsoleEntity>>

    @Query("SELECT * FROM consoles WHERE isEnabled = 1 ORDER BY sortOrder ASC")
    fun getEnabledConsoles(): Flow<List<ConsoleEntity>>

    @Query("SELECT * FROM consoles WHERE slug = :slug LIMIT 1")
    suspend fun getConsoleBySlug(slug: String): ConsoleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsoles(consoles: List<ConsoleEntity>)

    @Update
    suspend fun updateConsole(console: ConsoleEntity)

    @Query("UPDATE consoles SET isEnabled = :isEnabled WHERE slug = :slug")
    suspend fun setConsoleEnabled(slug: String, isEnabled: Boolean)

    @Query("UPDATE consoles SET isEnabled = :isEnabled")
    suspend fun setAllConsolesEnabled(isEnabled: Boolean)

    @Query("SELECT COUNT(*) FROM consoles")
    suspend fun getConsoleCount(): Int

    @Query("SELECT * FROM consoles")
    suspend fun getAllConsolesList(): List<ConsoleEntity>

    @Query("DELETE FROM consoles WHERE slug NOT IN (:validSlugs)")
    suspend fun retainOnlyConsoles(validSlugs: List<String>)
}

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE consoleSlug = :consoleSlug ORDER BY title ASC")
    fun getGamesForConsole(consoleSlug: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE title LIKE '%' || :query || '%' OR genre LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchGames(query: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE title LIKE '%' || :query || '%' OR genre LIKE '%' || :query || '%' ORDER BY title ASC")
    suspend fun searchGamesDirect(query: String): List<GameEntity>

    @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
    fun getGameById(id: String): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
    suspend fun getGameByIdDirect(id: String): GameEntity?

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteGames(): Flow<List<GameEntity>>

    @Query("SELECT id FROM games WHERE isFavorite = 1")
    suspend fun getFavoriteGameIds(): List<String>

    @Query("SELECT id FROM games WHERE isDownloaded = 1")
    suspend fun getDownloadedGameIds(): List<String>

    @Query("DELETE FROM games WHERE consoleSlug NOT IN (:validSlugs)")
    suspend fun deleteGamesForInvalidConsoles(validSlugs: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGames(games: List<GameEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: GameEntity)

    @Query("UPDATE games SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE games SET isDownloaded = :isDownloaded WHERE id = :id")
    suspend fun setDownloaded(id: String, isDownloaded: Boolean)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY timestamp DESC")
    fun getDownloadsByStatus(status: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE downloadManagerId = :dmId LIMIT 1")
    suspend fun getDownloadByManagerId(dmId: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE gameId = :gameId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getDownloadByGameId(gameId: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity): Long

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, downloadedBytes: Long, totalBytes: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: Long)
}
