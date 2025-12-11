package com.rolesencia.fotoxpress.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rolesencia.fotoxpress.data.model.FotoEstado

@OptIn(ExperimentalFoundationApi::class) // Para combinedClickable (Click largo)
@Composable
fun PantallaGaleriaSeleccion(
    fotos: List<FotoEstado>,
    seleccionadas: Set<android.net.Uri>,
    onToggleSeleccion: (FotoEstado) -> Unit,
    onRangoSeleccion: (FotoEstado) -> Unit,
    onCrearSesion: () -> Unit,
    onSeleccionarTodo: () -> Unit,
    onCancelar: () -> Unit,
    onVolver: () -> Unit
) {
    val haySeleccion = seleccionadas.isNotEmpty()
    val sonTodas = seleccionadas.size == fotos.size && fotos.isNotEmpty()

    Scaffold(
        // BARRA INFERIOR CONTEXTUAL
        bottomBar = {
                BottomAppBar(
                    containerColor = Color.DarkGray, // O MaterialTheme.colorScheme.surfaceContainer
                    contentColor = Color.White,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    // 1. IZQUIERDA: SELECCIONAR TODO
                    // Usamos TextButton para que sea fácil de tocar pero no invasivo
                    TextButton(
                        // Si ya son todas, el botón sirve para cancelar (Nada).
                        // Si falta alguna, el botón sirve para seleccionar todo.
                        onClick = if (sonTodas) onCancelar else onSeleccionarTodo,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        // Cambiamos el icono si ya están todas para dar feedback visual
                        Icon(
                            // Icono visual
                            if (sonTodas) Icons.Default.Close else Icons.Default.Menu,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (sonTodas) "Nada" else "Todo")
                    }

                    Spacer(modifier = Modifier.weight(1f)) // Empujamos al centro

                    // 2. CENTRO: EDITAR (Deshabilitado si es 0)
                    Button(
                        onClick = onCrearSesion,
                        // LÓGICA: Solo habilitado si hay al menos 1 foto
                        enabled = haySeleccion,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color.Gray.copy(alpha = 0.5f) // Estilo visual deshabilitado
                        )
                    ) {
                        Text("EDITAR (${seleccionadas.size})")
                    }

                    Spacer(modifier = Modifier.weight(1f)) // Empujamos a la derecha

                    // 3. DERECHA: CANCELAR / VOLVER
                    TextButton(
                        // LÓGICA: Si hay selección, limpia. Si no, sale de la carpeta.
                        onClick = if (haySeleccion) onCancelar else onVolver,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)
                    ) {
                        // Cambiamos el texto según el contexto
                        Text(if (haySeleccion) "Cancelar" else "Volver")
                    }
                }
        },
        containerColor = Color.Black
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 80.dp, // Espacio extra para que la última foto no quede tapada por la barra
                start = 2.dp,
                end = 2.dp
            ),            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(fotos, key = { it.uri.toString() }) { foto ->
                val estaSeleccionada = seleccionadas.contains(foto.uri)

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(Color.DarkGray)
                        // DETECTOR DE GESTOS
                        .combinedClickable(
                            onClick = { onToggleSeleccion(foto) }, // Click siempre selecciona ahora
                            onLongClick = { onRangoSeleccion(foto) }
                        )
                        .then(
                            if (estaSeleccionada) Modifier.border(4.dp, MaterialTheme.colorScheme.primary) else Modifier
                        )
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(foto.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(if (estaSeleccionada) 0.5f else 1f) // Oscurecer si seleccionada
                    )

                    // CHECK ICON (Solo si seleccionada)
                    if (estaSeleccionada) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Seleccionada",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .padding(2.dp)
                        )
                    }
                }
            }
        }
    }
}

// Extensión simple para Modifier.alpha sin importar todo GraphicsLayer
fun Modifier.alpha(alpha: Float) = this.then(
    Modifier.graphicsLayer(alpha = alpha)
)