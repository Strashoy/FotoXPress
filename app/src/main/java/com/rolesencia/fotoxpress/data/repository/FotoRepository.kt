package com.rolesencia.fotoxpress.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import com.rolesencia.fotoxpress.data.local.dao.SesionDao
import com.rolesencia.fotoxpress.data.model.Carpeta
import com.rolesencia.fotoxpress.data.model.FotoEstado
import com.rolesencia.fotoxpress.data.local.entity.FotoEntity
import com.rolesencia.fotoxpress.data.local.entity.SesionEntity
import com.rolesencia.fotoxpress.data.model.Decision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class FotoRepository(
    private val context: Context,
    private val sesionDao: SesionDao // El encargado de controlar
) {
    // --- MÉTODOS DE SESIÓN (NUEVOS) ---

    // 1. Crear una nueva sesión (Partido)
    suspend fun crearNuevaSesion(nombre: String, urisFotos: List<Uri>): Long {
        return withContext(Dispatchers.IO) {
            // A. Creamos la "Caja" de la sesión
            val nuevaSesion = SesionEntity(
                nombre = nombre,
                fechaCreacion = System.currentTimeMillis(),
                cantidadFotos = urisFotos.size
            )
            val sesionId = sesionDao.crearSesion(nuevaSesion)

            // B. Guardamos las fotos en el inventario (Entity)
            val fotosEntities = urisFotos.mapIndexed { index, uri ->
                FotoEntity(
                    sesionId = sesionId,
                    uriString = uri.toString(),
                    orden = index,
                    decision = "PENDIENTE"
                )
            }
            sesionDao.insertarFotos(fotosEntities)

            return@withContext sesionId
        }
    }

    // 2. Cargar una sesión existente (Aquí ocurre la traducción Entity -> Model)
    suspend fun cargarFotosDeSesion(sesionId: Long): List<FotoEstado> {
        return withContext(Dispatchers.IO) {
            val entities = sesionDao.obtenerFotosDeSesion(sesionId)

            // CONVERTIDOR: Entity (BD) -> Model (UI)
            entities.map { entity ->
                FotoEstado(
                    id = entity.id, // ID interno de la BD
                    uri = Uri.parse(entity.uriString),
                    rotacion = entity.rotacion,
                    decision = Decision.valueOf(entity.decision) // String a Enum
                )
            }
        }
    }

    // 3. Guardar Entretiempo (Actualizar DB rápido)
    suspend fun actualizarEstadoFoto(fotoId: Long, rotacion: Float, decision: Decision) {
        withContext(Dispatchers.IO) {
            // Actualizamos campos individuales para ser eficientes
            sesionDao.actualizarRotacion(fotoId, rotacion)
            sesionDao.actualizarDecision(fotoId, decision.name)
        }
    }

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

    /**
     * Intenta borrar el archivo.
     * Retorna TRUE si se borró, FALSE si falló.
     */
    fun eliminarFoto(uri: android.net.Uri): Boolean {
        return try {
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            rowsDeleted > 0
        } catch (e: SecurityException) {
            e.printStackTrace()
            // AQUÍ es donde Android 10+ grita "¡No es tu foto!"
            throw e
        }
    }

    /**
     * Lee, rota y sobrescribe.
     */
    fun guardarRotacion(uri: android.net.Uri, grados: Float) {
        try {
            // 1. Abrir lectura
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmapOriginal = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmapOriginal != null) {
                // 2. Rotar en memoria
                val bitmapRotado = com.rolesencia.fotoxpress.utils.ImageUtils.rotarBitmap(bitmapOriginal, grados)

                // 3. Abrir escritura (Modo "w" = Write/Truncate)
                val outputStream = context.contentResolver.openOutputStream(uri, "w")

                if (outputStream != null) {
                    // Guardamos manteniendo calidad (95%)
                    bitmapRotado.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, outputStream)
                    outputStream.close()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Genera la "Carta de Solicitud" para que el usuario autorice borrar fotos ajenas.
     * Solo funciona en Android 10 (API 29) y superior.
     */
    fun generarPermisoBorrado(uris: List<android.net.Uri>): android.content.IntentSender? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) { // Android 11+
            return android.provider.MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) { // Android 10
            // En Android 10 es más complicado (RecoverableSecurityException),
            // pero por simplicidad probemos si tienes Android 11+.
            return null
        }
        return null
    }

    /**
     * Genera la "Carta de Solicitud" para que el usuario autorice EDITAR (escribir) fotos ajenas.
     */
    fun generarPermisoEscritura(uris: List<android.net.Uri>): android.content.IntentSender? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) { // Android 11+
            return android.provider.MediaStore.createWriteRequest(context.contentResolver, uris).intentSender
        }
        return null
    }

    // Carga el Bitmap desde la URI
    fun cargarBitmap(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }
    }

    // Guarda el Bitmap en la URI (Sobreescribe)
    fun sobrescribirImagen(uri: Uri, bitmap: Bitmap) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            // Comprimimos al 100% de calidad (o 90% para ahorrar espacio)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        }
    }
}