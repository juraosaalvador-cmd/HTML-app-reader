package com.example.web

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText
import com.example.R

/**
 * WebChromeClient com suporte a JavaScript alerts, fullscreen HTML5, console e permissões.
 */
class HtmlWebChromeClient(
    private val context: Context,
    private val onProgress: (Int) -> Unit,
    private val onShowCustomViewCallback: (View, CustomViewCallback) -> Unit,
    private val onHideCustomViewCallback: () -> Unit,
    private val onPermissionRequestCallback: (PermissionRequest) -> Unit
) : WebChromeClient() {

    companion object {
        private const val TAG = "HtmlWebChromeClient"
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgress(newProgress)
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        if (consoleMessage != null) {
            Log.d(
                TAG,
                "[CONSOLE] [${consoleMessage.messageLevel()}] ${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
            )
        }
        return true
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        AlertDialog.Builder(context, R.style.TerminalDialog)
            .setTitle("// ALERTA JAVASCRIPT")
            .setMessage(message ?: "")
            .setPositiveButton("OK") { _, _ -> result?.confirm() }
            .setOnCancelListener { result?.cancel() }
            .show()
        return true
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        AlertDialog.Builder(context, R.style.TerminalDialog)
            .setTitle("// CONFIRMAÇÃO")
            .setMessage(message ?: "")
            .setPositiveButton("CONFIRMAR") { _, _ -> result?.confirm() }
            .setNegativeButton("CANCELAR") { _, _ -> result?.cancel() }
            .setOnCancelListener { result?.cancel() }
            .show()
        return true
    }

    override fun onJsPrompt(
        view: WebView?,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResult?
    ): Boolean {
        val input = EditText(context).apply {
            setText(defaultValue ?: "")
            setTextColor(context.getColor(R.color.term_green))
            setBackgroundResource(R.drawable.bg_terminal_card)
            setPadding(24, 24, 24, 24)
        }

        AlertDialog.Builder(context, R.style.TerminalDialog)
            .setTitle("// ENTRADA DE DADOS")
            .setMessage(message ?: "")
            .setView(input)
            .setPositiveButton("ENVIAR") { _, _ ->
                result?.confirm(input.text.toString())
            }
            .setNegativeButton("CANCELAR") { _, _ ->
                result?.cancel()
            }
            .setOnCancelListener { result?.cancel() }
            .show()
        return true
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view != null && callback != null) {
            onShowCustomViewCallback(view, callback)
        }
    }

    override fun onHideCustomView() {
        onHideCustomViewCallback()
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        if (request != null) {
            onPermissionRequestCallback(request)
        }
    }
}
