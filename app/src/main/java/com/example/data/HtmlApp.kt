package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Representa uma aplicação HTML instalada localmente no dispositivo.
 */
@Entity(tableName = "html_apps")
data class HtmlApp(
    @PrimaryKey
    val id: String,
    val name: String,
    val iconPath: String? = null,
    val entryFile: String = "index.html",
    val installedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val openCount: Int = 0,
    val sourceType: String = SourceType.IMPORTED_FILE.name,
    val sourceUrl: String? = null
)

enum class SourceType {
    IMPORTED_FILE,
    ZIP,
    URL
}
