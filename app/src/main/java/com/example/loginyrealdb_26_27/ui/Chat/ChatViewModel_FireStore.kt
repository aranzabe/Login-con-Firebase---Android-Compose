package com.example.loginyrealdb_26_27.ui.Chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loginyrealdb_26_27.ui.Colecciones
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Variante del chat almacenada en Firestore, tambien reactiva. */
class ChatViewModel_FireStore : ViewModel() {
    private val TAG="Fernando"
    private val mensajesCollection = FirebaseFirestore.getInstance()
        .collection(Colecciones.mensajes)

    private val _mensajes = MutableStateFlow<List<Mensaje>>(emptyList())
    val mensajes = _mensajes.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null

    init {
        listenerRegistration = mensajesCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Log.e(TAG, "Error al escuchar mensajes de Firestore", exception)
                    _errorMessage.value = exception.message
                    return@addSnapshotListener
                }

                _mensajes.value = snapshot?.documents
                    ?.mapNotNull { it.toObject(Mensaje::class.java) }
                    ?: emptyList()
            }
    }

    fun sendMessage(sender: String, text: String) {
        val mensaje = Mensaje(
            id = mensajesCollection.document().id,
            sender = sender,
            text = text
        )

        mensajesCollection.document(mensaje.id)
            .set(mensaje)
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al enviar el mensaje", exception)
                _errorMessage.value = exception.message
            }
    }

    override fun onCleared() {
        listenerRegistration?.remove()
        listenerRegistration = null
        super.onCleared()
    }
}
