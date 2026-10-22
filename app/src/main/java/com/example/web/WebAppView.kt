package com.example.web

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.HtmlApp
import java.io.File

/**
 * Visualizador completo e robusto de aplicações HTML locais baseado no WebView do Android.
 */
@SuppressLint("SetJavaScriptEnabled")
class WebAppView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var webView: WebView? = null
        private set

    private var customViewContainer: FrameLayout? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var progressBar: ProgressBar? = null

    private var nativeBridge: NativeBridge? = null
    var onExitRequested: (() -> Unit)? = null
    var onMediaPermissionRequest: ((PermissionRequest) -> Unit)? = null

    var currentApp: HtmlApp? = null
        private set

    init {
        setBackgroundColor(ContextCompat.getColor(context, R.color.term_background))
        setupContainers()
        initWebView()
    }

    private fun setupContainers() {
        removeAllViews()

        // Container para tela cheia de vídeo / elementos HTML5
        customViewContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            setBackgroundColor(ContextCompat.getColor(context, R.color.term_background))
        }
        addView(customViewContainer)

        // Barra de progresso terminal (verde fino no topo)
        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                8 // altura fina
            ).apply {
                gravity = Gravity.TOP
            }
            isIndeterminate = false
            max = 100
            progressDrawable = ContextCompat.getDrawable(context, R.drawable.bg_terminal_button_primary)
            visibility = View.GONE
        }
        addView(progressBar)
    }

    @Suppress("DEPRECATION")
    private fun initWebView() {
        val wv = WebView(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ContextCompat.getColor(context, R.color.term_background))
            isFocusable = true
            isFocusableInTouchMode = true
        }

        // Configuração estrita de WebSettings conforme especificação
        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.defaultTextEncodingName = "UTF-8"

        // Cookies
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        // Ponte nativa JavaScript
        val bridge = NativeBridge(context) {
            onExitRequested?.invoke()
        }
        nativeBridge = bridge
        wv.addJavascriptInterface(bridge, "AndroidNative")

        // DownloadListener
        wv.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            handleDownload(url, contentDisposition, mimetype)
        })

        // WebChromeClient
        wv.webChromeClient = HtmlWebChromeClient(
            context = context,
            onProgress = { progress ->
                if (progress < 100) {
                    progressBar?.visibility = View.VISIBLE
                    progressBar?.progress = progress
                } else {
                    progressBar?.visibility = View.GONE
                }
            },
            onShowCustomViewCallback = { view, callback ->
                showCustomView(view, callback)
            },
            onHideCustomViewCallback = {
                hideCustomView()
            },
            onPermissionRequestCallback = { request ->
                onMediaPermissionRequest?.invoke(request)
            }
        )

        // WebViewClient
        wv.webViewClient = HtmlWebViewClient(
            onPageStartedCallback = {
                progressBar?.visibility = View.VISIBLE
            },
            onPageFinishedCallback = {
                progressBar?.visibility = View.GONE
            },
            onReceivedErrorCallback = { errorMsg ->
                progressBar?.visibility = View.GONE
                Toast.makeText(context, "Erro HTML: $errorMsg", Toast.LENGTH_SHORT).show()
            },
            onRenderProcessGoneCallback = {
                recreateWebView()
            }
        )

        // Adiciona à hierarquia logo abaixo da barra de progresso
        addView(wv, 0)
        webView = wv
    }

    private fun handleDownload(url: String, contentDisposition: String, mimetype: String) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimetype)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setTitle(fileName)
                setDescription("Baixando arquivo da aplicação HTML...")
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "Iniciando download: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Falha no download: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Recria a WebView em caso de crash do processo de renderização.
     */
    fun recreateWebView() {
        val oldUrl = webView?.url
        destroyWebView()
        initWebView()
        if (!oldUrl.isNullOrEmpty()) {
            webView?.loadUrl(oldUrl)
        } else {
            loadPlaceholder()
        }
    }

    /**
     * Carrega a página inicial de terminal/placeholder.
     */
    fun loadPlaceholder() {
        currentApp = null
        nativeBridge?.currentAppDirectory = null
        webView?.loadUrl("file:///android_asset/placeholder.html")
    }

    /**
     * Carrega uma aplicação HTML instalada localmente no dispositivo.
     */
    fun loadApp(app: HtmlApp, appDir: File) {
        currentApp = app
        nativeBridge?.currentAppDirectory = appDir

        val entryFile = File(appDir, app.entryFile)
        val targetUrl = if (entryFile.exists()) {
            "file://${entryFile.absolutePath}"
        } else {
            // Tenta fallback para index.html
            val fallback = File(appDir, "index.html")
            if (fallback.exists()) "file://${fallback.absolutePath}" else "file:///android_asset/placeholder.html"
        }

        webView?.loadUrl(targetUrl)
    }

    fun canGoBack(): Boolean = webView?.canGoBack() == true

    fun goBack() {
        webView?.goBack()
    }

    fun isCustomViewShowing(): Boolean = customView != null

    private fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }

        customView = view
        customViewCallback = callback
        webView?.visibility = View.GONE
        customViewContainer?.visibility = View.VISIBLE
        customViewContainer?.addView(
            view,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    fun hideCustomView() {
        if (customView == null) return

        webView?.visibility = View.VISIBLE
        customViewContainer?.visibility = View.GONE
        customViewContainer?.removeView(customView)
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
    }

    fun saveState(outState: Bundle) {
        webView?.saveState(outState)
    }

    fun restoreState(inState: Bundle) {
        webView?.restoreState(inState)
    }

    fun onResume() {
        webView?.onResume()
    }

    fun onPause() {
        webView?.onPause()
    }

    fun destroyWebView() {
        webView?.let { wv ->
            removeView(wv)
            wv.stopLoading()
            wv.clearHistory()
            wv.clearCache(true)
            wv.loadUrl("about:blank")
            wv.onPause()
            wv.removeAllViews()
            wv.destroy()
        }
        webView = null
    }
}
