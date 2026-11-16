package com.example.loginyrealdb_26_27.ui.Chat

import com.example.loginyrealdb_26_27.ui.Colecciones
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Repositorio del chat almacenado en Firestore. */
class MensajeFirestoreRepository {
    private val mensajesCollection = FirebaseFirestore.getInstance()
        .collection(Colecciones.mensajes)

    val mensajes: Flow<List<Mensaje>> = callbackFlow {
        val registration = mensajesCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val mensajes = snapshot?.documents.orEmpty()
                    .mapNotNull { it.toObject(Mensaje::class.java) }
                trySend(mensajes)
            }

        awaitClose { registration.remove() }
    }

    fun enviar(sender: String, text: String): Task<Void> {
        val mensaje = Mensaje(
            id = mensajesCollection.document().id,
            sender = sender,
            text = text
        )

        return mensajesCollection.document(mensaje.id).set(mensaje)
    }
}
