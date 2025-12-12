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
import com.rolesencia.fotoxpress.data.local.model.SesionConProgreso
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PantallaInicio(
    sesiones: List<SesionConProgreso>,
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
                    items(sesiones, key = { it.sesion.id }) { item ->
                        ItemSesion(item, onRetomarSesion, onBorrarSesion)
                    }
                }
            }
        }
    }
}

@Composable
fun ItemSesion(
    item: SesionConProgreso,
    onClick: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    val sesion = item.sesion
    val fecha = remember(sesion.fechaCreacion) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(sesion.fechaCreacion))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(sesion.id) }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // TITULO
                Text(
                    text = sesion.nombre,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // FECHA (Gris sutil)
                Text(
                    text = "Creada: $fecha",
                    color = Color.LightGray.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(4.dp)) // Espacio para separar

                // PROGRESO (Blanco y destacado)
                Text(
                    text = "Progreso: ${item.fotosEditadas} de ${sesion.cantidadFotos}",
                    color = Color.White, // Ahora se ve perfecto
                    fontWeight = FontWeight.Medium, // Un poco más gordita la letra
                    fontSize = 14.sp
                )
            }

            // BOTÓN BORRAR
            IconButton(onClick = { onDelete(sesion.id) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Borrar",
                    tint = Color.Gray
                )
            }
        }
    }
}