package com.example.loginyrealdb_26_27.ui.RealTimeDB

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.loginyrealdb_26_27.ui.Login.LoginViewModel
import com.example.loginyrealdb_26_27.ui.Rutas


//https://firebase.google.com/docs/database?hl=es-419
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RealTimeDBVentana(navController: NavHostController, miViewModelRTDB: MiViewModelRealtime, loginViewModel: LoginViewModel) {
    val usuarios by miViewModelRTDB.usuarios.collectAsState()
    val codigoError by miViewModelRTDB.codigoError.collectAsState()
    var mostrar by remember { mutableStateOf(true) }
    var usuarioSeleccionado by remember { mutableStateOf<Usuario?>(null) }
    var mostrarDialogoBorrar by remember { mutableStateOf(false) }


    Column(modifier = Modifier.padding(vertical = 20.dp).systemBarsPadding()) {
        Button(
            onClick = {
                mostrar = !mostrar
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (mostrar) {
                Text("Ocultar")
            } else {
                Text("Mostrar")
            }
        }
        if (mostrar) {
            Text(
                text = "Usuarios Registrados",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (codigoError == 2) {
                Text(
                    text = "Lista vacía",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary)
                        .padding(8.dp)
                ) {
                    items(usuarios) { usuario ->
                        Text(
                            text = "Nombre: ${usuario.nombre}, Apellido: ${usuario.apellido}, Edad: ${usuario.edad}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                                .combinedClickable(
                                    onClick = { //Para mostrar infomación.
                                        usuarioSeleccionado = usuario
                                    },
                                    onLongClick = { //Para borrar el usuario seleccionnado.
                                        usuarioSeleccionado = usuario
                                        mostrarDialogoBorrar = true
                                    }
                                )
                        )
                    }
                }
            }
        }

        //Para mostrar información del usuario seleccionado.
        usuarioSeleccionado?.let { usuario ->
            Text(
                text = "Usuario seleccionado: ${usuario.nombre} ${usuario.apellido}, ${usuario.edad} años",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        //Muestra el AlertDialog para preguntar si queremos borrar el usuario seleccionado.
        if (mostrarDialogoBorrar) {
            AlertDialog(
                onDismissRequest = { mostrarDialogoBorrar = false },
                title = { Text("Confirmar borrado") },
                text = { Text("¿Deseas borrar a ${usuarioSeleccionado?.nombre}?") },
                confirmButton = {
                    Button(onClick = {
                        usuarioSeleccionado?.id?.let { id ->
                            miViewModelRTDB.borrarUsuarioPorId(id)
                        }
                        mostrarDialogoBorrar = false
                        usuarioSeleccionado = null
                    }) {
                        Text("Sí")
                    }
                },
                dismissButton = {
                    Button(onClick = { mostrarDialogoBorrar = false }) {
                        Text("No")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        //Botón para agregar un usuario aleatorio, llamando a la función agregarUsuario del miViewModelRTDB.
        Button(
            onClick = {
                miViewModelRTDB.agregarUsuario(Factoria.getUsuario())
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Agregar usuario aleatorio")
        }
        Spacer(modifier = Modifier.height(16.dp))

        //Botón para borrar menores de edad.

        Button(
            onClick = {
                miViewModelRTDB.borrarUsuariosMenoresDeEdad2()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Borrar menores de edad")
        }
        Spacer(modifier = Modifier.height(16.dp))

        //Botón para cerrar sesión.
        Button(
            onClick = {
                //loginViewModel.signOut()
                navController.popBackStack()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Volver")
        }
    }

}
