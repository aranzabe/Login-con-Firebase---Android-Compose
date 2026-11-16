package com.example.loginyrealdb_26_27.ui.Chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.loginyrealdb_26_27.ui.Login.LoginViewModel
import com.example.loginyrealdb_26_27.ui.Rutas

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    //viewModel: ChatViewModel_FireStore,
    navController: NavHostController,
    loginViewModel: LoginViewModel
) {
    val TAG = "Fernando"
    val contexto = LocalContext.current
    val mensajesUI by viewModel.mensajes.collectAsState(initial = emptyList())
    var inputMessage by remember { mutableStateOf("") }

    val listState = rememberLazyListState() //Estado del scroll para desplazar los mensajes y que el último esté siempre abajo.

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

//        Text("Número de mensajes: ${messages.size}")  // Agregado para depuración
//        for (message in messages) {
//            Text(message.toString()) // Mostrar cada mensaje como texto
//        }

        //Desplazar al primer mensaje cuando la lista cambie.
        LaunchedEffect(mensajesUI) {
            if (mensajesUI.isNotEmpty()) {
                listState.animateScrollToItem(0)
            }
        }

        //Cuando la lista de mensjes cambia se carga de nuevo la LazyColumn (Recyclerview).
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp),
            reverseLayout = true,
            state = listState //Asignar estado del scroll
        ) {
            items(mensajesUI) { mens ->
                ChatMessageItem(mens, loginViewModel)
                //Log.e(TAG,message.toString())
            }
        }

        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputMessage,
                onValueChange = { inputMessage = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe tu mensaje") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (inputMessage.isNotBlank()) {
                    viewModel.sendMessage(loginViewModel.getCurrentUser()!!.email.toString(), inputMessage)
                    inputMessage = ""
                }
            }) {
                Text("Enviar")
            }
        }
        //Spacer(modifier = Modifier.height(16.dp))

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

@Composable
fun ChatMessageItem(mens: Mensaje, loginViewModel: LoginViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        horizontalArrangement = if (mens.sender == loginViewModel.getCurrentUser()!!.email) Arrangement.End else Arrangement.Start
        //Con esto hacemos que los mensajes propios estén en un lado y los del otro en el otro lado (como hace ws o Telegram).
    ) {
        Column(
            modifier = Modifier
                .background(
                    if (mens.sender == loginViewModel.getCurrentUser()!!.email.toString()) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            Text(
                text = mens.sender,
                style = MaterialTheme.typography.labelSmall,
//                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = mens.text,
                style = MaterialTheme.typography.bodyLarge,
//                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
