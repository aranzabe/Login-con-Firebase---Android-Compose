package com.example.loginyrealdb_26_27.ui.RealTimeDB

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MiViewModelRealtime(
    private val repository: UsuarioRepository = UsuarioRepository()
) : ViewModel() {
    private val TAG = "Fernando"

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
            repository.usuarios.collectLatest { resultado ->
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
        repository.agregar(usuario).addOnFailureListener { error ->
            Log.w(TAG, "Error al agregar usuario", error)
            _errorMessage.value = error.message
        }
    }

    fun borrarUsuario(usuario: Usuario) {
        repository.borrarPorNombre(usuario.nombre)
            .addOnSuccessListener { snapshot -> repository.eliminarSnapshot(snapshot) }
            .addOnFailureListener { error ->
                Log.w(TAG, "Error al borrar usuario", error)
                _errorMessage.value = error.message
            }
    }

    fun borrarUsuarioPorId(usuarioId: String) {
        repository.borrarPorId(usuarioId).addOnFailureListener { error ->
            Log.w(TAG, "Error al borrar usuario", error)
            _errorMessage.value = error.message
        }
    }

    fun borrarUsuariosMenoresDeEdad2() {
        _usuariosBorrados.value = false
        repository.usuariosMenoresDeEdad(17.0)
            .addOnSuccessListener { snapshot ->
                _usuariosBorrados.value = snapshot.exists()
                repository.eliminarSnapshot(snapshot)
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Error al borrar menores", error)
                _errorMessage.value = error.message
            }
    }
}
