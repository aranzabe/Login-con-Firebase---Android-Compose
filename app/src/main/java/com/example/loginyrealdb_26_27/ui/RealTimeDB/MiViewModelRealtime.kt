package com.example.loginyrealdb_26_27.ui.RealTimeDB

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loginyrealdb_26_27.ui.Colecciones
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MiViewModelRealtime : ViewModel() {
    private val TAG = "Fernando"
    private val usuariosReference = FirebaseDatabase.getInstance()
        .getReference(Colecciones.usuarios)

    private val _usuarios = MutableStateFlow<List<Usuario>>(emptyList())
    val usuarios = _usuarios.asStateFlow()

    private val _codigoError = MutableStateFlow(0)
    val codigoError = _codigoError.asStateFlow()

    private val _usuariosBorrados = MutableStateFlow(false)
    val usuariosBorrados = _usuariosBorrados.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            usuariosReference.usuariosFlow().collectLatest { resultado ->
                resultado
                    .onSuccess { lista ->
                        _usuarios.value = lista
                        _codigoError.value = if (lista.isEmpty()) 2 else 0
                    }
                    .onFailure { exception ->
                        Log.e(TAG, "Error al escuchar usuarios", exception)
                        _codigoError.value = 1
                        _errorMessage.value = exception.message
                    }
            }
        }
    }

    fun agregarUsuario(usuario: Usuario) {
        val reference = usuariosReference.push()
        val usuarioConId = usuario.copy(id = reference.key)

        reference.setValue(usuarioConId)
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al agregar usuario", exception)
                _errorMessage.value = exception.message
            }
    }

    fun borrarUsuario(usuario: Usuario) {
        usuariosReference.orderByChild("nombre")
            .equalTo(usuario.nombre)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.children.forEach { it.ref.removeValue() }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al borrar usuario", exception)
                _errorMessage.value = exception.message
            }
    }

    fun borrarUsuarioPorId(usuarioId: String) {
        usuariosReference.child(usuarioId)
            .removeValue()
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al borrar usuario", exception)
                _errorMessage.value = exception.message
            }
    }

    fun borrarUsuariosMenoresDeEdad2() {
        _usuariosBorrados.value = false
        usuariosReference.orderByChild("edad")
            .endAt(17.0)
            .get()
            .addOnSuccessListener { snapshot ->
                _usuariosBorrados.value = snapshot.exists()
                snapshot.children.forEach { child ->
                    child.key?.let { usuariosReference.child(it).removeValue() }
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al borrar menores", exception)
                _errorMessage.value = exception.message
            }
    }

    private fun DatabaseReference.usuariosFlow() = callbackFlow {
        val listener = object : ValueEventListener {

            // Este método se ejecuta automáticamente CADA VEZ que los datos
            // del nodo "usuarios" cambian en Firebase (se añade, se borra o
            // se modifica algún usuario).
            override fun onDataChange(snapshot: DataSnapshot) {

                // PASO 1: Creamos una lista vacía y mutable donde iremos
                // añadiendo los usuarios que encontremos dentro del snapshot.
                val listaDeUsuarios = mutableListOf<Usuario>()

                // PASO 2: El "snapshot" que recibimos es como una "foto" de
                // TODO el nodo "usuarios". Dentro de ese snapshot, cada
                // "child" (hijo) es, a su vez, un snapshot de UN SOLO usuario.
                //
                // Ejemplo de estructura en Firebase:
                // usuarios
                //   ├── -Nabc123   <- child.key (clave generada por Firebase)
                //   │     ├── nombre: "Ana"
                //   │     ├── apellido: "García"
                //   │     └── edad: 20
                //   └── -Nxyz456
                //         ├── nombre: "Luis"
                //         ├── apellido: "Pérez"
                //         └── edad: 17
                //
                // Recorremos uno a uno todos esos hijos con un bucle for.
                for (child in snapshot.children) {

                    // PASO 3: Intentamos convertir el contenido del hijo
                    // (nombre, apellido, edad...) en un objeto Usuario.
                    // getValue() puede devolver null si los datos no
                    // coinciden con la clase Usuario, por eso usamos "?".
                    val usuarioSinId: Usuario? = child.getValue(Usuario::class.java)

                    // PASO 4: Si la conversión ha funcionado (no es null)...
                    if (usuarioSinId != null) {

                        // PASO 5: El campo "id" NO viene dentro de los datos
                        // del usuario, es la CLAVE del propio nodo en
                        // Firebase (child.key). Por eso creamos una copia
                        // del usuario añadiéndole ese id manualmente.
                        val idDelUsuario: String? = child.key
                        val usuarioConId: Usuario = usuarioSinId.copy(id = idDelUsuario)

                        // PASO 6: Añadimos el usuario ya completo (con su id)
                        // a la lista que iremos devolviendo.
                        listaDeUsuarios.add(usuarioConId)
                    }
                    // Si usuarioSinId fuese null, simplemente ignoramos ese
                    // hijo y seguimos con el siguiente (un dato corrupto no
                    // debe romper toda la lista).
                }

                // PASO 7: Una vez recorridos TODOS los hijos del snapshot,
                // enviamos la lista completa de usuarios envuelta en un
                // Result.success, para indicar que todo ha ido bien.
                trySend(Result.success(listaDeUsuarios))
            }

            // Este método se ejecuta si Firebase no nos deja leer los datos
            // (por ejemplo, por falta de permisos o de conexión).
            override fun onCancelled(error: DatabaseError) {
                // Enviamos un Result.failure con el error, para que quien
                // esté escuchando este Flow sepa que algo ha fallado.
                trySend(Result.failure(error.toException()))
                close(error.toException())
            }
        }

        // Nos suscribimos a los cambios del nodo "usuarios".
        addValueEventListener(listener)

        // Cuando el Flow se cancele (por ejemplo, al destruirse el
        // ViewModel), nos damos de baja del listener para no seguir
        // escuchando cambios innecesariamente.
        awaitClose { removeEventListener(listener) }
    }
}
