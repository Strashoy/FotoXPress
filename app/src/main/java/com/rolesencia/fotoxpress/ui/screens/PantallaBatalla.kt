package com.rolesencia.fotoxpress.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.rolesencia.fotoxpress.data.model.Carpeta

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

    // NUEVO: LANZADOR PARA EL POPUP DE BORRADO/EDICIÓN
    val launcherAcciones = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // ¡EL USUARIO DIJO QUE SÍ!
            viewModel.onPermisoOtorgado()
        } else {
            // El usuario dijo que no o canceló. Podrías mostrar un mensaje de error.
        }
    }

    // EFECTO: Vigila si el ViewModel pide permiso
    LaunchedEffect(state.solicitudPermiso) {
        state.solicitudPermiso?.let { intentSender ->
            // Lanzamos el popup del sistema
            val request = IntentSenderRequest.Builder(intentSender).build()
            launcherAcciones.launch(request)
        }
    }

    // 3. ESTRUCTURA PRINCIPAL
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            if (state.mostrandoCarpetas) {
                // MODO 1: SELECCIÓN DE CARPETA
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    PantallaSeleccionCarpeta(
                        carpetas = state.listaCarpetas,
                        onCarpetaClick = { id -> viewModel.seleccionarCarpeta(id) }
                    )
                }
            } else {
                // MODO 2: BATALLA (Edición)
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (state.fotoActual == null) {
                    // Pantalla de fin
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("¡Misión Cumplida!", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)

                        // Mostramos el resumen
                        Text(
                            text = viewModel.obtenerResumen(),
                            color = Color.LightGray,
                            modifier = Modifier.padding(24.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // BOTÓN DE EJECUCIÓN (El importante)
                        Button(
                            onClick = { viewModel.ejecutarCambiosReales() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("APLICAR CAMBIOS (Simulado)")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(onClick = { viewModel.volverASeleccion() }) {
                            Text("Descartar y Salir")
                        }
                    }
                } else {
                    // Vista de edición
                    VistaDeBatalla(
                        uri = state.fotoActual!!.uri,
                        rotacion = state.fotoActual!!.rotacion,
                        fotosRestantes = state.fotosRestantes,
                        onRotar = { d -> viewModel.actualizarRotacion(d) },
                        onDecidir = { d -> viewModel.tomarDecision(d) }
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

@Composable
fun PantallaSeleccionCarpeta(
    carpetas: List<Carpeta>,
    onCarpetaClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.background(Color.Black)
    ) {
        items(carpetas) { carpeta ->
            Card(
                onClick = { onCarpetaClick(carpeta.id) },
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
            ) {
                Column {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(carpeta.primeraFotoUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(carpeta.nombre, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${carpeta.cantidadFotos} fotos", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}