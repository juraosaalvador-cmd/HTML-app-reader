package com.example.installer

import com.example.data.AppRepository
import com.example.data.HtmlApp
import com.example.data.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Responsável por baixar uma aplicação HTML a partir de uma URL e armazená-la localmente.
 */
class UrlInstaller(
    private val repository: AppRepository
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun downloadAndInstall(
        url: String,
        customName: String?,
        onProgress: (String) -> Unit
    ): Result<HtmlApp> = withContext(Dispatchers.IO) {
        try {
            val formattedUrl = if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                "https://$url"
            } else {
                url
            }

            onProgress("Conectando ao servidor remoto...")
            val request = Request.Builder()
                .url(formattedUrl)
                .header("User-Agent", "HTML-App-Reader/1.0 (Android; Hacker-Client)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Error: ${response.code} ${response.message}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Resposta vazia recebida do servidor"))

            val appId = "app_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(4)
            val appDir = repository.getAppDirectory(appId)
            if (!appDir.exists()) {
                appDir.mkdirs()
            }

            onProgress("Gravando página HTML principal...")
            val indexFile = File(appDir, "index.html")
            FileOutputStream(indexFile).use { out ->
                body.byteStream().copyTo(out)
            }

            // Cria pasta de assets local vazia para recursos adicionais
            File(appDir, "assets").mkdirs()

            val resolvedName = if (!customName.isNullOrBlank()) {
                customName.trim()
            } else {
                try {
                    val host = java.net.URI(formattedUrl).host ?: "web_app"
                    host.removePrefix("www.")
                } catch (e: Exception) {
                    "Web App"
                }
            }

            // Grava meta.json
            val metaJson = JSONObject().apply {
                put("id", appId)
                put("name", resolvedName)
                put("entryFile", "index.html")
                put("sourceType", SourceType.URL.name)
                put("sourceUrl", formattedUrl)
                put("installedAt", System.currentTimeMillis())
                put("lastOpenedAt", System.currentTimeMillis())
            }
            File(appDir, "meta.json").writeText(metaJson.toString(2))

            val htmlApp = HtmlApp(
                id = appId,
                name = resolvedName,
                entryFile = "index.html",
                installedAt = System.currentTimeMillis(),
                lastOpenedAt = System.currentTimeMillis(),
                openCount = 0,
                sourceType = SourceType.URL.name,
                sourceUrl = formattedUrl
            )

            repository.insertApp(htmlApp)
            onProgress("Instalação concluída com sucesso.")
            Result.success(htmlApp)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
