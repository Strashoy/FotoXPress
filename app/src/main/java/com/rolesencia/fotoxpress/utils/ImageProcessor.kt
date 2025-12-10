package com.rolesencia.fotoxpress.domain.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object ImageProcessor {

    /**
     * Aplica la rotación y el recorte (crop) estético a un Bitmap.
     * Mantiene las dimensiones originales de la imagen.
     *
     * @param original El bitmap cargado del disco.
     * @param grados Rotación en grados (ej: -8f, 90f).
     * @param scaleUsuario Zoom extra aplicado por el usuario (pinch).
     * @return Un nuevo Bitmap editado.
     */
    fun aplicarEdicion(original: Bitmap, grados: Float, scaleUsuario: Float = 1f): Bitmap {
        val ancho = original.width
        val alto = original.height

        // 1. Calcular el Auto-Crop (La misma lógica que en la UI)
        // Esto nos da el factor de zoom base para esconder los triángulos negros.
        val scaleBase = calcularFactorAutoCrop(ancho.toFloat(), alto.toFloat(), grados)

        // 2. Combinar escalas
        val scaleTotal = scaleBase * scaleUsuario

        // 3. Crear el Bitmap destino (Mismo tamaño que el original para mantener resolución)
        // Usamos ARGB_8888 para máxima calidad
        val bitmapResultado = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmapResultado)

        // 4. Configurar la Matriz de Transformación
        val matrix = Matrix()

        // A. Movemos el centro de la imagen al origen (0,0) para rotar desde el centro
        matrix.postTranslate(-ancho / 2f, -alto / 2f)

        // B. Rotamos
        matrix.postRotate(grados)

        // C. Escalamos (Aquí es donde eliminamos los bordes negros)
        matrix.postScale(scaleTotal, scaleTotal)

        // D. Devolvemos la imagen al centro del Canvas
        matrix.postTranslate(ancho / 2f, alto / 2f)

        // 5. Dibujar
        // El Paint con isAntiAlias=true y isFilterBitmap=true es VITAL para que no se pixele al rotar
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }

        canvas.drawBitmap(original, matrix, paint)

        return bitmapResultado
    }

    // Tu fórmula matemática, portada para usarla aquí también
    private fun calcularFactorAutoCrop(ancho: Float, alto: Float, grados: Float): Float {
        if (ancho == 0f || alto == 0f) return 1f
        val rad = Math.toRadians(abs(grados.toDouble()))
        val sinRad = sin(rad)
        val cosRad = cos(rad)

        val anchoProyectado = ancho * cosRad + alto * sinRad
        val altoProyectado = ancho * sinRad + alto * cosRad

        val factorAncho = anchoProyectado / ancho
        val factorAlto = altoProyectado / alto

        return max(factorAncho, factorAlto).toFloat()
    }
}