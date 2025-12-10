package com.example.fotoxpress.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.rolesencia.fotoxpress.data.model.FotoEstado

class FotoRepository(private val context: Context) {

    /**
     * Escanea el dispositivo buscando fotos.
     * Devuelve una lista limpia de objetos FotoEstado listos para usar.
     */
    fun obtenerFotosDeDispositivo(): List<FotoEstado> {
        val listaFotos = mutableListOf<FotoEstado>()

        // 1. ¿Qué columnas queremos leer de la base de datos de Android?
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )

        // 2. Ordenar por fecha (las más nuevas primero)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        // 3. Ejecutar la consulta (Query)
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, // ¿Dónde buscar? (Almacenamiento externo)
            projection, // ¿Qué datos traer?
            null,       // ¿Filtros? (null = traer todo)
            null,       // Argumentos del filtro
            sortOrder   // Orden
        )

        // 4. Procesar los resultados
        cursor?.use {
            // Obtenemos los índices de las columnas
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

            while (it.moveToNext()) {
                // Leemos el ID numérico de la foto
                val id = it.getLong(idColumn)

                // Convertimos ese ID en una dirección URI válida (content://...)
                // Esto es lo que COIL necesita para mostrar la imagen.
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                // Agregamos a nuestra lista
                listaFotos.add(
                    FotoEstado(
                        id = id,
                        uri = contentUri
                        // rotacion y decision ya tienen valores por defecto en la clase
                    )
                )
            }
        }

        return listaFotos
    }
}