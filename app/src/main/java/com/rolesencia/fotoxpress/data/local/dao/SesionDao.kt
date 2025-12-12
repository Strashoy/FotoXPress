package com.rolesencia.fotoxpress.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.rolesencia.fotoxpress.data.local.entity.FotoEntity
import com.rolesencia.fotoxpress.data.local.entity.SesionEntity
import com.rolesencia.fotoxpress.data.local.model.SesionConProgreso
import kotlinx.coroutines.flow.Flow

@Dao
interface SesionDao {
    // --- SESIONES ---

    @Query("SELECT s.*, (SELECT COUNT(*) FROM fotos_sesion f WHERE f.sesionId = s.id AND f.decision != 'PENDIENTE') as fotosEditadas FROM sesiones s ORDER BY s.fechaCreacion DESC")
    fun obtenerSesionesConProgreso(): Flow<List<SesionConProgreso>>

    @Insert
    suspend fun crearSesion(sesion: SesionEntity): Long // Devuelve el ID de la nueva sesión

    @Query("DELETE FROM sesiones WHERE id = :sesionId")
    suspend fun eliminarSesion(sesionId: Long)

    // --- FOTOS DE UNA SESIÓN ---
    @Query("SELECT * FROM fotos_sesion WHERE sesionId = :sesionId ORDER BY orden ASC")
    suspend fun obtenerFotosDeSesion(sesionId: Long): List<FotoEntity>

    @Insert
    suspend fun insertarFotos(fotos: List<FotoEntity>)

    // --- ENTRETIEMPO (Actualizaciones puntuales) ---
    @Query("UPDATE fotos_sesion SET rotacion = :nuevaRotacion WHERE id = :fotoId")
    suspend fun actualizarRotacion(fotoId: Long, nuevaRotacion: Float)

    @Query("UPDATE fotos_sesion SET decision = :nuevaDecision WHERE id = :fotoId")
    suspend fun actualizarDecision(fotoId: Long, nuevaDecision: String)

    // Obtenemos solo las URIs para chequeo rápido (Set de Strings)
    @Query("SELECT uriString FROM fotos_sesion")
    suspend fun obtenerTodasLasUrisUsadas(): List<String>
}