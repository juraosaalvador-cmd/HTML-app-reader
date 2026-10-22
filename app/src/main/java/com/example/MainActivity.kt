package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.webkit.PermissionRequest
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.data.AppRepository
import com.example.data.HtmlApp
import com.example.databinding.ActivityMainBinding
import com.example.installer.HtmlInstaller
import com.example.installer.UrlInstaller
import com.example.ui.AppListAdapter
import com.example.ui.DialogHelper
import com.example.util.ImmersiveModeHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * Atividade principal e host da aplicação HTML App Reader.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: AppRepository
    private lateinit var htmlInstaller: HtmlInstaller
    private lateinit var urlInstaller: UrlInstaller
    private lateinit var immersiveHelper: ImmersiveModeHelper
    private lateinit var appAdapter: AppListAdapter

    private var pendingMediaRequest: PermissionRequest? = null
    private var pendingFileUri: Uri? = null
    private var pendingFileName: String? = null
    private var activeImportController: DialogHelper.ImportDialogController? = null

    // Launcher do Seletor de Arquivos SAF (Zero permissões perigosas necessárias)
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            handleSelectedFileUri(uri)
        }
    }

    // Launcher para permissões de Câmera / Áudio requeridas por WebChromeClient
    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        val req = pendingMediaRequest
        if (req != null) {
            if (granted) {
                req.grant(req.resources)
            } else {
                req.deny()
            }
            pendingMediaRequest = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AppRepository(this)
        htmlInstaller = HtmlInstaller(this, repository)
        urlInstaller = UrlInstaller(repository)
        immersiveHelper = ImmersiveModeHelper(this)

        setupFloatingControls()
        setupDrawer()
        setupWebView()
        setupBackHandler()

        // Restaura estado ou carrega inicial
        if (savedInstanceState != null) {
            binding.webAppView.restoreState(savedInstanceState)
        } else {
            binding.webAppView.loadPlaceholder()
        }

        // Observa banco de dados de apps
        observeApps()

        // Semeia aplicações de demonstração se for primeira execução
        seedSampleAppsIfNecessary()
    }

    private fun setupFloatingControls() {
        // Alternar Drawer (≡)
        binding.btnMenuToggle.setOnClickListener {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // Alternar modo imersivo (⛶)
        binding.btnFullscreenToggle.setOnClickListener {
            val isImmersive = immersiveHelper.toggle()
            val iconRes = if (isImmersive) {
                R.drawable.ic_fullscreen_exit
            } else {
                R.drawable.ic_fullscreen_enter
            }
            binding.btnFullscreenToggle.setImageResource(iconRes)
        }

        // Recarregar / Voltar para Home Terminal
        binding.btnReload.setOnClickListener {
            val current = binding.webAppView.currentApp
            if (current != null) {
                val appDir = repository.getAppDirectory(current.id)
                binding.webAppView.loadApp(current, appDir)
                Toast.makeText(this, "Recarregando: ${current.name}", Toast.LENGTH_SHORT).show()
            } else {
                binding.webAppView.loadPlaceholder()
            }
        }
    }

    private fun setupDrawer() {
        appAdapter = AppListAdapter(
            onAppClick = { app ->
                openApp(app)
            },
            onAppRename = { app ->
                DialogHelper.showRenameDialog(this, app.name) { newName ->
                    lifecycleScope.launch {
                        repository.renameApp(app.id, newName)
                        Toast.makeText(this@MainActivity, "Nome atualizado", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onAppDetails = { app ->
                val appDir = repository.getAppDirectory(app.id)
                DialogHelper.showAppDetailsDialog(this, app, appDir)
            },
            onAppDelete = { app ->
                DialogHelper.showConfirmationDialog(
                    this,
                    getString(R.string.confirm_delete_title),
                    getString(R.string.confirm_delete_msg)
                ) {
                    lifecycleScope.launch {
                        repository.deleteApp(app.id)
                        if (binding.webAppView.currentApp?.id == app.id) {
                            binding.webAppView.loadPlaceholder()
                            immersiveHelper.disableImmersiveMode()
                        }
                        Toast.makeText(this@MainActivity, "App excluído", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        binding.recyclerViewApps.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appAdapter
        }

        binding.btnImportFile.setOnClickListener {
            openImportDialog()
        }

        binding.btnImportUrl.setOnClickListener {
            openImportDialog()
        }

        binding.tvSettings.setOnClickListener {
            DialogHelper.showAboutDialog(this)
        }

        // Ouve abertura do drawer para fechar imersivo temporariamente se necessário
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                // Mantém barras limpas
            }
        })
    }

    private fun setupWebView() {
        binding.webAppView.onExitRequested = {
            confirmExitCurrentApp()
        }

        binding.webAppView.onMediaPermissionRequest = { request ->
            val perms = mutableListOf<String>()
            for (res in request.resources) {
                if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                    perms.add(Manifest.permission.CAMERA)
                }
                if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                    perms.add(Manifest.permission.RECORD_AUDIO)
                }
            }
            if (perms.isNotEmpty()) {
                pendingMediaRequest = request
                mediaPermissionLauncher.launch(perms.toTypedArray())
            } else {
                request.grant(request.resources)
            }
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 1. Se a barra lateral estiver aberta, fecha
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    return
                }

                // 2. Se elemento de vídeo/HTML5 estiver em tela cheia, fecha
                if (binding.webAppView.isCustomViewShowing()) {
                    binding.webAppView.hideCustomView()
                    return
                }

                // 3. Se a WebView puder navegar para trás no histórico do app
                if (binding.webAppView.canGoBack()) {
                    binding.webAppView.goBack()
                    return
                }

                // 4. Se um app estiver aberto, pede confirmação para fechar e voltar ao terminal
                if (binding.webAppView.currentApp != null) {
                    confirmExitCurrentApp()
                    return
                }

                // 5. Se já estiver no terminal raiz, fecha o app normalmente
                finish()
            }
        })
    }

    private fun openApp(app: HtmlApp) {
        val appDir = repository.getAppDirectory(app.id)
        if (!appDir.exists()) {
            Toast.makeText(this, "Pasta do app não encontrada no disco", Toast.LENGTH_SHORT).show()
            return
        }

        binding.webAppView.loadApp(app, appDir)
        appAdapter.selectedAppId = app.id

        // Fecha a barra lateral automaticamente
        binding.drawerLayout.closeDrawer(GravityCompat.START)

        // Modo imersivo automático conforme especificação
        immersiveHelper.enableImmersiveMode()
        binding.btnFullscreenToggle.setImageResource(R.drawable.ic_fullscreen_exit)

        // Incrementa contagem de execuções
        lifecycleScope.launch {
            repository.recordAppOpened(app.id)
        }
    }

    private fun confirmExitCurrentApp() {
        DialogHelper.showConfirmationDialog(
            this,
            getString(R.string.confirm_close_app_title),
            getString(R.string.confirm_close_app_msg)
        ) {
            binding.webAppView.loadPlaceholder()
            appAdapter.selectedAppId = null
            immersiveHelper.disableImmersiveMode()
            binding.btnFullscreenToggle.setImageResource(R.drawable.ic_fullscreen_enter)
        }
    }

    private fun openImportDialog() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        pendingFileUri = null
        pendingFileName = null

        activeImportController = DialogHelper.showImportDialog(
            activity = this,
            onChooseFileClick = {
                filePickerLauncher.launch(
                    arrayOf(
                        "text/html",
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream",
                        "*/*"
                    )
                )
            },
            onConfirmFileImport = { customName ->
                val uri = pendingFileUri
                val fileName = pendingFileName
                if (uri == null || fileName == null) {
                    Toast.makeText(this, "Selecione um arquivo .html ou .zip primeiro", Toast.LENGTH_SHORT).show()
                    return@showImportDialog
                }
                activeImportController?.showProgress("Instalando pacote no armazenamento privado...")
                lifecycleScope.launch {
                    val result = htmlInstaller.installFromFile(uri, fileName, customName)
                    result.onSuccess { installedApp ->
                        activeImportController?.dismiss()
                        Toast.makeText(this@MainActivity, "App instalado com sucesso!", Toast.LENGTH_SHORT).show()
                        openApp(installedApp)
                    }.onFailure { err ->
                        activeImportController?.dismiss()
                        Toast.makeText(this@MainActivity, "Falha: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onConfirmUrlImport = { url, customName ->
                activeImportController?.showProgress("Conectando e baixando página web...")
                lifecycleScope.launch {
                    val result = urlInstaller.downloadAndInstall(url, customName) { progressMsg ->
                        activeImportController?.showProgress(progressMsg)
                    }
                    result.onSuccess { installedApp ->
                        activeImportController?.dismiss()
                        Toast.makeText(this@MainActivity, "Instalado com sucesso!", Toast.LENGTH_SHORT).show()
                        openApp(installedApp)
                    }.onFailure { err ->
                        activeImportController?.dismiss()
                        Toast.makeText(this@MainActivity, "Erro: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    private fun handleSelectedFileUri(uri: Uri) {
        pendingFileUri = uri
        var displayName = "app.html"

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                displayName = cursor.getString(nameIndex)
            }
        }
        pendingFileName = displayName
        activeImportController?.setSelectedFile(displayName)
    }

    private fun observeApps() {
        lifecycleScope.launch {
            repository.appsFlow.collectLatest { apps ->
                appAdapter.submitList(apps)
                binding.tvAppCount.text = getString(R.string.subtitle_installed_apps, apps.size)
                if (apps.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.recyclerViewApps.visibility = View.GONE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    binding.recyclerViewApps.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun seedSampleAppsIfNecessary() {
        lifecycleScope.launch {
            val existing = repository.getAppDirectory("sample_matrix")
            if (!existing.exists()) {
                htmlInstaller.installFromAssets(
                    assetFolder = "samples/matrix",
                    appId = "sample_matrix",
                    appName = "Matrix Rain Canvas",
                    entryFile = "index.html"
                )
            }

            val existingNotes = repository.getAppDirectory("sample_notes")
            if (!existingNotes.exists()) {
                htmlInstaller.installFromAssets(
                    assetFolder = "samples/notes",
                    appId = "sample_notes",
                    appName = "Hacker Scratchpad",
                    entryFile = "index.html"
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webAppView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        binding.webAppView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.webAppView.onPause()
    }

    override fun onDestroy() {
        binding.webAppView.destroyWebView()
        super.onDestroy()
    }
}
