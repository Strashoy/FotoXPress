package com.rolesencia.fotoxpress.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rolesencia.fotoxpress.data.model.Decision
import com.rolesencia.fotoxpress.ui.BatallaViewModel
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun PantallaBatalla(
    viewModel: BatallaViewModel = viewModel()
) {
    // 1. OBSERVAMOS EL ESTADO DEL VIEWMODEL
    // Cada vez que el VM cambie algo, esta variable 'state' se actualizará y repintará la pantalla.
    val state by viewModel.uiState.collectAsState()

    // 2. GESTIÓN DE PERMISOS (Vital para que funcione)
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Si nos dan permiso, forzamos una recarga (truco rápido)
            // En una app final, llamaríamos a un método viewModel.recargar()
        }
    }

    LaunchedEffect(Unit) {
        // Pedimos permiso según la versión de Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // 3. ESTRUCTURA PRINCIPAL
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    // MODO CARGA
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                    Text(
                        "Escaneando Galería...",
                        color = Color.Gray,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 60.dp)
                    )
                }
                state.fotoActual == null && !state.isLoading -> {
                    // MODO "SE ACABARON LAS FOTOS"
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                        Text("¡Misión Cumplida!", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Has procesado ${state.totalFotos} fotos.", color = Color.Gray)
                        // Aquí iría el botón de "EXPORTAR RESULTADOS"
                    }
                }
                else -> {
                    // MODO BATALLA (HAY FOTO)
                    VistaDeBatalla(
                        uri = state.fotoActual!!.uri,
                        rotacion = state.fotoActual!!.rotacion,
                        fotosRestantes = state.fotosRestantes,
                        onRotar = { delta -> viewModel.actualizarRotacion(delta) },
                        onDecidir = { decision -> viewModel.tomarDecision(decision) }
                    )
                }
            }
        }
    }
}

@Composable
fun VistaDeBatalla(
    uri: android.net.Uri,
    rotacion: Float,
    fotosRestantes: Int,
    onRotar: (Float) -> Unit,
    onDecidir: (Decision) -> Unit
) {
    // Variables locales para la animación del swipe (Feedback Visual)
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val umbralDecision = 150f // Píxeles que hay que mover para confirmar

    Column(modifier = Modifier.fillMaxSize()) {

        // --- ZONA A: VISUALIZACIÓN Y DECISIÓN (80%) ---
        Box(
            modifier = Modifier
                .weight(0.8f)
                .fillMaxWidth()
                .pointerInput(Unit) { // DETECTOR DE SWIPE
                    detectDragGestures(
                        onDragEnd = {
                            // Al soltar, decidimos si fue suficiente
                            if (dragOffset > umbralDecision) {
                                onDecidir(Decision.CONSERVAR)
                            } else if (dragOffset < -umbralDecision) {
                                onDecidir(Decision.ELIMINAR)
                            }
                            dragOffset = 0f // Reset
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount.x
                    }
                }
        ) {
            // LA IMAGEN (COIL)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(uri)
                    .crossfade(true) // Transición suave
                    .build(),
                contentDescription = "Foto Actual",
                contentScale = ContentScale.Fit, // Que entre entera en la pantalla
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // APLICAMOS LA ROTACIÓN VISUAL
                        rotationZ = rotacion

                        // Pequeño efecto de desplazamiento al hacer swipe
                        translationX = dragOffset
                        alpha = 1f - (dragOffset.absoluteValue / 1000f) // Se desvanece un poco
                    }
            )

            // CAPA DE FEEDBACK (Overlay Verde/Rojo)
            if (dragOffset.absoluteValue > 10) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = if (dragOffset > 0)
                                    listOf(Color.Transparent, Color.Green.copy(alpha = 0.3f))
                                else
                                    listOf(Color.Red.copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                ) {
                    Text(
                        text = if (dragOffset > 0) "CONSERVAR" else "ELIMINAR",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Contador
            Text(
                text = "Faltan: $fotosRestantes",
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            )
        }

        // --- ZONA B: CONTROL DE PRECISIÓN (20%) ---
        Box(
            modifier = Modifier
                .weight(0.2f)
                .fillMaxWidth()
                .background(Color.DarkGray)
                .draggable( // DETECTOR DE ROTACIÓN
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        // DIVIDIMOS POR 5 PARA MAYOR PRECISIÓN
                        onRotar(delta / 5)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${rotacion.roundToInt()}°",
                    color = Color.Yellow,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "< ARRASTRA PARA ENDEREZAR >",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}