package com.example.util

import android.app.Activity
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Utilitário para controle do modo imersivo de tela cheia.
 */
class ImmersiveModeHelper(private val activity: Activity) {

    private val window = activity.window
    private val controller: WindowInsetsControllerCompat =
        WindowCompat.getInsetsController(window, window.decorView)

    var isImmersive: Boolean = false
        private set

    init {
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /**
     * Habilita tela cheia imersiva (oculta status bar e navigation bar).
     */
    fun enableImmersiveMode() {
        controller.hide(WindowInsetsCompat.Type.systemBars())
        isImmersive = true
    }

    /**
     * Desabilita modo imersivo (revela barras do sistema).
     */
    fun disableImmersiveMode() {
        controller.show(WindowInsetsCompat.Type.systemBars())
        isImmersive = false
    }

    /**
     * Alterna entre modo imersivo e modo normal. Retorna o novo estado.
     */
    fun toggle(): Boolean {
        if (isImmersive) {
            disableImmersiveMode()
        } else {
            enableImmersiveMode()
        }
        return isImmersive
    }
}
