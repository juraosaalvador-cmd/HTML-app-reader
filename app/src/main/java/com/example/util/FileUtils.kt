package com.example.util

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

object FileUtils {

    /**
     * Descompacta um fluxo ZIP para o diretório de destino.
     */
    fun unzip(zipInputStream: InputStream, targetDir: File): Boolean {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val zis = ZipInputStream(zipInputStream)
        var entry = zis.nextEntry

        while (entry != null) {
            val file = File(targetDir, entry.name)

            // Prevenção contra Zip Slip vulnerability
            val canonicalPath = file.canonicalPath
            val targetCanonicalPath = targetDir.canonicalPath
            if (!canonicalPath.startsWith(targetCanonicalPath)) {
                zis.closeEntry()
                entry = zis.nextEntry
                continue
            }

            if (entry.isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { out ->
                    zis.copyTo(out)
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()
        return true
    }

    /**
     * Localiza o arquivo de entrada principal (.html) em um diretório.
     * Prioriza index.html, index.htm ou o primeiro arquivo .html encontrado.
     */
    fun findEntryHtml(dir: File): String {
        val rootIndex = File(dir, "index.html")
        if (rootIndex.exists() && rootIndex.isFile) return "index.html"

        val rootIndexHtm = File(dir, "index.htm")
        if (rootIndexHtm.exists() && rootIndexHtm.isFile) return "index.htm"

        // Procura no primeiro nível
        val htmlInRoot = dir.listFiles { f -> f.isFile && (f.name.endsWith(".html", true) || f.name.endsWith(".htm", true)) }
        if (!htmlInRoot.isNullOrEmpty()) {
            return htmlInRoot[0].name
        }

        // Procura recursivamente até 2 níveis
        val allHtmls = dir.walkTopDown().maxDepth(3).filter { it.isFile && (it.name.endsWith(".html", true) || it.name.endsWith(".htm", true)) }.toList()
        if (allHtmls.isNotEmpty()) {
            return allHtmls[0].relativeTo(dir).path
        }

        return "index.html"
    }

    /**
     * Conta arquivos e tamanho total em bytes de uma pasta.
     */
    fun countFilesAndSize(dir: File): Pair<Int, Long> {
        if (!dir.exists()) return Pair(0, 0L)
        var count = 0
        var totalBytes = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                count++
                totalBytes += file.length()
            }
        }
        return Pair(count, totalBytes)
    }

    /**
     * Formata tamanho em bytes para KB/MB legível.
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Formata tempo relativo em português ("hoje", "ontem", "há X dias").
     */
    fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0L) return "nunca"
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val oneMinute = 60 * 1000L
        val oneHour = 60 * oneMinute
        val oneDay = 24 * oneHour

        return when {
            diff < 5 * oneMinute -> "agora há pouco"
            diff < oneHour -> "há ${diff / oneMinute} min"
            diff < oneDay -> "há ${diff / oneHour}h"
            diff < 2 * oneDay -> "ontem"
            diff < 30 * oneDay -> "há ${diff / oneDay} dias"
            else -> {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
}
