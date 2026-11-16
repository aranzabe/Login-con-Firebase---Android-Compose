package com.example.loginyrealdb_26_27.ui.Chat

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow

/** Variante del chat almacenada en Firestore, también reactiva. */
class ChatViewModel_FireStore(
    private val repository: MensajeFirestoreRepository = MensajeFirestoreRepository()
) : ViewModel() {
    val mensajes: Flow<List<Mensaje>> = repository.mensajes

    fun sendMessage(sender: String, text: String) {
        repository.enviar(sender, text).addOnFailureListener { error ->
            Log.w("ChatViewModel_FireStore", "Error al enviar el mensaje", error)
        }
    }
}
