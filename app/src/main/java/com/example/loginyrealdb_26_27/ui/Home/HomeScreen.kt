package com.example.loginyrealdb_26_27.ui.Home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.loginyrealdb_26_27.ui.Login.LoginViewModel
import com.example.loginyrealdb_26_27.ui.Rutas


@Composable
fun HomeScreen(navController: NavHostController, logginViewModel: LoginViewModel) {
    val user = logginViewModel.getCurrentUser()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Bienvenido a Home", style = MaterialTheme.typography.titleLarge)

        user?.let {
            Text(
                text = "Usuario:, ${it.displayName ?: it.email ?: "Usuario"}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Correo: ${it.email ?: "No disponible"}",
                style = MaterialTheme.typography.bodyMedium
            )
        } ?:
        Text(
            text = "No se encontró información del usuario",
            style = MaterialTheme.typography.bodyMedium
        )


        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                logginViewModel.signOut()
                navController.navigate(Rutas.login) {
                    popUpTo(Rutas.login) { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar sesión")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                navController.navigate(Rutas.rtdb) {
                    popUpTo(Rutas.rtdb) { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Real Time Database")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                navController.navigate(Rutas.chat) {
                    popUpTo(Rutas.chat) { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ir al chat")
        }
    }
}