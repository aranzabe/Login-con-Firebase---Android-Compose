package com.example.loginyrealdb_26_27.ui.Login

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loginyrealdb_26_27.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Un unico ViewModel para el estado y las operaciones de Firebase Authentication.
 *
 * La UI observa los StateFlow publicos, pero solo este ViewModel puede cambiar
 * los MutableStateFlow privados.
 */
class LoginViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess = _loginSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    // Mensajes puntuales (Toast) que solo tienen sentido durante el flujo de
    // Google: cancelacion del usuario, fallo del selector o token invalido.
    // La UI los observa, los muestra y luego llama a consumeGoogleToastMessage().
    private val _googleToastMessage = MutableStateFlow<String?>(null)
    val googleToastMessage = _googleToastMessage.asStateFlow()

    val isUserLoggedIn: Boolean
        get() = auth.currentUser != null

    fun loginWithEmail(email: String, password: String) {
        prepareRequest()
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                finishRequest(task.isSuccessful, task.exception)
            }
    }

    fun registerWithEmail(email: String, password: String) {
        prepareRequest()
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                finishRequest(task.isSuccessful, task.exception)
            }
    }

    /** Recibe el ID token que Credential Manager obtiene de Google. */
    fun loginWithGoogle(idToken: String) {
        if (idToken.isBlank()) {
            _errorMessage.value = "Google no devolvio un identificador valido."
            return
        }

        prepareRequest()
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                finishRequest(task.isSuccessful, task.exception)
            }
    }

    /**
     * Lanza el selector de cuentas de Google via Credential Manager y, si hay
     * exito, delega en [loginWithGoogle]. El [context] solo se usa dentro de
     * esta corrutina (no se guarda como campo) para evitar fugas de memoria.
     *
     * @param isRegistering si true, fuerza el selector explicito de Google
     * (util para el alta, ya que no depende de credenciales ya autorizadas).
     */
    fun launchGoogle(context: Context, isRegistering: Boolean) {
        viewModelScope.launch {
            try {
                val googleClientId = context.getString(R.string.default_web_client_id)
                val credentialManager = CredentialManager.create(context)

                val googleOption = if (isRegistering) {
                    GetSignInWithGoogleOption.Builder(googleClientId)
                        .build()
                } else {
                    GetGoogleIdOption.Builder()
                        .setServerClientId(googleClientId)
                        .setFilterByAuthorizedAccounts(false)
                        .setAutoSelectEnabled(false)
                        .build()
                }

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleOption)
                    .build()

                val result = credentialManager.getCredential(context, request)
                val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
                loginWithGoogle(credential.idToken)
            } catch (_: GetCredentialCancellationException) {
                _googleToastMessage.value = "Inicio con Google cancelado por el usuario"
            } catch (exception: GetCredentialException) {
                // Esta excepcion también se produce si Google no está configurado
                // para el paquete o para el certificado SHA de esta aplicacion.
                Log.e("LoginViewModel", "Credential Manager no pudo obtener la cuenta", exception)
                val detail = exception.message ?: "La cuenta no pudo seleccionarse."
                _googleToastMessage.value = "Google no pudo iniciar sesion: $detail"
            } catch (exception: GoogleIdTokenParsingException) {
                Log.e("LoginViewModel", "No se pudo interpretar el token de Google", exception)
                _googleToastMessage.value = "La respuesta de Google no es valida"
            } catch (exception: Exception) {
                // Red de seguridad: algunos fallos internos de Play Services
                // (p. ej. "Unknown calling package name") no llegan como
                // GetCredentialException y, si no se capturan aquí, tiran la app.
                Log.e("LoginViewModel", "Fallo inesperado durante el login con Google", exception)
                _googleToastMessage.value = "No se pudo iniciar sesion con Google. Intentalo de nuevo."
            }
        }
    }

    /** La UI llama a esto justo despues de mostrar el Toast correspondiente. */
    fun consumeGoogleToastMessage() {
        _googleToastMessage.value = null
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun signOut() {
        auth.signOut()
        _loginSuccess.value = false
        _errorMessage.value = null
    }

    private fun prepareRequest() {
        _isLoading.value = true
        _loginSuccess.value = false
        _errorMessage.value = null
    }

    private fun finishRequest(success: Boolean, exception: Exception?) {
        _isLoading.value = false
        _loginSuccess.value = success
        if (!success) {
            _errorMessage.value = exception?.message
                ?: "No se pudo completar la operacion."
        }
    }
}
