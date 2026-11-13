package com.example.loginyrealdb_26_27.ui.Login

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.loginyrealdb_26_27.ui.Rutas

@Composable
fun LoginScreen(
    navController: NavHostController,
    loginViewModel: LoginViewModel
) {
    val context = LocalContext.current
    val isLoading by loginViewModel.isLoading.collectAsState()
    val loginSuccess by loginViewModel.loginSuccess.collectAsState()
    val errorMessage by loginViewModel.errorMessage.collectAsState()
    val googleToastMessage by loginViewModel.googleToastMessage.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isRegistering) "Crear cuenta" else "Iniciar sesion",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(24.dp))

        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electronico") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contrasena (minimo 6 caracteres)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (isRegistering) {
                    loginViewModel.registerWithEmail(email.trim(), password)
                } else {
                    loginViewModel.loginWithEmail(email.trim(), password)
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRegistering) "Registrarme con correo" else "Entrar con correo")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { loginViewModel.launchGoogle(context, isRegistering) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRegistering) "Registrarme con Google" else "Entrar con Google")
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { isRegistering = !isRegistering }) {
            Text(
                if (isRegistering) "Ya tengo cuenta: iniciar sesion"
                else "No tengo cuenta: registrarme"
            )
        }

        if (isLoading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }
        errorMessage?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }

    LaunchedEffect(loginSuccess) {
        if (loginSuccess) {
            Toast.makeText(context, "Autenticacion correcta", Toast.LENGTH_SHORT).show()
            navController.navigate(Rutas.home) {
                popUpTo(Rutas.login) { inclusive = true }
            }
        }
    }

    LaunchedEffect(googleToastMessage) {
        googleToastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            loginViewModel.consumeGoogleToastMessage()
        }
    }
}
