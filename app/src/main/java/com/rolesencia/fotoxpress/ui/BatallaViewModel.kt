package com.rolesencia.fotoxpress.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rolesencia.fotoxpress.data.model.Carpeta // Asegúrate de importar esto
import com.rolesencia.fotoxpress.data.model.Decision
import com.rolesencia.fotoxpress.data.model.FotoEstado
import com.rolesencia.fotoxpress.data.repository.FotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BatallaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FotoRepository(application)
    private var listaMaestraFotos = mutableListOf<FotoEstado>()
    private var indiceActual = 0

    // ESTADO DE LA UI
    data class UiState(
        val mostrandoCarpetas: Boolean = true, // Nuevo: ¿Estamos eligiendo carpeta?
        val listaCarpetas: List<Carpeta> = emptyList(), // Nuevo: La lista para mostrar
        val fotoActual: FotoEstado? = null,
        val isLoading: Boolean = true,
        val fotosRestantes: Int = 0,
        val totalFotos: Int = 0
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        cargarCarpetas() // Al iniciar, cargamos carpetas, NO fotos
    }

    // LÓGICA 1: Cargar la lista de carpetas
    fun cargarCarpetas() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, mostrandoCarpetas = true)
            val carpetas = repository.obtenerCarpetasConFotos()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                listaCarpetas = carpetas
            )
        }
    }

    // LÓGICA 2: El usuario eligió una carpeta -> Cargar sus fotos
    fun seleccionarCarpeta(bucketId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, mostrandoCarpetas = false)

            // Pedimos al repo SOLO las fotos de esa carpeta
            listaMaestraFotos = repository.obtenerFotosDeDispositivo(bucketId).toMutableList()
            indiceActual = 0

            if (listaMaestraFotos.isNotEmpty()) {
                actualizarFotoVisible()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, fotoActual = null)
            }
        }
    }

    // Para el botón "Volver"
    fun volverASeleccion() {
        cargarCarpetas()
    }

    // --- MÉTODOS EXISTENTES (IGUAL QUE ANTES) ---
    fun actualizarRotacion(deltaRotacion: Float) {
        val foto = _uiState.value.fotoActual ?: return
        val nuevaRotacion = foto.rotacion + deltaRotacion
        val fotoActualizada = foto.copy(rotacion = nuevaRotacion)
        listaMaestraFotos[indiceActual] = fotoActualizada
        _uiState.value = _uiState.value.copy(fotoActual = fotoActualizada)
    }

    fun tomarDecision(decision: Decision) {
        val foto = _uiState.value.fotoActual ?: return
        val fotoDecidida = foto.copy(decision = decision)
        listaMaestraFotos[indiceActual] = fotoDecidida

        if (indiceActual < listaMaestraFotos.size - 1) {
            indiceActual++
            actualizarFotoVisible()
        } else {
            _uiState.value = _uiState.value.copy(fotoActual = null, isLoading = false)
        }
    }

    private fun actualizarFotoVisible() {
        val foto = listaMaestraFotos[indiceActual]
        _uiState.value = UiState(
            fotoActual = foto,
            isLoading = false,
            fotosRestantes = listaMaestraFotos.size - indiceActual,
            totalFotos = listaMaestraFotos.size,
            mostrandoCarpetas = false // Aseguramos que no muestre carpetas
        )
    }

    fun obtenerResumen(): String {
        val aBorrar = listaMaestraFotos.count { it.decision == Decision.ELIMINAR }
        val aEditar = listaMaestraFotos.count { it.decision == Decision.CONSERVAR && it.rotacion != 0f }
        val aIgnorar = listaMaestraFotos.count { it.decision == Decision.CONSERVAR && it.rotacion == 0f }

        return "Resumen:\n🗑️ Se borrarán: $aBorrar fotos\n🔄 Se editarán: $aEditar fotos\n✅ Se dejan igual: $aIgnorar fotos"
    }

    fun ejecutarCambiosReales() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // AQUÍ ES DONDE OCURRIRÁ LA MAGIA REAL
            listaMaestraFotos.forEach { foto ->
                when {
                    foto.decision == Decision.ELIMINAR -> {
                        // repository.borrarFoto(foto.uri) // PRÓXIMAMENTE
                        println("SIMULACIÓN: Borrando foto ${foto.id}")
                    }
                    foto.decision == Decision.CONSERVAR && foto.rotacion != 0f -> {
                        // repository.procesarYGuardar(foto) // PRÓXIMAMENTE
                        println("SIMULACIÓN: Rotando foto ${foto.id} a ${foto.rotacion} grados")
                    }
                }
            }

            // Al terminar, volvemos al inicio
            _uiState.value = _uiState.value.copy(isLoading = false, fotoActual = null, mostrandoCarpetas = true)
        }
    }
}

