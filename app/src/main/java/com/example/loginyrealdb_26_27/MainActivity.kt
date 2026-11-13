package com.example.loginyrealdb_26_27

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.loginyrealdb_26_27.ui.Chat.ChatScreen
import com.example.loginyrealdb_26_27.ui.Chat.ChatViewModel
import com.example.loginyrealdb_26_27.ui.Chat.ChatViewModel_FireStore
import com.example.loginyrealdb_26_27.ui.RealTimeDB.MiViewModelRealtime
import com.example.loginyrealdb_26_27.ui.RealTimeDB.RealTimeDBVentana
import com.example.loginyrealdb_26_27.ui.Home.HomeScreen
import com.example.loginyrealdb_26_27.ui.Login.LoginScreen
import com.example.loginyrealdb_26_27.ui.Login.LoginViewModel
import com.example.loginyrealdb_26_27.ui.Rutas
import com.example.loginyrealdb_26_27.ui.theme.LoginYRealDB_26_27Theme

class MainActivity : ComponentActivity() {
    private val viewModel = LoginViewModel()
    val miViewModelRTDB = MiViewModelRealtime()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LoginYRealDB_26_27Theme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = Rutas.login) {
                    composable(Rutas.login) {
                        LoginScreen(navController = navController, loginViewModel = viewModel)
                    }
                    composable(Rutas.home) {
                        HomeScreen(navController = navController, logginViewModel = viewModel)
                    }
                    composable(Rutas.rtdb) {
                        RealTimeDBVentana(navController = navController, miViewModelRTDB = miViewModelRTDB, loginViewModel = viewModel)
                    }
                    composable(Rutas.chat) {
                        val miChatVM_Firestore: ChatViewModel_FireStore = viewModel()
                        val miChatVM: ChatViewModel = viewModel()
                        ChatScreen(navController = navController, viewModel = miChatVM,  loginViewModel = viewModel)
                        //ChatScreen(navController = navController, viewModel = miChatVM_Firestore,  loginViewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LoginYRealDB_26_27Theme {
        Greeting("Android")
    }
}
