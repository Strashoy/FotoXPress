package com.rolesencia.fotoxpress.ui.screens

import OverlayRecorte
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import com.rolesencia.fotoxpress.FotoXPressApp
import com.rolesencia.fotoxpress.data.model.Carpeta
import com.rolesencia.fotoxpress.data.model.FotoEstado
import com.rolesencia.fotoxpress.data.repository.FotoRepository
import com.rolesencia.fotoxpress.ui.FotoViewModel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.material.icons.filled.Home
import com.rolesencia.fotoxpress.ui.screens.PantallaGaleriaSeleccion

@Composable
fun PantallaSeleccion(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit
) {

    // --- INYECCIÓN DE DEPENDENCIAS MANUAL (NUEVO) ---
    val context = LocalContext.current
    // Obtenemos la aplicación para acceder a la Base de Datos
    val app = context.applicationContext as FotoXPressApp
    val db = app.database

    // Creamos el Repo y el Factory (Usamos 'remember' para no recrearlos cada vez que la pantalla parpadea)
    val repo = remember { FotoRepository(context, db.sesionDao()) }
    val factory = remember { FotoViewModel.FotoViewModelFactory(repo) }

    // Obtenemos el ViewModel usando la Factory que acabamos de crear
    val viewModel: FotoViewModel = viewModel(factory = factory)

    // 1. OBSERVAMOS EL ESTADO DEL VIEWMODEL
    // Cada vez que el VM cambie algo, esta variable 'state' se actualizará y repintará la pantalla.
    val state by viewModel.uiState.collectAsState()
    val vistaActual by viewModel.vistaActual.collectAsState() // Navegación
    val seleccionadas by viewModel.fotosSeleccionadas.collectAsState() // Selección

    // 2. GESTIÓN DEL BOTÓN ATRÁS (Sistema)
    BackHandler {
        viewModel.manejarVolver()
    }

    // 3. GESTIÓN DE PERMISOS (Vital para que funcione)
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

    // LANZADOR PARA EL POPUP DE BORRADO/EDICIÓN
    val launcherAcciones = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // ¡EL USUARIO DIJO QUE SÍ!
            viewModel.onPermisoOtorgado()
        } else {
            // Si el usuario cancela, avisamos al VM para quitar el loading
            viewModel.onPermisoDenegado()        }
    }

    // EFECTO: Vigila si el ViewModel pide permiso
    LaunchedEffect(state.solicitudPermiso) {
        state.solicitudPermiso?.let { intentSender ->
            // Lanzamos el popup del sistema
            val request = IntentSenderRequest.Builder(intentSender).build()
            launcherAcciones.launch(request)
        }
    }



    // 4. ESTRUCTURA PRINCIPAL DE NAVEGACIÓN
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            // --- AQUÍ DECIDIMOS QUÉ PANTALLA MOSTRAR ---
            when (vistaActual) {

                // EL DASHBOARD
                FotoViewModel.VistaActual.INICIO -> {
                    // Necesitas observar la lista de sesiones
                    // Agrega esta línea arriba junto a las otras observaciones:
                    // val listaSesiones by viewModel.listaSesiones.collectAsState()

                    val listaSesiones by viewModel.listaSesiones.collectAsState()

                    PantallaInicio(
                        sesiones = listaSesiones,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = onThemeToggle,
                        onNuevaSesion = { viewModel.irANuevaImportacion() },
                        onRetomarSesion = { id -> viewModel.retomarSesion(id) },
                        onBorrarSesion = { id -> viewModel.borrarSesion(id) }
                    )
                }

                // PANTALLA 2: LISTA DE CARPETAS
                FotoViewModel.VistaActual.CARPETAS -> {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        PantallaSeleccionCarpeta(
                            carpetas = state.listaCarpetas,
                            versionCache = state.versionCache,
                            onCarpetaClick = { id ->
                                // Ahora abre la galería intermedia, no el editor directo
                                viewModel.abrirCarpetaEnGaleria(id)
                            }
                        )
                    }
                }

                // PANTALLA 2: GALERÍA DE SELECCIÓN (NUEVA)
                FotoViewModel.VistaActual.GALERIA -> {
                    val urisUsadas by viewModel.urisUsadas.collectAsState()
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        PantallaGaleriaSeleccion(
                            fotos = viewModel.obtenerListaActual(), // Debes tener este métod público en el VM
                            seleccionadas = seleccionadas,
                            onToggleSeleccion = { viewModel.toggleSeleccion(it) },
                            onRangoSeleccion = { viewModel.seleccionarRango(it) },
                            onCrearSesion = { nombre -> viewModel.confirmarSeleccionYCrearSesion(nombre) },                            onSeleccionarTodo = { viewModel.seleccionarTodo() },
                            onCancelar = { viewModel.limpiarSeleccion() },
                            onVolver = { viewModel.manejarVolver() }, // Para salir de la carpeta
                            urisUsadas = urisUsadas

                        )
                    }
                }

                // PANTALLA 3: EDITOR
                FotoViewModel.VistaActual.EDITOR -> {
                    // CAMBIO DE ORDEN LÓGICO:
                    // 1. Primero chequeamos si estamos en modo RESUMEN (fotoActual es null).
                    //    ¿Por qué? Porque PantallaResumen sabe manejar su propio isLoading
                    //    con la barra de progreso lineal.
                    if (state.fotoActual == null) {
                        PantallaResumen(
                            resumen = viewModel.obtenerResumen(),
                            isLoading = state.isLoading, // Le pasamos el estado de carga
                            progreso = state.progreso,
                            mensajeProgreso = state.mensajeProgreso,
                            modo = state.modoExportacion,
                            carpeta = state.nombreCarpetaDestino,
                            onCambiarModo = { viewModel.setModoExportacion(it) },
                            onCambiarCarpeta = { viewModel.setNombreCarpetaDestino(it) },
                            onAplicar = { viewModel.ejecutarCambiosReales() },
                            onDescartar = { viewModel.manejarVolver() }
                        )
                    }
                    // 2. Si NO es resumen, y está cargando, entonces sí usamos el círculo genérico
                    //    (ej: cargando la primera foto al abrir la sesión)
                    else if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    // 3. Si no carga y hay foto, mostramos el Editor
                    else {
                        VistaEdicion(
                            uri = state.fotoActual!!.uri,
                            rotacion = state.fotoActual!!.rotacion,
                            fotosRestantes = state.fotosRestantes,
                            versionCache = state.versionCache,
                            onPausar = { viewModel.pausarSesion() },
                            onRotar = { d -> viewModel.actualizarRotacion(d) },
                            onDecidir = { d -> viewModel.tomarDecision(d) },
                            onResetRotacion = { viewModel.setRotacionAbsoluta(0f) }
                        )
                    }
                }
            }
        }
    }
}



