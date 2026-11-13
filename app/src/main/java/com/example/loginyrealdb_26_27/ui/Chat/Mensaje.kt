package com.example.loginyrealdb_26_27.ui.Chat


data class Mensaje(
    val id: String = "",
    val sender: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
