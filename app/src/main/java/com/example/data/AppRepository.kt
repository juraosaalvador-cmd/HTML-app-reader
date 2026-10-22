package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repositório central de dados e arquivos das aplicações instaladas.
 */
class AppRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.htmlAppDao()

    val appsFlow: Flow<List<HtmlApp>> = dao.getAllAppsOrderedByLastOpened()

    suspend fun getAppById(id: String): HtmlApp? = withContext(Dispatchers.IO) {
        dao.getAppById(id)
    }

    suspend fun insertApp(app: HtmlApp) = withContext(Dispatchers.IO) {
        dao.insertApp(app)
    }

    suspend fun updateApp(app: HtmlApp) = withContext(Dispatchers.IO) {
        dao.updateApp(app)
    }

    suspend fun renameApp(id: String, newName: String) = withContext(Dispatchers.IO) {
        val app = dao.getAppById(id)
        if (app != null) {
            dao.updateApp(app.copy(name = newName))
        }
    }

    suspend fun recordAppOpened(id: String) = withContext(Dispatchers.IO) {
        dao.incrementOpenCount(id, System.currentTimeMillis())
    }

    /**
     * Remove o app do banco de dados e apaga sua pasta física correspondente.
     */
    suspend fun deleteApp(id: String) = withContext(Dispatchers.IO) {
        dao.deleteApp(id)
        val appDir = getAppDirectory(id)
        if (appDir.exists()) {
            appDir.deleteRecursively()
        }
    }

    fun getAppDirectory(id: String): File {
        val appsDir = File(context.filesDir, "apps")
        return File(appsDir, id)
    }
}
