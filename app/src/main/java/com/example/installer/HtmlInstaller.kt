package com.example.installer

import android.content.Context
import android.net.Uri
import com.example.data.AppRepository
import com.example.data.HtmlApp
import com.example.data.SourceType
import com.example.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Responsável por importar e instalar arquivos .html ou pacotes .zip locais.
 */
class HtmlInstaller(
    private val context: Context,
    private val repository: AppRepository
) {

    suspend fun installFromFile(
        uri: Uri,
        fileName: String,
        customName: String?
    ): Result<HtmlApp> = withContext(Dispatchers.IO) {
        try {
            val appId = "app_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(4)
            val appDir = repository.getAppDirectory(appId)
            if (!appDir.exists()) {
                appDir.mkdirs()
            }

            val isZip = fileName.endsWith(".zip", ignoreCase = true)
            val resolvedName = if (!customName.isNullOrBlank()) {
                customName.trim()
            } else {
                fileName.substringBeforeLast(".")
            }

            var entryFile = "index.html"
            val sourceType: SourceType

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Não foi possível ler o arquivo selecionado"))

            inputStream.use { stream ->
                if (isZip) {
                    sourceType = SourceType.ZIP
                    val extracted = FileUtils.unzip(stream, appDir)
                    if (!extracted) {
                        appDir.deleteRecursively()
                        return@withContext Result.failure(Exception("Falha ao descompactar pacote ZIP"))
                    }
                    entryFile = FileUtils.findEntryHtml(appDir)
                } else {
                    sourceType = SourceType.IMPORTED_FILE
                    val targetHtml = File(appDir, "index.html")
                    FileOutputStream(targetHtml).use { out ->
                        stream.copyTo(out)
                    }
                    entryFile = "index.html"
                }
            }

            // Verifica se o arquivo de entrada realmente existe
            val entryCheck = File(appDir, entryFile)
            if (!entryCheck.exists() || !entryCheck.isFile) {
                appDir.deleteRecursively()
                return@withContext Result.failure(Exception("Nenhum arquivo HTML válido encontrado no pacote"))
            }

            // Cria meta.json
            val metaJson = JSONObject().apply {
                put("id", appId)
                put("name", resolvedName)
                put("entryFile", entryFile)
                put("sourceType", sourceType.name)
                put("installedAt", System.currentTimeMillis())
                put("lastOpenedAt", System.currentTimeMillis())
            }
            File(appDir, "meta.json").writeText(metaJson.toString(2))

            val htmlApp = HtmlApp(
                id = appId,
                name = resolvedName,
                entryFile = entryFile,
                installedAt = System.currentTimeMillis(),
                lastOpenedAt = System.currentTimeMillis(),
                openCount = 0,
                sourceType = sourceType.name
            )

            repository.insertApp(htmlApp)
            Result.success(htmlApp)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Instala um app pré-empacotado nos assets locais (usado para seeding).
     */
    suspend fun installFromAssets(
        assetFolder: String,
        appId: String,
        appName: String,
        entryFile: String = "index.html"
    ): Result<HtmlApp> = withContext(Dispatchers.IO) {
        try {
            val appDir = repository.getAppDirectory(appId)
            if (!appDir.exists()) {
                appDir.mkdirs()
            }

            // Copia arquivos da pasta de assets
            val assetManager = context.assets
            copyAssetFolder(assetFolder, appDir)

            val metaJson = JSONObject().apply {
                put("id", appId)
                put("name", appName)
                put("entryFile", entryFile)
                put("sourceType", SourceType.IMPORTED_FILE.name)
                put("installedAt", System.currentTimeMillis())
            }
            File(appDir, "meta.json").writeText(metaJson.toString(2))

            val htmlApp = HtmlApp(
                id = appId,
                name = appName,
                entryFile = entryFile,
                installedAt = System.currentTimeMillis(),
                lastOpenedAt = System.currentTimeMillis(),
                openCount = 0,
                sourceType = SourceType.IMPORTED_FILE.name
            )
            repository.insertApp(htmlApp)
            Result.success(htmlApp)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun copyAssetFolder(assetPath: String, targetDir: File) {
        val assetManager = context.assets
        val list = assetManager.list(assetPath) ?: return
        if (list.isEmpty()) {
            // É um arquivo individual
            val targetFile = File(targetDir, File(assetPath).name)
            assetManager.open(assetPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            // É uma pasta
            for (subFile in list) {
                val subAssetPath = if (assetPath.isEmpty()) subFile else "$assetPath/$subFile"
                val subList = assetManager.list(subAssetPath)
                if (subList != null && subList.isNotEmpty()) {
                    val subDir = File(targetDir, subFile)
                    subDir.mkdirs()
                    copyAssetFolder(subAssetPath, subDir)
                } else {
                    val targetFile = File(targetDir, subFile)
                    targetFile.parentFile?.mkdirs()
                    assetManager.open(subAssetPath).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }
}
