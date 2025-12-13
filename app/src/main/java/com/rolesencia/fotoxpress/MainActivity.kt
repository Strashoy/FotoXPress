package com.rolesencia.fotoxpress

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rolesencia.fotoxpress.ui.screens.PantallaSeleccion
import com.rolesencia.fotoxpress.ui.theme.FotoXPressTheme
import com.rolesencia.fotoxpress.ui.theme.UOMTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        // 1. INSTALAR EL SPLASH (Justo antes del super.onCreate)
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // 2. LA ESPERA DRAMÁTICA (2 Segundos)
        var mantenerSplash = true

        lifecycleScope.launch {
            delay(2000) // 2 segundos para mostrar el logo
            mantenerSplash = false
        }

        // Bloqueamos el pintado de la app hasta que termine el timer
        splashScreen.setKeepOnScreenCondition { mantenerSplash }

        setContent {
            // DETECTAMOS EL TEMA DEL SISTEMA
            val systemDark = isSystemInDarkTheme()

            // FIX: Usamos rememberSaveable
            // Esto dice: "Si ya tengo un valor guardado (porque rotó), úsalo.
            // Si es la primera vez que arranco, usa 'systemDark'".
            var isDarkTheme by rememberSaveable { mutableStateOf(systemDark) }

            UOMTheme(
                darkTheme = isDarkTheme,
                dynamicColor = false
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PantallaSeleccion(
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = { isDarkTheme = !isDarkTheme }
                    )
                }
            }
        }
    }
}