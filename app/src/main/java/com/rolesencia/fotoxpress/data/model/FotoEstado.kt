package com.rolesencia.fotoxpress.data.model

import android.net.Uri

/**
 * Representa el estado "vivo" de una foto en la memoria de la App.
 * Contiene la referencia al archivo original y las decisiones que has tomado (rotación/borrado),
 * pero NO modifica el archivo real hasta el paso final.
 */
data class FotoEstado(
    val id: Long,             // Identificador único (lo sacaremos del sistema de Android)
    val uri: Uri,             // La dirección "mapa" para encontrar el archivo en el disco
    val rotacion: Float = 0f, // Los grados de corrección (ej: 4.5f, -2.0f)
    val decision: Decision = Decision.PENDIENTE // Tu veredicto sobre la foto
)
/**
 * Las tres posibles etiquetas que puede tener una foto en tu flujo de trabajo.
 */
enum class Decision {
    PENDIENTE,  // Aún no la has evaluado (Neutro)
    CONSERVAR,  // Swipe Derecha: Se procesará y guardará (Verde)
    ELIMINAR    // Swipe Izquierda: Se enviará a la papelera (Rojo)
}