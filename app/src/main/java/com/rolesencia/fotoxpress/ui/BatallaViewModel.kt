package com.rolesencia.fotoxpress.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fotoxpress.data.repository.FotoRepository
import com.rolesencia.fotoxpress.data.model.Decision
import com.rolesencia.fotoxpress.data.model.FotoEstado
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Usamos AndroidViewModel para tener acceso al 'Application' context
// y poder pasárselo al Repositorio sin complicaciones de Inyección de Dependencias complejas.
class BatallaViewModel(application: Application) : AndroidViewModel(application) {

    // 1. Instanciamos el repositorio (nuestra conexión con los datos)
    private val repository = FotoRepository(application)

    // 2. LA LISTA MAESTRA (En memoria)
    // Guardamos todas las fotos aquí. Es privada para que nadie la toque desde fuera.
    private var listaMaestraFotos = mutableListOf<FotoEstado>()
    private var indiceActual = 0

    // 3. EL ESTADO VISIBLE (Lo que la UI observa)
    // UI State: Encapsula todo lo que la pantalla necesita saber
    data class UiState(
        val fotoActual: FotoEstado? = null,
        val isLoading: Boolean = true,
        val fotosRestantes: Int = 0,
        val totalFotos: Int = 0
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 4. INICIALIZACIÓN
    init {
        cargarFotos()
    }

    private fun cargarFotos() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Pedimos las fotos al repositorio
            listaMaestraFotos = repository.obtenerFotosDeDispositivo().toMutableList()

            // Actualizamos la UI
            if (listaMaestraFotos.isNotEmpty()) {
                actualizarFotoVisible()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    // --- ACCIONES DEL USUARIO (Inputs) ---

    /**
     * Se llama cuando el usuario desliza el dedo en la zona inferior.
     * Actualizamos la rotación de la foto actual en tiempo real.
     */
    fun actualizarRotacion(deltaRotacion: Float) {
        val foto = _uiState.value.fotoActual ?: return

        // Calculamos nueva rotación
        val nuevaRotacion = foto.rotacion + deltaRotacion

        // Creamos una COPIA del objeto con el nuevo valor (Inmutabilidad)
        val fotoActualizada = foto.copy(rotacion = nuevaRotacion)

        // 1. Actualizamos la lista maestra
        listaMaestraFotos[indiceActual] = fotoActualizada

        // 2. Actualizamos el estado visible para que Compose repinte
        _uiState.value = _uiState.value.copy(fotoActual = fotoActualizada)
    }

    /**
     * Se llama cuando el usuario hace Swipe Izquierda (Eliminar) o Derecha (Conservar).
     */
    fun tomarDecision(decision: Decision) {
        val foto = _uiState.value.fotoActual ?: return

        // 1. Guardamos la decisión en la lista maestra
        val fotoDecidida = foto.copy(decision = decision)
        listaMaestraFotos[indiceActual] = fotoDecidida

        // 2. Avanzamos el índice
        if (indiceActual < listaMaestraFotos.size - 1) {
            indiceActual++
            actualizarFotoVisible()
        } else {
            // ¡Se acabaron las fotos!
            // Aquí podríamos mostrar una pantalla de "Resumen Final"
            _uiState.value = _uiState.value.copy(fotoActual = null, isLoading = false)
        }
    }

    // --- HELPERS ---

    private fun actualizarFotoVisible() {
        val foto = listaMaestraFotos[indiceActual]
        _uiState.value = UiState(
            fotoActual = foto,
            isLoading = false,
            fotosRestantes = listaMaestraFotos.size - indiceActual,
            totalFotos = listaMaestraFotos.size
        )
    }
}