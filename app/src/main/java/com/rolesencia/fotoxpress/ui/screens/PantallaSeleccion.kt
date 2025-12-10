package com.rolesencia.fotoxpress.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
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
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.geometry.Offset
import com.rolesencia.fotoxpress.data.model.Carpeta
import com.rolesencia.fotoxpress.ui.FotoViewModel

@Composable
fun PantallaSeleccion(
    viewModel: FotoViewModel = viewModel()
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
                        versionCache = state.versionCache, // <--- PASAMOS EL DATO
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
                    VistaEdicion(
                        uri = state.fotoActual!!.uri,
                        rotacion = state.fotoActual!!.rotacion,
                        fotosRestantes = state.fotosRestantes,
                        versionCache = state.versionCache,
                        onRotar = { d -> viewModel.actualizarRotacion(d) },
                        onDecidir = { d -> viewModel.tomarDecision(d) }
                    )
                }
            }
        }
    }
}

@Composable
fun VistaEdicion(
    uri: android.net.Uri,
    rotacion: Float,
    fotosRestantes: Int,
    versionCache: Long, // Recibimos el dato
    onRotar: (Float) -> Unit,
    onDecidir: (Decision) -> Unit
) {
    // ESTADO DEL ZOOM
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // ESTADO DEL SWIPE
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val umbralDecision = 150f // Píxeles que hay que mover para confirmar

    // EL DETECTOR DE TOQUE DEL DIAL
    val interactionSource = remember { MutableInteractionSource() }
    val estaRotando by interactionSource.collectIsDraggedAsState()

    Column(modifier = Modifier.fillMaxSize()) {

        // --- ZONA A: VISUALIZACIÓN Y DECISIÓN (80%) ---
        Box(
            modifier = Modifier
                .weight(0.8f)
                .fillMaxWidth()
                // DETECTOR DE "LEVANTAR EL DEDO" (Para decidir Swipe)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown() // 1. Esperamos que toque la pantalla

                        // 2. Esperamos hasta que levante TODOS los dedos
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })

                        // 3. AL SOLTAR: Decidimos qué hacer
                        // Solo si no hay zoom activo (margen de error 1.05f)
                        if (scale <= 1.05f) {
                            if (dragOffset > umbralDecision) {
                                onDecidir(Decision.CONSERVAR)
                            } else if (dragOffset < -umbralDecision) {
                                onDecidir(Decision.ELIMINAR)
                            }
                        }
                        // Siempre reseteamos el swipe al soltar
                        dragOffset = 0f
                    }
                }
                // CAPA 2: DETECTOR MATEMÁTICO UNIFICADO (Zoom + Pan)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // A) GESTIÓN DEL ZOOM
                        // Multiplicamos la escala actual por el cambio (zoom)
                        // Limitamos entre 1x y 5x
                        scale = (scale * zoom).coerceIn(1f, 5f)

                        // B) GESTIÓN DEL MOVIMIENTO (PAN/SWIPE)
                        if (scale > 1.05f) {
                            // MODO ZOOM: El movimiento mueve la foto (Pan)
                            // Calculamos límites para que la foto no se pierda
                            val maxOffsetX = (scale - 1) * 2000f
                            val maxOffsetY = (scale - 1) * 2000f

                            // Aplicamos el 'pan' que nos da el gesto (funciona con 1 o 2 dedos)
                            val nuevoX = offset.x + pan.x * scale // Multiplicamos por scale para sensación natural
                            val nuevoY = offset.y + pan.y * scale

                            offset = Offset(
                                nuevoX.coerceIn(-maxOffsetX, maxOffsetX),
                                nuevoY.coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        } else {
                            // MODO NORMAL: El movimiento es para Borrar (Swipe)
                            // Solo nos importa el movimiento horizontal (x)
                            dragOffset += pan.x
                        }                    }
                }
        ) {
            // LA IMAGEN (COIL)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(uri)
                    .crossfade(true) // Transición suave
                    .setParameter("buster", versionCache) // Lo que ve la versión
                    .build(),
                contentDescription = "Foto Actual",
                contentScale = ContentScale.Fit, // Que entre entera en la pantalla
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // APLICAMOS LA ROTACIÓN VISUAL
                        rotationZ = rotacion

                        // Zoom y Pan
                        scaleX = scale
                        scaleY = scale

                        // Aplicamos la traslación correcta según el modo
                        if (scale > 1.05f) {
                            translationX = offset.x
                            translationY = offset.y
                        } else {
                            translationX = dragOffset
                        }

                        // Feedback visual de borrado
                        if (scale <= 1.05f) {
                            alpha = 1f - (dragOffset.absoluteValue / 1000f)
                        }
                    }
            )

            // 2. LA GRILLA (Esta NO rota, está fija)
            // Solo la mostramos si no estamos arrastrando para borrar (dragOffset == 0)
            // para no ensuciar la vista cuando quieres decidir.
            GrillaReferencia(visible = estaRotando)

            // 3. CAPA DE FEEDBACK (Overlay Verde/Rojo)
            if (dragOffset.absoluteValue > 10 && scale <= 1.05f) {
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
                    },
                    interactionSource = interactionSource // Conexión del visor de rotación
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
    versionCache: Long, // Recibimos el dato
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
                            .setParameter("buster", versionCache) // Lo que ve la versión
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

@Composable
fun GrillaReferencia(
    visible: Boolean = true
) {
    if (!visible) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        val ancho = size.width
        val alto = size.height

        // --- Configuración ---
        val pasoBase = 10.dp.toPx() // Distancia mínima entre cualquier línea
        val frecuenciaMaestra = 4   // Cada 4 líneas, una es Maestra (Maestra - s - s - s - Maestra)

        // Estilos
        val colorMaestra = Color.White.copy(alpha = 0.6f)
        val grosorMaestra = 1.5.dp.toPx()

        val colorSutil = Color.White.copy(alpha = 0.3f) // Muy transparente
        val grosorSutil = 0.5.dp.toPx() // Hairline (muy fino)

        // 1. DIBUJAR VERTICALES
        var iX = 1
        var x = pasoBase
        while (x < ancho) {
            val esMaestra = (iX % frecuenciaMaestra == 0)

            drawLine(
                color = if (esMaestra) colorMaestra else colorSutil,
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, alto),
                strokeWidth = if (esMaestra) grosorMaestra else grosorSutil
            )
            x += pasoBase
            iX++
        }

        // 2. DIBUJAR HORIZONTALES
        var iY = 1
        var y = pasoBase
        while (y < alto) {
            val esMaestra = (iY % frecuenciaMaestra == 0)

            drawLine(
                color = if (esMaestra) colorMaestra else colorSutil,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(ancho, y),
                strokeWidth = if (esMaestra) grosorMaestra else grosorSutil
            )
            y += pasoBase
            iY++
        }
    }
}