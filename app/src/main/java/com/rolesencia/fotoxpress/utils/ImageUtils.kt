package com.rolesencia.fotoxpress.utils

import android.graphics.Bitmap
import android.graphics.Matrix

object ImageUtils {

    /**
     * Rota un bitmap en memoria.
     * Cuidado: Esto consume RAM. El Garbage Collector limpiará el viejo.
     */
    fun rotarBitmap(source: Bitmap, angle: Float): Bitmap {
        if (angle == 0f) return source

        val matrix = Matrix()
        matrix.postRotate(angle)

        return Bitmap.createBitmap(
            source,
            0, 0,
            source.width,
            source.height,
            matrix,
            true
        )
    }
}