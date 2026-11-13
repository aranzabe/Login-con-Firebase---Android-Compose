package com.example.loginyrealdb_26_27.ui.RealTimeDB

object Factoria {
    private val nombres = listOf("Iván", "Giovanny", "Ismael", "Daniel", "Gabriel", "Jorge", "Gregory","Borja","David","Juan M","Adán","Álvaro","Juan C")
    private val apellidos = listOf("Potter", "Skywalker", "Bolson", "Sith", "Jedi")

    fun getUsuario(): Usuario {
        val nombre = nombres.random()
        val apellido = apellidos.random()
        val edad = (1..40).random()
        return Usuario(nombre, apellido, edad)
    }
}
