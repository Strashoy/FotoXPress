package com.rolesencia.fotoxpress.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rolesencia.fotoxpress.data.model.Carpeta
import com.rolesencia.fotoxpress.data.model.Decision
import com.rolesencia.fotoxpress.data.model.FotoEstado
import com.rolesencia.fotoxpress.data.repository.FotoRepository
import com.rolesencia.fotoxpress.domain.image.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class FotoViewModel(private val repository: FotoRepository) : ViewModel() {

    private var listaMaestraFotos = mutableListOf<FotoEstado>()
    private var indiceActual = 0

    // Guardar ID de la sesión actual
    private var currentSesionId: Long? = null
    // Escuchamos la DB en tiempo real
    val listaSesiones = repository.obtenerSesionesActivas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Lista de fotos que ya están en alguna sesión
    private val _urisUsadas = MutableStateFlow<Set<String>>(emptySet())
    val urisUsadas = _urisUsadas.asStateFlow()

    // --- NUEVO: ESTADO DE NAVEGACIÓN ---
    enum class VistaActual { INICIO, CARPETAS, GALERIA, EDITOR }

    private val _vistaActual = MutableStateFlow(VistaActual.INICIO)
    val vistaActual: StateFlow<VistaActual> = _vistaActual.asStateFlow()

    // --- NUEVO: ESTADO DE SELECCIÓN ---
    private val _fotosSeleccionadas = MutableStateFlow<Set<Uri>>(emptySet())
    val fotosSeleccionadas: StateFlow<Set<Uri>> = _fotosSeleccionadas.asStateFlow()

    // Para el rango (Shift+Click lógico)
    private var ultimaUriSeleccionada: Uri? = null

    // Enum auxiliar (puedes ponerlo fuera de la clase o dentro)
    enum class ModoExportacion { SOBRESCRIBIR, COPIAR }

    // ESTADO DE LA UI GENERAL
    data class UiState(
        val mostrandoCarpetas: Boolean = true, // (Se mantiene por compatibilidad, pero usaremos vistaActual)
        val listaCarpetas: List<Carpeta> = emptyList(),
        val fotoActual: FotoEstado? = null,
        val isLoading: Boolean = true,
        val fotosRestantes: Int = 0,
        val totalFotos: Int = 0,
        val solicitudPermiso: android.content.IntentSender? = null,
        val tipoAccionPendiente: String? = null,
        val versionCache: Long = System.currentTimeMillis(),
        val progreso: Float = 0f, // De 0.0 a 1.0
        val mensajeProgreso: String = "", // Ej: "Guardando 3 de 10..."
        val modoExportacion: ModoExportacion = ModoExportacion.SOBRESCRIBIR,
        val nombreCarpetaDestino: String = "",
        val mostrarOpcionesExportacion: Boolean = false // Para expandir/colapsar el menú
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()



    // LÓGICA 1: Cargar la lista de carpetas
    fun cargarCarpetas() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, mostrandoCarpetas = true)
            val carpetas = repository.obtenerCarpetasConFotos()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                listaCarpetas = carpetas,
                versionCache = System.currentTimeMillis()
            )
            // Aseguramos que la vista sea carpetas
            _vistaActual.value = VistaActual.CARPETAS
        }
    }

    // --- NUEVA LÓGICA DE NAVEGACIÓN ---

    // Paso 1: Abrir carpeta -> Ver Galería (Antes esto iba directo al editor)
    fun abrirCarpetaEnGaleria(bucketId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // 1. Cargar fotos de la carpeta
            val fotos = repository.obtenerFotosDeDispositivo(bucketId)
            listaMaestraFotos = fotos.toMutableList()

            // 2. Cargar la lista negra (fotos ya importadas antes)
            _urisUsadas.value = repository.obtenerUrisProcesadas()

            // Limpiamos selección previa
            _fotosSeleccionadas.value = emptySet()
            ultimaUriSeleccionada = null

            // Cambiamos de vista
            _vistaActual.value = VistaActual.GALERIA

            // Actualizamos UI (quitamos loading)
            _uiState.value = _uiState.value.copy(isLoading = false, mostrandoCarpetas = false)
        }
    }

    // Para exponer la lista a la Galería (que no necesita fotoActual todavía)
    fun obtenerListaActual(): List<FotoEstado> = listaMaestraFotos

    // --- NUEVA LÓGICA DE SELECCIÓN ---

    fun toggleSeleccion(foto: FotoEstado) {
        val seleccionActual = _fotosSeleccionadas.value.toMutableSet()
        if (seleccionActual.contains(foto.uri)) {
            seleccionActual.remove(foto.uri)
            // Si al quitar esta foto, la lista queda vacía, reseteamos el ancla.
            // Así el próximo click largo empezará de cero.
            if (seleccionActual.isEmpty()) {
                ultimaUriSeleccionada = null
            }
        } else {
            seleccionActual.add(foto.uri)
            ultimaUriSeleccionada = foto.uri // Marcamos nuevo ancla
        }
        _fotosSeleccionadas.value = seleccionActual
    }

    fun seleccionarRango(fotoDestino: FotoEstado) {
        // Si no hay ancla (porque se limpió) O no hay nada seleccionado,
        // tratamos el click largo como un "Empezar selección aquí".
        val inicioUri = ultimaUriSeleccionada
        if (inicioUri == null || _fotosSeleccionadas.value.isEmpty()) {
            toggleSeleccion(fotoDestino)
            return
        }

        // Buscamos posiciones
        val indexInicio = listaMaestraFotos.indexOfFirst { it.uri == inicioUri }
        val indexFin = listaMaestraFotos.indexOfFirst { it.uri == fotoDestino.uri }

        if (indexInicio != -1 && indexFin != -1) {
            val min = min(indexInicio, indexFin)
            val max = max(indexInicio, indexFin)

            val seleccionActual = _fotosSeleccionadas.value.toMutableSet()
            // Agregamos todo el rango
            for (i in min..max) {
                seleccionActual.add(listaMaestraFotos[i].uri)
            }
            _fotosSeleccionadas.value = seleccionActual
            ultimaUriSeleccionada = fotoDestino.uri
        }
    }

    fun seleccionarTodo() {
        // Simplemente mapeamos todas las URIs al set
        _fotosSeleccionadas.value = listaMaestraFotos.map { it.uri }.toSet()
        // El ancla queda en la última foto para permitir rangos inversos si quisieras
        ultimaUriSeleccionada = listaMaestraFotos.lastOrNull()?.uri
    }
    fun limpiarSeleccion() {
        _fotosSeleccionadas.value = emptySet()
        ultimaUriSeleccionada = null
    }

    // Paso 2: Confirmar selección -> Crear Sesión DB -> Ir al Editor
    fun confirmarSeleccionYCrearSesion(nombreSesion: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val seleccion = _fotosSeleccionadas.value.toList()
            if (seleccion.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true)

                // Usamos el nombre que viene del Dialog
                val idSesion = repository.crearNuevaSesion(nombreSesion, seleccion)
                currentSesionId = idSesion

                listaMaestraFotos = repository.cargarFotosDeSesion(idSesion).toMutableList()
                indiceActual = 0

                _vistaActual.value = VistaActual.EDITOR
                actualizarFotoVisible()
            }
        }
    }

    // Botón Volver (Maneja la navegación hacia atrás)
    fun manejarVolver() {
        when (_vistaActual.value) {
            VistaActual.EDITOR -> {
                // Del Editor volvemos a la Galería
                _vistaActual.value = VistaActual.GALERIA
                _uiState.value = _uiState.value.copy(fotoActual = null)
            }
            VistaActual.GALERIA -> {
                if (_fotosSeleccionadas.value.isNotEmpty()) {
                    limpiarSeleccion()
                } else {
                    // De la Galería volvemos a elegir Carpeta
                    _vistaActual.value = VistaActual.CARPETAS
                    cargarCarpetas()
                }
            }
            VistaActual.CARPETAS -> {
                // De elegir Carpeta volvemos al Home (Dashboard)
                _vistaActual.value = VistaActual.INICIO
            }
            VistaActual.INICIO -> {
                // Estamos en la raíz. No hacemos nada aquí.
                // La UI detectará que no manejamos el evento y cerrará la app.
            }
        }
    }

    // --- LÓGICA DE EDICIÓN (EXISTENTE) ---

    fun actualizarRotacion(deltaRotacion: Float) {
        val foto = _uiState.value.fotoActual ?: return
        val nuevaRotacion = foto.rotacion + deltaRotacion
        val fotoActualizada = foto.copy(rotacion = nuevaRotacion)

        listaMaestraFotos[indiceActual] = fotoActualizada
        _uiState.value = _uiState.value.copy(fotoActual = fotoActualizada)

        viewModelScope.launch(Dispatchers.IO) {
            repository.actualizarEstadoFoto(foto.id, fotoActualizada.rotacion, fotoActualizada.decision)
        }
    }

    fun tomarDecision(decision: Decision) {
        val foto = _uiState.value.fotoActual ?: return
        val fotoDecidida = foto.copy(decision = decision)

        listaMaestraFotos[indiceActual] = fotoDecidida

        viewModelScope.launch(Dispatchers.IO) {
            repository.actualizarEstadoFoto(foto.id, fotoDecidida.rotacion, fotoDecidida.decision)
        }

        if (indiceActual < listaMaestraFotos.size - 1) {
            indiceActual++
            actualizarFotoVisible()
        } else {
            // Fin de la lista en el editor
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
            mostrandoCarpetas = false
        )
    }

    fun obtenerResumen(): String {
        val aBorrar = listaMaestraFotos.count { it.decision == Decision.ELIMINAR }
        val aEditar = listaMaestraFotos.count { it.decision == Decision.CONSERVAR && it.rotacion != 0f }
        val aIgnorar = listaMaestraFotos.count { it.decision == Decision.CONSERVAR && it.rotacion == 0f }

        return "Resumen:\n🗑️ Se borrarán: $aBorrar fotos\n🔄 Se editarán: $aEditar fotos\n✅ Se dejan igual: $aIgnorar fotos"
    }

    // --- FASES DE GUARDADO (EXISTENTE) ---

    fun ejecutarCambiosReales() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val fotosParaBorrar = listaMaestraFotos.filter { it.decision == Decision.ELIMINAR }

            if (fotosParaBorrar.isNotEmpty()) {
                val uris = fotosParaBorrar.map { it.uri }
                val intentSender = repository.generarPermisoBorrado(uris)

                if (intentSender != null) {
                    _uiState.value = _uiState.value.copy(
                        solicitudPermiso = intentSender,
                        tipoAccionPendiente = "BORRAR"
                    )
                    return@launch
                } else {
                    fotosParaBorrar.forEach { repository.eliminarFoto(it.uri) }
                }
            }
            procesarEdiciones()
        }
    }

    private fun procesarEdiciones() {
        viewModelScope.launch(Dispatchers.IO) {
            val modo = _uiState.value.modoExportacion

            // FILTRADO:
            // Si es SOBRESCRIBIR: Solo las que rotamos (para no tocar las que están bien).
            // Si es COPIAR: Todas las que decidimos CONSERVAR (para llevar el lote completo).
            val fotosAProcesar = if (modo == ModoExportacion.SOBRESCRIBIR) {
                listaMaestraFotos.filter { it.decision == Decision.CONSERVAR && it.rotacion != 0f }
            } else {
                listaMaestraFotos.filter { it.decision == Decision.CONSERVAR }
            }

            val total = fotosAProcesar.size
            if (total == 0) {
                finalizarProceso()
                return@launch
            }

            // PERMISOS: Solo necesarios para SOBRESCRIBIR (Android protege el archivo original)
            if (modo == ModoExportacion.SOBRESCRIBIR) {
                val uris = fotosAProcesar.map { it.uri }
                val intentSender = repository.generarPermisoEscritura(uris)

                if (intentSender != null) {
                    _uiState.value = _uiState.value.copy(
                        solicitudPermiso = intentSender,
                        tipoAccionPendiente = "EDITAR"
                    )
                    return@launch
                }
            }

            // PROCESAMIENTO
            var procesadas = 0
            // Si el nombre está vacío, usamos uno por defecto
            val carpetaDestino = _uiState.value.nombreCarpetaDestino.ifBlank { "FotoXPress_Export" }

            fotosAProcesar.forEach { foto ->
                procesadas++
                _uiState.value = _uiState.value.copy(
                    progreso = procesadas.toFloat() / total.toFloat(),
                    mensajeProgreso = "Procesando $procesadas de $total..."
                )

                try {
                    val bitmapOriginal = repository.cargarBitmap(foto.uri)

                    if (bitmapOriginal != null) {
                        // Rotamos si hace falta
                        val bitmapFinal = if (foto.rotacion != 0f) {
                            com.rolesencia.fotoxpress.utils.ImageUtils.rotarBitmap(bitmapOriginal, foto.rotacion)
                        } else {
                            bitmapOriginal
                        }

                        // GUARDADO
                        when (modo) {
                            ModoExportacion.SOBRESCRIBIR -> {
                                repository.sobrescribirImagen(foto.uri, bitmapFinal)
                            }
                            ModoExportacion.COPIAR -> {
                                // Usamos el nombre original para mantener orden
                                val nombreArchivo = "Foto_$procesadas"

                                repository.guardarImagenEnGaleria(
                                    bitmapFinal,
                                    nombreArchivo,
                                    carpetaDestino
                                )
                            }
                        }

                        if (bitmapFinal != bitmapOriginal) bitmapFinal.recycle()
                        bitmapOriginal.recycle()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            finalizarProceso()
        }
    }

    fun onPermisoOtorgado() {
        val accion = _uiState.value.tipoAccionPendiente
        _uiState.value = _uiState.value.copy(solicitudPermiso = null, tipoAccionPendiente = null)

        viewModelScope.launch(Dispatchers.IO) {
            if (accion == "BORRAR") {
                procesarEdiciones()
            } else if (accion == "EDITAR") {
                val fotosParaEditar = listaMaestraFotos.filter { it.decision == Decision.CONSERVAR && it.rotacion != 0f }
                fotosParaEditar.forEach { foto ->
                    aplicarEdicionConRecorte(foto)
                }
                finalizarProceso()
            }
        }
    }

    private fun finalizarProceso() {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Borramos la sesión de la DB (Limpieza)
            currentSesionId?.let { id ->
                repository.eliminarSesion(id)
            }
            currentSesionId = null

            // 2. Volvemos al Dashboard (INICIO)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                fotoActual = null,
                progreso = 0f, // Reset
                mensajeProgreso = ""
            )
            _vistaActual.value = VistaActual.INICIO
        }
    }

    private fun aplicarEdicionConRecorte(foto: FotoEstado) {
        try {
            val bitmapOriginal = repository.cargarBitmap(foto.uri) ?: return
            val bitmapEditado = ImageProcessor.aplicarEdicion(
                original = bitmapOriginal,
                grados = foto.rotacion
            )
            repository.sobrescribirImagen(foto.uri, bitmapEditado)
            if (bitmapOriginal != bitmapEditado) bitmapOriginal.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // FACTORY
    class FotoViewModelFactory(private val repository: FotoRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FotoViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return FotoViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    fun retomarSesion(sesionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // 1. Cargamos las fotos
            listaMaestraFotos = repository.cargarFotosDeSesion(sesionId).toMutableList()
            currentSesionId = sesionId

            // 2. LÓGICA DE RE-INGRESO: Buscamos la primera que sea PENDIENTE
            val primerPendiente = listaMaestraFotos.indexOfFirst { it.decision == Decision.PENDIENTE }

            // Si devuelve -1 (todo editado), vamos al principio (0). Si no, vamos a esa foto.
            indiceActual = if (primerPendiente != -1) primerPendiente else 0

            // 3. Directo al Editor
            _uiState.value = _uiState.value.copy(isLoading = false, fotoActual = null)
            actualizarFotoVisible()
            _vistaActual.value = VistaActual.EDITOR
        }
    }

    fun borrarSesion(sesionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.eliminarSesion(sesionId)
        }
    }

    fun irANuevaImportacion() {
        // Esto hace lo que hacía antes el 'init': va a elegir carpetas
        cargarCarpetas()
        _vistaActual.value = VistaActual.CARPETAS
    }

    fun onPermisoDenegado() {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            solicitudPermiso = null,
            tipoAccionPendiente = null,
            mensajeProgreso = "Permiso denegado. No se pudieron aplicar los cambios."
        )
    }

    fun pausarSesion() {
        // Simplemente nos vamos al inicio.
        // Room ya guardó todo en tiempo real, no hace falta "guardar" nada extra.
        _vistaActual.value = VistaActual.INICIO
        _uiState.value = _uiState.value.copy(fotoActual = null)
    }

    // CONTROLAR LA UI
    fun setModoExportacion(modo: ModoExportacion) {
        _uiState.value = _uiState.value.copy(modoExportacion = modo)
    }

    fun setNombreCarpetaDestino(nombre: String) {
        _uiState.value = _uiState.value.copy(nombreCarpetaDestino = nombre)
    }
}