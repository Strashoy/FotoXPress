package com.rolesencia.fotoxpress.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sesiones")
data class SesionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,              // Ej: "Partido Sábado 14hs"
    val fechaCreacion: Long,         // System.currentTimeMillis()
    val cantidadFotos: Int = 0,      // Para mostrar en el dashboard sin contar 1x1
    val estaFinalizada: Boolean = false // Si es true, ya se exportó
)