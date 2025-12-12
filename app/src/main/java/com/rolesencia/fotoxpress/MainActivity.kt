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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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