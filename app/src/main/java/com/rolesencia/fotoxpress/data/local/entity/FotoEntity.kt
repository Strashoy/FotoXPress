package com.rolesencia.fotoxpress.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fotos_sesion",
    // Esta clave foránea asegura que si borras una Sesion, se borren sus fotos automáticamente (CASCADE)
    foreignKeys = [
        ForeignKey(
            entity = SesionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sesionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // Creamos un índice para que buscar por sesión sea rapidísimo
    indices = [Index(value = ["sesionId"])]
)
data class FotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sesionId: Long,
    val uriString: String,           // Room no guarda Uri, guarda String. Convertiremos al leer.

    // El "Entretiempo" (Estado Mutable)
    val rotacion: Float = 0f,
    val decision: String = "PENDIENTE", // "PENDIENTE", "CONSERVAR", "ELIMINAR"

    // Ordenamiento (opcional pero útil para mantener secuencia)
    val orden: Int = 0
)