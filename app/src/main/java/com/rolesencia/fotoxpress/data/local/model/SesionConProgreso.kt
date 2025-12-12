package com.rolesencia.fotoxpress.data.local.model

import androidx.room.Embedded
import com.rolesencia.fotoxpress.data.local.entity.SesionEntity

data class SesionConProgreso(
    @Embedded val sesion: SesionEntity,
    val fotosEditadas: Int
)