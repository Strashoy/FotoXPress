package com.rolesencia.fotoxpress.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.rolesencia.fotoxpress.data.local.entity.FotoEntity
import com.rolesencia.fotoxpress.data.local.entity.SesionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SesionDao {
    // --- SESIONES ---
    @Query("SELECT * FROM sesiones ORDER BY fechaCreacion DESC")
    fun obtenerTodasLasSesiones(): Flow<List<SesionEntity>> // Flow avisa a la UI si hay cambios

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
}