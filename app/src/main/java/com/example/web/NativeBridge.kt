package com.example.web

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Ponte nativa bidirecional exposta aos scripts JavaScript sob o namespace "AndroidNative".
 */
class NativeBridge(
    private val context: Context,
    private val onExitRequested: () -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    var currentAppDirectory: File? = null

    /**
     * Salva arquivo no armazenamento do app atual ou no armazenamento privado geral.
     */
    @JavascriptInterface
    fun saveFile(fileName: String, content: String): Boolean {
        return try {
            val baseDir = currentAppDirectory ?: context.filesDir
            val safeFileName = File(fileName).name // previne directory traversal
            val targetFile = File(baseDir, safeFileName)
            targetFile.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Lê o conteúdo de um arquivo do armazenamento privado.
     */
    @JavascriptInterface
    fun readFile(fileName: String): String {
        return try {
            val baseDir = currentAppDirectory ?: context.filesDir
            val safeFileName = File(fileName).name
            val targetFile = File(baseDir, safeFileName)
            if (targetFile.exists() && targetFile.isFile) {
                targetFile.readText(Charsets.UTF_8)
            } else {
                ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Retorna uma lista em formato JSON dos arquivos do diretório atual.
     */
    @JavascriptInterface
    fun listFiles(): String {
        return try {
            val baseDir = currentAppDirectory ?: context.filesDir
            val jsonArray = JSONArray()
            baseDir.listFiles()?.forEach { file ->
                val obj = JSONObject().apply {
                    put("name", file.name)
                    put("size", file.length())
                    put("isDirectory", file.isDirectory)
                    put("lastModified", file.lastModified())
                }
                jsonArray.put(obj)
            }
            jsonArray.toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    /**
     * Retorna telemetria sobre o modelo, versão do Android e versão do app.
     */
    @JavascriptInterface
    fun getDeviceInfo(): String {
        return try {
            val pInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) {
                null
            }

            val json = JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("device", Build.DEVICE)
                put("osVersion", Build.VERSION.RELEASE)
                put("apiLevel", Build.VERSION.SDK_INT)
                put("appVersion", pInfo?.versionName ?: "1.0.0")
                put("packageName", context.packageName)
            }
            json.toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    /**
     * Exibe um Toast nativo para o usuário a partir do código JavaScript.
     */
    @JavascriptInterface
    fun notify(message: String) {
        mainHandler.post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Solicita o encerramento do app HTML atual, retornando à tela inicial/terminal.
     */
    @JavascriptInterface
    fun exitApp() {
        mainHandler.post {
            onExitRequested()
        }
    }
}
