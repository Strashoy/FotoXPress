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

class FotoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FotoRepository(application)
    private var listaMaestraFotos = mutableListOf<FotoEstado>()
    private var indiceActual = 0

    // ESTADO DE LA UI
    data class UiState(
        val mostrandoCarpetas: Boolean = true, // ¿Estamos eligiendo carpeta?
        val listaCarpetas: List<Carpeta> = emptyList(), // La lista para mostrar
        val fotoActual: FotoEstado? = null,
        val isLoading: Boolean = true,
        val fotosRestantes: Int = 0,
        val totalFotos: Int = 0,
        val solicitudPermiso: android.content.IntentSender? = null, // EL POPUP
        val tipoAccionPendiente: String? = null, // "BORRAR" o "EDITAR"
        val versionCache: Long = System.currentTimeMillis() // Firma de tiempo
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
                listaCarpetas = carpetas,
                versionCache = System.currentTimeMillis() // Ve la versión
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

    // FASE 1: Intentamos borrar primero
    fun ejecutarCambiosReales() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // 1. Filtramos qué fotos hay que borrar
            val fotosParaBorrar = listaMaestraFotos.filter { it.decision == Decision.ELIMINAR }

            if (fotosParaBorrar.isNotEmpty()) {
                val uris = fotosParaBorrar.map { it.uri }
                // Pedimos el Intent al repo
                val intentSender = repository.generarPermisoBorrado(uris)

                if (intentSender != null) {
                    // ¡ALTO! Necesitamos permiso. Le decimos a la UI que muestre el popup.
                    _uiState.value = _uiState.value.copy(
                        solicitudPermiso = intentSender,
                        tipoAccionPendiente = "BORRAR"
                    )
                    return@launch // Pausamos aquí hasta que el usuario responda
                } else {
                    // Si es Android 9 o inferior, borramos directo
                    fotosParaBorrar.forEach { repository.eliminarFoto(it.uri) }
                }
            }

            // Si no había nada que borrar (o ya se borró), pasamos a Fase 2
            procesarEdiciones()
        }
    }

    // FASE 2: Procesar ediciones (Rotación)
    private fun procesarEdiciones() {
        viewModelScope.launch(Dispatchers.IO) {
            val fotosParaEditar = listaMaestraFotos.filter { it.decision == Decision.CONSERVAR && it.rotacion != 0f }

            if (fotosParaEditar.isNotEmpty()) {
                val uris = fotosParaEditar.map { it.uri }
                val intentSender = repository.generarPermisoEscritura(uris)

                if (intentSender != null) {
                    _uiState.value = _uiState.value.copy(
                        solicitudPermiso = intentSender,
                        tipoAccionPendiente = "EDITAR"
                    )
                    return@launch
                } else {
                    // Android antiguo o permiso ya concedido
                    fotosParaEditar.forEach { repository.guardarRotacion(it.uri, it.rotacion) }
                }
            }

            finalizarProceso()
        }
    }

    // FASE 3: Callback (Cuando el usuario dice "SÍ" en el popup)
    fun onPermisoOtorgado() {
        // El usuario dijo SI en el popup.
        // Limpiamos el popup del estado
        val accion = _uiState.value.tipoAccionPendiente
        _uiState.value = _uiState.value.copy(solicitudPermiso = null, tipoAccionPendiente = null)

        viewModelScope.launch(Dispatchers.IO) {
            if (accion == "BORRAR") {
                // En Android 11, al aceptar el popup, EL SISTEMA YA BORRÓ LAS FOTOS.
                // No necesitamos llamar a repository.eliminarFoto() de nuevo.
                // Pasamos directo a editar.
                procesarEdiciones()
            } else if (accion == "EDITAR") {
                // En Escritura, el sistema nos dio PERMISO, pero NO rotó la foto.
                // Ahora sí tenemos llave para entrar.
                val fotosParaEditar = listaMaestraFotos.filter { it.decision == Decision.CONSERVAR && it.rotacion != 0f }
                fotosParaEditar.forEach { repository.guardarRotacion(it.uri, it.rotacion) }
                finalizarProceso()
            }
        }
    }

    private fun finalizarProceso() {
        // Recargamos y volvemos al inicio
        val carpetas = repository.obtenerCarpetasConFotos()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            fotoActual = null,
            mostrandoCarpetas = true,
            listaCarpetas = carpetas,
            versionCache = System.currentTimeMillis()
        )
    }
}

