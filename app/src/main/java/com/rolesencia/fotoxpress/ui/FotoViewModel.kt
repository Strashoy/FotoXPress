package com.rolesencia.fotoxpress.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rolesencia.fotoxpress.data.model.Carpeta // Asegúrate de importar esto
import com.rolesencia.fotoxpress.data.model.Decision
import com.rolesencia.fotoxpress.data.model.FotoEstado
import com.rolesencia.fotoxpress.data.repository.FotoRepository
import com.rolesencia.fotoxpress.domain.image.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FotoViewModel(private val repository: FotoRepository) : ViewModel() {

    private var listaMaestraFotos = mutableListOf<FotoEstado>()
    private var indiceActual = 0

    // Guardar ID de la sesión actual
    private var currentSesionId: Long? = null

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

            // PASO A: Obtenemos las URIs crudas del dispositivo (usando tu método viejo)
            // Nota: Asumo que 'obtenerFotosDeDispositivo' devuelve List<FotoEstado>.
            // Si devuelve otra cosa, ajusta el map.
            val fotosOriginales = repository.obtenerFotosDeDispositivo(bucketId)
            val uris = fotosOriginales.map { it.uri }

            if (uris.isNotEmpty()) {
                // PASO B (NUEVO): Creamos la Sesión en la Base de Datos
                val nombreSesion = "Sesión ${System.currentTimeMillis()}" // Puedes mejorar el nombre luego
                val idSesion = repository.crearNuevaSesion(nombreSesion, uris)
                currentSesionId = idSesion

                // PASO C (NUEVO): Ahora cargamos las fotos DESDE LA BASE DE DATOS (Entities -> Models)
                listaMaestraFotos = repository.cargarFotosDeSesion(idSesion).toMutableList()

                indiceActual = 0
                actualizarFotoVisible()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, fotoActual = null)
            }
        }
    }

    // Para el botón "Volver"
    fun volverASeleccion() {
        currentSesionId = null // Limpiamos la sesión al salir
        cargarCarpetas()
    }

    // --- EDICIÓN CON PERSISTENCIA INSTANTÁNEA (Entretiempo) ---
    fun actualizarRotacion(deltaRotacion: Float) {
        val foto = _uiState.value.fotoActual ?: return
        val nuevaRotacion = foto.rotacion + deltaRotacion
        val fotoActualizada = foto.copy(rotacion = nuevaRotacion)

        // 1. Actualizamos UI (Rápido)
        listaMaestraFotos[indiceActual] = fotoActualizada
        _uiState.value = _uiState.value.copy(fotoActual = fotoActualizada)

        // 2. Actualizamos DB (Persistencia)
        viewModelScope.launch(Dispatchers.IO) {
            repository.actualizarEstadoFoto(foto.id, fotoActualizada.rotacion, fotoActualizada.decision)
        }
    }

    fun tomarDecision(decision: Decision) {
        val foto = _uiState.value.fotoActual ?: return
        val fotoDecidida = foto.copy(decision = decision)

        // 1. Actualizamos UI
        listaMaestraFotos[indiceActual] = fotoDecidida

        // 2. NUEVO: Actualizamos DB
        viewModelScope.launch(Dispatchers.IO) {
            repository.actualizarEstadoFoto(foto.id, fotoDecidida.rotacion, fotoDecidida.decision)
        }

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
    // Nota: Aquí leemos de 'listaMaestraFotos', que está sincronizada con la DB, así que es seguro.
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
                    fotosParaEditar.forEach { foto ->
                        aplicarEdicionConRecorte(foto)
                    }
                }
            }
            finalizarProceso() // Cache busting y recarga
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
                // Ejecutamos la edición real con recorte.
                val fotosParaEditar = listaMaestraFotos.filter { it.decision == Decision.CONSERVAR && it.rotacion != 0f }

                fotosParaEditar.forEach { foto ->
                    aplicarEdicionConRecorte(foto)
                }

                finalizarProceso()
            }
        }
    }

    private fun finalizarProceso() {
        // Al finalizar, podríamos borrar la sesión de la DB si quisiéramos
        // repository.eliminarSesion(currentSesionId) <- Pendiente para el futuro
        // Recargamos y volvemos al inicio
        viewModelScope.launch(Dispatchers.IO) {
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

    private fun aplicarEdicionConRecorte(foto: FotoEstado) {
        try {
            // 1. Pedimos al repo que nos de el Bitmap original
            // (Nota: Tendrás que asegurarte de tener esta función en el repo, ver abajo)
            val bitmapOriginal = repository.cargarBitmap(foto.uri) ?: return

            // 2. Usamos tu nuevo ImageProcessor para rotar Y recortar (Auto-Crop)
            val bitmapEditado = ImageProcessor.aplicarEdicion(
                original = bitmapOriginal,
                grados = foto.rotacion
                // scaleUsuario = foto.scaleUsuario // Descomenta si decidiste guardar el zoom manual también
            )

            // 3. Guardamos el resultado sobrescribiendo el archivo
            repository.sobrescribirImagen(foto.uri, bitmapEditado)

            // 4. Limpieza de memoria
            if (bitmapOriginal != bitmapEditado) bitmapOriginal.recycle()
            // bitmapEditado se recicla solo o al salir, pero cuidado con la memoria aquí.

        } catch (e: Exception) {
            e.printStackTrace()
            // Manejar error (opcional)
        }
    }

    // --- CLASE FACTORY (NECESARIA PARA INYECTAR DEPENDENCIAS) ---
// Copia esto al final del archivo FotoViewModel.kt

    class FotoViewModelFactory(private val repository: FotoRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FotoViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return FotoViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

