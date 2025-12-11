package com.rolesencia.fotoxpress

import android.app.Application
import com.rolesencia.fotoxpress.data.local.AppDatabase

class FotoXPressApp : Application() {
    // La base de datos se inicializa de forma perezosa (lazy) la primera vez que se necesita
    val database by lazy { AppDatabase.getDatabase(this) }
}