package com.example.loginyrealdb_26_27.ui.RealTimeDB

import com.example.loginyrealdb_26_27.ui.Colecciones
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class UsuarioRepository {
    private val usuariosReference = FirebaseDatabase.getInstance()
        .getReference(Colecciones.usuarios)

    val usuarios: Flow<Result<List<Usuario>>> = callbackFlow {
        val listener = object : ValueEventListener {

            // Este método se ejecuta automáticamente CADA VEZ que los datos
            // del nodo "usuarios" cambian en Firebase (se añade, se borra o
            // se modifica algún usuario).
            override fun onDataChange(snapshot: DataSnapshot) {
                val listaDeUsuarios = snapshot.children.mapNotNull { child ->
                    child.getValue(Usuario::class.java)?.let { usuarioSinId ->
                        usuarioSinId.copy(id = child.key)
                    }
                }

                trySend(Result.success(listaDeUsuarios))
            }

            // Este método se ejecuta si Firebase no nos deja leer los datos
            // (por ejemplo, por falta de permisos o de conexión).
            override fun onCancelled(error: DatabaseError) {
                trySend(Result.failure(error.toException()))
                close(error.toException())
            }
        }

        usuariosReference.addValueEventListener(listener)
        awaitClose { usuariosReference.removeEventListener(listener) }
    }

    fun agregar(usuario: Usuario): Task<Void> {
        val reference = usuariosReference.push()
        val usuarioConId = usuario.copy(id = reference.key)
        return reference.setValue(usuarioConId)
    }

    fun borrarPorNombre(nombre: String): Task<DataSnapshot> =
        usuariosReference.orderByChild("nombre")
            .equalTo(nombre)
            .get()

    fun borrarPorId(usuarioId: String): Task<Void> =
        usuariosReference.child(usuarioId).removeValue()

    fun usuariosMenoresDeEdad(edadMaxima: Double): Task<DataSnapshot> =
        usuariosReference.orderByChild("edad")
            .endAt(edadMaxima)
            .get()

    fun eliminarSnapshot(snapshot: DataSnapshot) {
        snapshot.children.forEach { it.ref.removeValue() }
    }
}
