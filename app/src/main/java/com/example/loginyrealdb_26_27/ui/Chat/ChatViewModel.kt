package com.example.loginyrealdb_26_27.ui.Chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loginyrealdb_26_27.ui.Colecciones
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Chat almacenado en Firebase Realtime Database. */
class ChatViewModel : ViewModel() {
    private val TAG = "Fernando"
    private val mensajesReference = FirebaseDatabase.getInstance()
        .getReference(Colecciones.mensajes)

    private val _mensajes = MutableStateFlow<List<Mensaje>>(emptyList())
    val mensajes = _mensajes.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val mensajesFlow = mensajesReference
        .orderByChild("timestamp")
        .limitToLast(50)
        .mensajesFlow()

    init {
        viewModelScope.launch {
            mensajesFlow.collectLatest { lista ->
                _mensajes.value = lista
            }
        }
    }

    fun sendMessage(sender: String, text: String) {
        val id = mensajesReference.push().key
        if (id == null) {
            _errorMessage.value = "No se pudo generar el identificador del mensaje."
            return
        }

        mensajesReference.child(id)
            .setValue(
                mapOf(
                    "id" to id,
                    "sender" to sender,
                    "text" to text,
                    "timestamp" to ServerValue.TIMESTAMP
                )
            )
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al enviar el mensaje", exception)
                _errorMessage.value = exception.message
            }
    }

    private fun Query.mensajesFlow() = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = snapshot.children
                    .mapNotNull { it.getValue(Mensaje::class.java) }
                    .sortedByDescending { it.timestamp }
                trySend(lista)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error al escuchar mensajes", error.toException())
                close(error.toException())
            }
        }

        addValueEventListener(listener)
        awaitClose { removeEventListener(listener) }
    }
}
