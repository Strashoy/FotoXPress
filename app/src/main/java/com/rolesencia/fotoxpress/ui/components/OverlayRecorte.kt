import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun OverlayRecorte(
    modifier: Modifier = Modifier,
    aspectRatioImagen: Float, // La proporción original de la foto (ancho/alto)
    margen: Dp = 24.dp, // Este valor debe coincidir con el de la foto
    onAreaRecorteCalculada: (Rect) -> Unit // Avisamos al padre dónde quedó el hueco
) {
    // Aumentamos el margen para asegurar que se note bien el efecto marco
    val margenPx = with(LocalDensity.current) { margen.toPx() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val anchoPantalla = size.width
        val altoPantalla = size.height

        // 1. CÁLCULO DEL RECUADRO BLANCO (Mantenemos tu lógica que ya funciona bien)
        val anchoDisponible = anchoPantalla - (margenPx * 2)
        val altoDisponible = altoPantalla - (margenPx * 2)

        val escalaAncho = anchoDisponible / aspectRatioImagen
        val escalaAlto = altoDisponible

        val escalaFinal = minOf(anchoDisponible, escalaAlto * aspectRatioImagen) / aspectRatioImagen

        var anchoRecuadro = escalaFinal * aspectRatioImagen
        var altoRecuadro = escalaFinal

        val left = (anchoPantalla - anchoRecuadro) / 2
        val top = (altoPantalla - altoRecuadro) / 2

        val rectRecorte = Rect(
            left = left,
            top = top,
            right = left + anchoRecuadro,
            bottom = top + altoRecuadro
        )

        // Devolvemos el dato al padre
        onAreaRecorteCalculada(rectRecorte)

        // 3. DIBUJAR LA MÁSCARA (La magia de clipPath)
        val pathAgujero = Path().apply { addRect(rectRecorte) }

        // Usamos clipPath con Difference: "Pinta en todo el lienzo MENOS en el agujero"
        clipPath(path = pathAgujero, clipOp = ClipOp.Difference) {
            // Dibujamos un rectángulo GIGANTE para asegurar que cubra todo
            // incluso si hay problemas de coordenadas.
            drawRect(
                color = Color.Black.copy(alpha = 0.6f),
                topLeft = Offset(-1000f, -1000f),
                size = Size(anchoPantalla + 2000f, altoPantalla + 2000f)
            )
        }

        // 4. DIBUJAR EL BORDE BLANCO
        drawRect(
            color = Color.White,
            topLeft = rectRecorte.topLeft,
            size = rectRecorte.size,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
    }
}