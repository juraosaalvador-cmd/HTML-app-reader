package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Interface DAO para persistência das aplicações HTML via Room.
 */
@Dao
interface HtmlAppDao {

    @Query("SELECT * FROM html_apps ORDER BY lastOpenedAt DESC")
    fun getAllAppsOrderedByLastOpened(): Flow<List<HtmlApp>>

    @Query("SELECT * FROM html_apps WHERE id = :id LIMIT 1")
    suspend fun getAppById(id: String): HtmlApp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: HtmlApp)

    @Update
    suspend fun updateApp(app: HtmlApp)

    @Query("UPDATE html_apps SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun updateLastOpened(id: String, timestamp: Long)

    @Query("DELETE FROM html_apps WHERE id = :id")
    suspend fun deleteApp(id: String)

    @Query("UPDATE html_apps SET openCount = openCount + 1, lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun incrementOpenCount(id: String, timestamp: Long = System.currentTimeMillis())
}
