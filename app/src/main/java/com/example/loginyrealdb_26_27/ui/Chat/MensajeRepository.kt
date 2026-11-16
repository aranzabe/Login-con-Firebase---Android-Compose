package com.example.loginyrealdb_26_27.ui.Chat

import com.example.loginyrealdb_26_27.ui.Colecciones
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Repositorio del chat almacenado en Firebase Realtime Database. */
class MensajeRepository {
    private val mensajesReference = FirebaseDatabase.getInstance()
        .getReference(Colecciones.mensajes)

    val mensajes: Flow<List<Mensaje>> = callbackFlow {
        val query = mensajesReference
            .orderByChild("timestamp")
            .limitToLast(50)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = snapshot.children
                    .mapNotNull { it.getValue(Mensaje::class.java) }
                    .sortedByDescending { it.timestamp }
                trySend(lista)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    fun enviar(sender: String, text: String): Task<Void> {
        val id = mensajesReference.push().key
            ?: return Tasks.forException(IllegalStateException("No se pudo generar el identificador del mensaje."))

        return mensajesReference.child(id)
            .setValue(
                mapOf(
                    "id" to id,
                    "sender" to sender,
                    "text" to text,
                    "timestamp" to ServerValue.TIMESTAMP
                )
            )
    }
}
