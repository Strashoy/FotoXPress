package com.rolesencia.fotoxpress.ui.screens

import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rolesencia.fotoxpress.data.local.entity.SesionEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PantallaInicio(
    sesiones: List<SesionEntity>,
    onNuevaSesion: () -> Unit,
    onRetomarSesion: (Long) -> Unit,
    onBorrarSesion: (Long) -> Unit
) {
    Scaffold(
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNuevaSesion,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("NUEVA IMPORTACIÓN")
            }
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "Tus Sesiones",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            if (sesiones.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No tienes ediciones pendientes.\nCrea una nueva para empezar.",
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    // Esto permite que el último item suba más allá del botón flotante.
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(sesiones, key = { it.id }) { sesion ->
                        ItemSesion(sesion, onRetomarSesion, onBorrarSesion)
                    }
                }
            }
        }
    }
}

@Composable
fun ItemSesion(
    sesion: SesionEntity,
    onClick: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    val fecha = remember(sesion.fechaCreacion) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(sesion.fechaCreacion))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
        modifier = Modifier.fillMaxWidth().clickable { onClick(sesion.id) }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(sesion.nombre, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Creada: $fecha", color = Color.LightGray, fontSize = 12.sp)
                Text("${sesion.cantidadFotos} fotos", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }

            IconButton(onClick = { onDelete(sesion.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = Color.Gray)
            }
        }
    }
}