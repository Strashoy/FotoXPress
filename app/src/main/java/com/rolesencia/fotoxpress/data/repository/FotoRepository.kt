package com.rolesencia.fotoxpress.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.rolesencia.fotoxpress.data.model.Carpeta
import com.rolesencia.fotoxpress.data.model.FotoEstado

class FotoRepository(private val context: Context) {

    // FUNCIÓN 1: Escanear y agrupar carpetas
    fun obtenerCarpetasConFotos(): List<Carpeta> {
        val carpetasMap = mutableMapOf<String, Carpeta>()

        // Pedimos ID de bucket, nombre y ID de foto (para la portada)
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media._ID
        )

        // Ordenamos por fecha (las más nuevas primero)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, null, sortOrder
        )

        cursor?.use {
            val bucketIdCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

            while (it.moveToNext()) {
                val bucketId = it.getString(bucketIdCol) ?: continue
                val bucketName = it.getString(bucketNameCol) ?: "Desconocido"
                val imageId = it.getLong(idCol)

                if (carpetasMap.containsKey(bucketId)) {
                    // Si ya existe, sumamos 1 al contador
                    val existente = carpetasMap[bucketId]!!
                    carpetasMap[bucketId] = existente.copy(cantidadFotos = existente.cantidadFotos + 1)
                } else {
                    // Si es nueva, la creamos
                    val uriPortada = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId
                    )
                    carpetasMap[bucketId] = Carpeta(bucketId, bucketName, uriPortada, 1)
                }
            }
        }
        return carpetasMap.values.toList().sortedBy { it.nombre }
    }

    // FUNCIÓN 2: Obtener fotos (con filtro opcional)
    fun obtenerFotosDeDispositivo(bucketIdFilter: String? = null): List<FotoEstado> {
        val listaFotos = mutableListOf<FotoEstado>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        // Si hay filtro, usamos la cláusula SQL "WHERE BUCKET_ID = ?"
        val selection = if (bucketIdFilter != null) "${MediaStore.Images.Media.BUCKET_ID} = ?" else null
        val selectionArgs = if (bucketIdFilter != null) arrayOf(bucketIdFilter) else null

        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                listaFotos.add(FotoEstado(id = id, uri = contentUri))
            }
        }
        return listaFotos
    }
}