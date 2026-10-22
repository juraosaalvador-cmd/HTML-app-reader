package com.example.web

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * WebViewClient especializado com interceptação de URLs, erros e recuperação de render process gone.
 */
class HtmlWebViewClient(
    private val onPageStartedCallback: (String?) -> Unit,
    private val onPageFinishedCallback: (String?) -> Unit,
    private val onReceivedErrorCallback: (String) -> Unit,
    private val onRenderProcessGoneCallback: () -> Unit
) : WebViewClient() {

    companion object {
        private const val TAG = "HtmlWebViewClient"
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        Log.d(TAG, "Navegação solicitada: $url")

        // Permite carregar recursos file:// e http/https locais ou remotos na própria WebView
        if (url.startsWith("file://") || url.startsWith("content://") || url.startsWith("http://") || url.startsWith("https://")) {
            return false // Deixa a WebView carregar normalmente
        }

        // Outros esquemas (mailto, tel, etc)
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d(TAG, "Página iniciada: $url")
        onPageStartedCallback(url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        Log.d(TAG, "Página carregada com sucesso: $url")
        onPageFinishedCallback(url)
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                error?.description?.toString() ?: "Erro desconhecido"
            } else {
                "Erro de carregamento"
            }
            Log.e(TAG, "Erro de carregamento da página principal: $description")
            onReceivedErrorCallback(description)
        }
    }

    /**
     * Recuperação automática de crash do processo de renderização da WebView.
     */
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        val didCrash = detail?.didCrash() == true
        Log.e(TAG, "O processo de renderização da WebView caiu! Crash=$didCrash. Recriando WebView...")
        onRenderProcessGoneCallback()
        return true // Trata o crash graciosamente
    }
}
