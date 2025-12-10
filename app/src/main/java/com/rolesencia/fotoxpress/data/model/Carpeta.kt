package com.rolesencia.fotoxpress.data.model

import android.net.Uri

data class Carpeta(
    val id: String,          // El ID interno (Bucket ID)
    val nombre: String,      // Ej: "Cámara", "Torneo Sábado"
    val primeraFotoUri: Uri, // Para usar de portada
    val cantidadFotos: Int   // Ej: 615
)