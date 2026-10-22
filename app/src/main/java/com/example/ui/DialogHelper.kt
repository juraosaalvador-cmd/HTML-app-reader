package com.example.ui

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.example.R
import com.example.data.HtmlApp
import com.example.databinding.DialogAppDetailsBinding
import com.example.databinding.DialogImportBinding
import com.example.databinding.DialogRenameBinding
import com.example.util.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utilitários para criação e controle de diálogos no estilo terminal do app.
 */
object DialogHelper {

    /**
     * Diálogo principal de importação de arquivo local ou download de URL.
     */
    fun showImportDialog(
        activity: Activity,
        onChooseFileClick: () -> Unit,
        onConfirmFileImport: (customName: String?) -> Unit,
        onConfirmUrlImport: (url: String, customName: String?) -> Unit
    ): ImportDialogController {
        val binding = DialogImportBinding.inflate(LayoutInflater.from(activity))
        val dialog = AlertDialog.Builder(activity, R.style.TerminalDialog)
            .setView(binding.root)
            .setCancelable(true)
            .create()

        var isFileTab = true

        binding.btnTabFile.setOnClickListener {
            isFileTab = true
            binding.btnTabFile.setBackgroundResource(R.drawable.bg_terminal_button_primary)
            binding.btnTabFile.setTextColor(activity.getColor(R.color.black))
            binding.btnTabUrl.setBackgroundResource(R.drawable.bg_terminal_button)
            binding.btnTabUrl.setTextColor(activity.getColor(R.color.term_text_primary))
            binding.layoutFileSection.visibility = View.VISIBLE
            binding.layoutUrlSection.visibility = View.GONE
        }

        binding.btnTabUrl.setOnClickListener {
            isFileTab = false
            binding.btnTabUrl.setBackgroundResource(R.drawable.bg_terminal_button_primary)
            binding.btnTabUrl.setTextColor(activity.getColor(R.color.black))
            binding.btnTabFile.setBackgroundResource(R.drawable.bg_terminal_button)
            binding.btnTabFile.setTextColor(activity.getColor(R.color.term_text_primary))
            binding.layoutFileSection.visibility = View.GONE
            binding.layoutUrlSection.visibility = View.VISIBLE
        }

        binding.btnChooseFile.setOnClickListener {
            onChooseFileClick()
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnExecuteImport.setOnClickListener {
            if (isFileTab) {
                val customName = binding.etFileName.text?.toString()?.trim()
                onConfirmFileImport(customName)
            } else {
                val url = binding.etUrlInput.text?.toString()?.trim() ?: ""
                val customName = binding.etUrlAppName.text?.toString()?.trim()
                if (url.isBlank()) {
                    Toast.makeText(activity, "Por favor insira uma URL válida", Toast.LENGTH_SHORT).show()
                } else {
                    onConfirmUrlImport(url, customName)
                }
            }
        }

        dialog.show()

        return ImportDialogController(dialog, binding)
    }

    class ImportDialogController(
        val dialog: AlertDialog,
        private val binding: DialogImportBinding
    ) {
        fun setSelectedFile(name: String) {
            binding.tvSelectedFile.text = "> Arquivo: $name"
            binding.tvSelectedFile.setTextColor(binding.root.context.getColor(R.color.term_green))
        }

        fun showProgress(message: String) {
            binding.tvImportStatus.visibility = View.VISIBLE
            binding.tvImportStatus.text = message
            binding.pbImportProgress.visibility = View.VISIBLE
            binding.btnExecuteImport.isEnabled = false
        }

        fun dismiss() {
            dialog.dismiss()
        }
    }

    /**
     * Diálogo para renomear uma aplicação.
     */
    fun showRenameDialog(
        activity: Activity,
        currentName: String,
        onConfirm: (newName: String) -> Unit
    ) {
        val binding = DialogRenameBinding.inflate(LayoutInflater.from(activity))
        binding.etRenameInput.setText(currentName)
        binding.etRenameInput.setSelection(currentName.length)

        val dialog = AlertDialog.Builder(activity, R.style.TerminalDialog)
            .setView(binding.root)
            .setCancelable(true)
            .create()

        binding.btnCancelRename.setOnClickListener { dialog.dismiss() }
        binding.btnConfirmRename.setOnClickListener {
            val newName = binding.etRenameInput.text?.toString()?.trim()
            if (!newName.isNullOrBlank()) {
                onConfirm(newName)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    /**
     * Diálogo de detalhes e propriedades do pacote.
     */
    fun showAppDetailsDialog(
        activity: Activity,
        app: HtmlApp,
        appDir: File
    ) {
        val binding = DialogAppDetailsBinding.inflate(LayoutInflater.from(activity))
        val dialog = AlertDialog.Builder(activity, R.style.TerminalDialog)
            .setView(binding.root)
            .setCancelable(true)
            .create()

        val (fileCount, totalSize) = FileUtils.countFilesAndSize(appDir)
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        val detailsBuilder = StringBuilder().apply {
            append("NOME: ").append(app.name).append("\n")
            append("ID: ").append(app.id).append("\n")
            append("ENTRADA: ").append(app.entryFile).append("\n")
            append("FONTE: ").append(app.sourceType).append("\n")
            if (!app.sourceUrl.isNullOrBlank()) {
                append("URL ORIGEM: ").append(app.sourceUrl).append("\n")
            }
            append("INSTALADO EM: ").append(sdf.format(Date(app.installedAt))).append("\n")
            append("ÚLTIMA EXECUÇÃO: ").append(sdf.format(Date(app.lastOpenedAt))).append("\n")
            append("EXECUÇÕES: ").append(app.openCount).append("\n")
            append("DIRETÓRIO: ").append(appDir.absolutePath).append("\n")
            append("ARQUIVOS TOTAIS: ").append(fileCount).append("\n")
            append("TAMANHO EM DISCO: ").append(FileUtils.formatFileSize(totalSize)).append("\n\n")
            append("> LISTA DE ARQUIVOS NA RAIZ:\n")
            appDir.listFiles()?.take(15)?.forEach { f ->
                val type = if (f.isDirectory) "[DIR]" else "[FILE]"
                append(" - ").append(type).append(" ").append(f.name).append(" (").append(FileUtils.formatFileSize(f.length())).append(")\n")
            }
        }

        binding.tvDetailsContent.text = detailsBuilder.toString()
        binding.btnDetailsClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    /**
     * Diálogo minimalista de confirmação.
     */
    fun showConfirmationDialog(
        activity: Activity,
        title: String,
        message: String,
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(activity, R.style.TerminalDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("CONFIRMAR") { _, _ -> onConfirm() }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    /**
     * Diálogo sobre o app e recursos.
     */
    fun showAboutDialog(activity: Activity) {
        val message = """
            HTML APP READER
            Versão: 1.0.0-terminal (Build 2026)
            
            Leitor dedicado de aplicações web e HTML locais.
            
            RECURSOS NATIVOS:
            - Aceleração por Hardware (WebGL 3D)
            - DOMStorage & IndexedDB habilitados
            - Ponte JavascriptInterface "AndroidNative":
              * saveFile, readFile, listFiles
              * getDeviceInfo, notify, exitApp
            - Modo Imersivo com Transient Bars
            - Downloads nativos para armazenamento
            - Recuperação automática de falhas WebKit
            
            Desenvolvido com estética monocromática terminal.
        """.trimIndent()

        AlertDialog.Builder(activity, R.style.TerminalDialog)
            .setTitle("// SOBRE O SISTEMA")
            .setMessage(message)
            .setPositiveButton("FECHAR", null)
            .show()
    }
}