// --- PANTALLA RESUMEN (Extraída para limpieza) ---
@Composable
fun PantallaResumen(
    resumen: String,
    isLoading: Boolean,        // Recibimos estado de carga
    progreso: Float,           // Recibimos 0.0 a 1.0
    mensajeProgreso: String,   // Recibimos texto
    onAplicar: () -> Unit,
    onDescartar: () -> Unit,
    modo: FotoViewModel.ModoExportacion,
    carpeta: String,
    onCambiarModo: (FotoViewModel.ModoExportacion) -> Unit,
    onCambiarCarpeta: (String) -> Unit,
) {
    // EL CONTENEDOR PRINCIPAL
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), // Un poco de margen general
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isLoading) {
            // --- MODO PROCESANDO ---
            Text(
                "Aplicando cambios...",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Barra de Progreso Lineal
            LinearProgressIndicator(
                progress = { progreso },
                modifier = Modifier.width(200.dp).height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.DarkGray,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = mensajeProgreso, color = MaterialTheme.colorScheme.onSurfaceVariant)

        } else {
            Text("Configuración de Exportación", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            // TARJETA DE OPCIONES
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Destino de archivos:", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    // OPCIÓN 1: SOBRESCRIBIR
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = modo == FotoViewModel.ModoExportacion.SOBRESCRIBIR,
                            onClick = { onCambiarModo(FotoViewModel.ModoExportacion.SOBRESCRIBIR) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary, unselectedColor = Color.LightGray)
                        )
                        Text("Sobrescribir originales", color = Color.White, fontSize = 14.sp)
                    }

                    // OPCIÓN 2: COPIAR
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = modo == FotoViewModel.ModoExportacion.COPIAR,
                            onClick = { onCambiarModo(FotoViewModel.ModoExportacion.COPIAR) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary, unselectedColor = Color.LightGray)
                        )
                        Text("Copiar a carpeta nueva", color = Color.White, fontSize = 14.sp)
                    }

                    // INPUT DE NOMBRE (Solo si es COPIAR)
                    // CORRECCIÓN: Usamos '==' porque SOLO queremos verlo al copiar
                    if (modo == FotoViewModel.ModoExportacion.COPIAR) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = carpeta,
                            onValueChange = onCambiarCarpeta,
                            label = { Text("Carpeta (Nueva o Existente)") },
                            placeholder = { Text("Ej: Selección Final") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            // Colores para modo oscuro
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.LightGray,
                                cursorColor = Color.White,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = Color.LightGray
                            )
                        )

                        // TEXTO DE AYUDA
                        Text(
                            text = "* Si la carpeta ya existe, las fotos se agregarán dentro.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onAplicar,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary)
            ) {
                Text("APLICAR CAMBIOS")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(onClick = onDescartar) {
                Text("Descartar y Salir")
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
    onPausar: () -> Unit,
    onRotar: (Float) -> Unit,
    onDecidir: (Decision) -> Unit,
    onResetRotacion: () -> Unit
) {
    // ESTADO DEL ZOOM
    var scaleUsuario by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // NUEVO: Dimensiones para el cálculo
    var sizeImagen by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    // NUEVO: Para guardar la proporción real de la foto original
    var imageAspectRatio by remember { mutableFloatStateOf(1f) }

    // NUEVO: Para saber dónde está el recuadro (para el futuro)
    var rectRecorte by remember { mutableStateOf(Rect.Zero) }

    // DEFINIMOS EL MARGEN UNIFICADO AQUÍ
    val margenUnificado = 24.dp

    // CÁLCULO REACTIVO DEL AUTO-CROP
    // Cada vez que cambie la rotación, recalculamos el zoom base
    val scaleAutoCrop = remember(rotacion, sizeImagen) {
        calcularAutoCrop(
            ancho = sizeImagen.width.toFloat(),
            alto = sizeImagen.height.toFloat(),
            grados = rotacion
        )
    }

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
                .background(Color.Black) // Fondo base negro
                .clipToBounds() // Recorta solo al borde de la Zona A, no de la foto
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
                        if (scaleUsuario <= 1.05f) {
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
                        scaleUsuario = (scaleUsuario * zoom).coerceIn(1f, 5f)

                        // B) GESTIÓN DEL MOVIMIENTO (PAN/SWIPE)
                        if (scaleUsuario > 1.05f) {
                            // MODO ZOOM: El movimiento mueve la foto (Pan)
                            // Calculamos límites para que la foto no se pierda
                            val maxOffsetX = (scaleUsuario - 1) * 2000f
                            val maxOffsetY = (scaleUsuario - 1) * 2000f

                            // Aplicamos el 'pan' que nos da el gesto (funciona con 1 o 2 dedos)
                            val nuevoX = offset.x + pan.x * scaleUsuario // Multiplicamos por scale para sensación natural
                            val nuevoY = offset.y + pan.y * scaleUsuario

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
                // AQUÍ CAPTURAMOS LA PROPORCIÓN REAL
                onSuccess = { state ->
                    val size = state.painter.intrinsicSize
                    if (size.height > 0) {
                        imageAspectRatio = size.width / size.height
                    }
                },
                contentScale = ContentScale.Fit, // Ahora hará Fit dentro del área con padding
                modifier = Modifier
                    .fillMaxSize() // Llenará el área con padding
                    // 1. Le damos padding para que arranque del mismo tamaño que el Overlay
                    .padding(margenUnificado)
                    // IMPORTANTE: Permitimos que se dibuje FUERA de ese padding al rotar
                    .graphicsLayer { clip = false }
                    .onGloballyPositioned { coordinates ->
                        sizeImagen = coordinates.size
                    }
                    .onGloballyPositioned { coordinates -> // Capturamos tamaño real
                        sizeImagen = coordinates.size
                    }
                    .graphicsLayer {
                        // APLICAMOS LA ROTACIÓN VISUAL
                        rotationZ = rotacion

                        // 2. FUSIÓN DE ESCALAS
                        // Multiplicamos la automática por la manual
                        val escalaFinal = scaleAutoCrop * scaleUsuario

                        scaleX = escalaFinal
                        scaleY = escalaFinal

                        // Aplicamos la traslación correcta según el modo
                        if (scaleUsuario > 1.05f) {
                            translationX = offset.x
                            translationY = offset.y
                        } else {
                            translationX = dragOffset
                        }

                        // Feedback visual de borrado
                        if (scaleUsuario <= 1.05f) {
                            alpha = 1f - (dragOffset.absoluteValue / 1000f)
                        }
                    }
            )

            // CAPA 2: EL OVERLAY (Encima de la foto)
            if (imageAspectRatio > 0) {
                OverlayRecorte(
                    modifier = Modifier.fillMaxSize(), // Llenará el área con Padding
                    aspectRatioImagen = imageAspectRatio,
                    margen = margenUnificado, // Le pasamos el mismo valor
                    onAreaRecorteCalculada = { rect -> rectRecorte = rect }
                )
            }

            // 3. LA GRILLA (Esta NO rota, está fija)
            // Solo la mostramos si no estamos arrastrando para borrar (dragOffset == 0)
            // para no ensuciar la vista cuando quieres decidir.
            GrillaReferencia(visible = estaRotando)

            // 4. CAPA DE FEEDBACK (Overlay Verde/Rojo)
            if (dragOffset.absoluteValue > 10 && scaleUsuario <= 1.05f) {

                // LÓGICA DE COLORES Y TEXTO
                val esDerecha = dragOffset > 0
                val esModificacion = rotacion != 0f

                val (colorFondo, texto) = when {
                    !esDerecha -> Pair(Color.Red, "ELIMINAR") // Izquierda siempre es Rojo
                    esModificacion -> Pair(Color.Yellow, "MODIFICAR") // Derecha + Rotación = Amarillo
                    else -> Pair(Color.Green, "CONSERVAR") // Derecha + Recto = Verde
                }

                // Color del texto: Si es Amarillo, el blanco no se lee bien, usamos Negro
                val colorTexto = Color.White

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = if (esDerecha)
                                    listOf(Color.Transparent, colorFondo.copy(alpha = 0.5f)) // Un poco más opaco para ver el amarillo
                                else
                                    listOf(colorFondo.copy(alpha = 0.5f), Color.Transparent)
                            )
                        )
                ) {
                    Text(
                        text = texto,
                        color = colorTexto,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // --- BOTÓN HOME (FLOTANTE ARRIBA) ---
            IconButton(
                onClick = onPausar,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    // En Light Mode será blanco/gris claro, en Dark Mode será oscuro
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.Default.Home, contentDescription = "Pausar")
            }

            // Contador
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh, // Dinámico
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                val textoContador = if (fotosRestantes == 1) "Falta: 1" else "Faltan: $fotosRestantes"

                Text(
                    text = textoContador,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // --- ZONA B: CONTROL DE PRECISIÓN (20%) ---
        Box(
            modifier = Modifier
                .weight(0.2f)
                .fillMaxWidth()
                // Fondo dinámico (Claro en LightMode, Oscuro en DarkMode)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        onRotar(delta / 5)
                    },
                    interactionSource = interactionSource
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                // CAMBIO AQUÍ: Usamos una Row para poner el texto y el botón lado a lado
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${rotacion.roundToInt()}°",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Solo mostramos el botón si hay rotación (tolerancia 0.1)
                    if (kotlin.math.abs(rotacion) > 0.1f) {
                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = onResetRotacion,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Resetear Rotación",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "< ARRASTRA PARA ENDEREZAR >",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        modifier = Modifier.background(MaterialTheme.colorScheme.background)    ) {
        items(carpetas) { carpeta ->
            Card(
                onClick = { onCarpetaClick(carpeta.id) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )            ) {
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
                        Text(carpeta.nombre, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${carpeta.cantidadFotos} fotos", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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

fun calcularAutoCrop(ancho: Float, alto: Float, grados: Float): Float {
    if (ancho == 0f || alto == 0f) return 1f

    val rad = Math.toRadians(abs(grados.toDouble()))
    val sinRad = sin(rad)
    val cosRad = cos(rad)

    // Calculamos las nuevas dimensiones de la "Caja Rotada"
    val anchoRotado = ancho * cosRad + alto * sinRad
    val altoRotado = ancho * sinRad + alto * cosRad

    // Calculamos cuánto hay que estirar para que cubra el original
    // Tomamos el MAYOR factor necesario para asegurar que no queden huecos
    val factorAncho = anchoRotado / ancho
    val factorAlto = altoRotado / alto

    return Math.max(factorAncho, factorAlto).toFloat()
}