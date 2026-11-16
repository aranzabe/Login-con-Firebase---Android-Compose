package com.example.loginyrealdb_26_27.ui.Chat

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow

/** Chat almacenado en Firebase Realtime Database. */
class ChatViewModel(
    private val repository: MensajeRepository = MensajeRepository()
) : ViewModel() {
    val mensajes: Flow<List<Mensaje>> = repository.mensajes

    fun sendMessage(sender: String, text: String) {
        repository.enviar(sender, text).addOnFailureListener { error ->
            Log.w("ChatViewModel", "Error al enviar el mensaje", error)
        }
    }
}
