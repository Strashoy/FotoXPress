package com.rolesencia.fotoxpress.ui.screens

import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
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
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
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
                contentColor = MaterialTheme.colorScheme.onPrimary, // Usamos el color del tema
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("NUEVA IMPORTACIÓN")
            }
        },
        // Usamos el color de fondo del tema nuevo
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            // --- CABECERA CON BOTÓN DE TEMA ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween, // Separa Titulo y Boton
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tus Sesiones",
                    color = MaterialTheme.colorScheme.onBackground, // Color adaptativo
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                // BOTÓN DE CAMBIO DE TEMA (Estilo "Home")
                IconButton(
                    onClick = onThemeToggle,
                    colors = IconButtonDefaults.iconButtonColors(
                        // Usamos DarkGray si es oscuro, o Primary si es claro, o fijo según prefieras.
                        // Para mantener el estilo "Home", usaremos un color sólido que contraste.
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    // Si es Dark mostramos Sol (para ir a luz), si es Light mostramos Luna
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Cambiar Tema"
                    )
                }
            }

            if (sesiones.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No tienes ediciones pendientes.\nCrea una nueva para empezar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
        // Usamos Surface Variant (que suele ser un gris suave o tintado)
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
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
                    color = MaterialTheme.colorScheme.onSurface, // Color texto principal                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // FECHA
                Text(
                    text = "Creada: $fecha",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, // Color texto secundario                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(4.dp)) // Espacio para separar

                // PROGRESO (Blanco y destacado)
                Text(
                    text = "Progreso: ${item.fotosEditadas} de ${sesion.cantidadFotos}",
                    color = MaterialTheme.colorScheme.primary, // Usamos el color Primario (Azul/Violeta)                    fontWeight = FontWeight.Medium, // Un poco más gordita la letra
                    fontSize = 14.sp
                )
            }

            // BOTÓN BORRAR
            IconButton(onClick = { onDelete(sesion.id) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Borrar",
                    tint = MaterialTheme.colorScheme.error) // Color de error semántico
            }
        }
    }
}