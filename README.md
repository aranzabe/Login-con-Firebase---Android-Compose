# Login con Firebase: Email/Contraseña y Google

Apuntes paso a paso para montar, desde cero, una pantalla de login/registro en
Jetpack Compose usando **Firebase Authentication** con dos proveedores:

1. Email y contraseña.
2. Google (con **Credential Manager**, la API moderna que sustituye a
   `GoogleSignInClient`).

El objetivo es entender **qué hay que configurar** (consola de Firebase,
Gradle, manifest...) y **qué código hay que escribir** y por qué, siguiendo
el patrón MVVM: el `ViewModel` hace el trabajo, la pantalla (`Composable`)
solo observa estado y dibuja.

---

## 1. Arquitectura general

```
LoginScreen (Composable)          LoginViewModel
┌─────────────────────┐           ┌───────────────────────────┐
│ observa StateFlow    │ <──────── │ isLoading                 │
│ dibuja UI            │           │ loginSuccess               │
│ llama a funciones    │ ────────> │ errorMessage               │
│ del ViewModel        │           │ googleToastMessage         │
└─────────────────────┘           │                            │
                                   │ loginWithEmail()           │
                                   │ registerWithEmail()        │
                                   │ launchGoogle()             │
                                   │ loginWithGoogle()          │
                                   │ signOut()                  │
                                   └───────────────────────────┘
```

Reglas de oro:

- La UI **nunca** llama directamente a Firebase ni a Credential Manager.
- El ViewModel **nunca** guarda una referencia a largo plazo de un `Context`
  de Activity (evita fugas de memoria). Si necesita un `Context` puntual
  (como para Credential Manager), se lo pasamos como parámetro de la función.
- Todo lo que la UI necesita mostrar (carga, error, éxito, toasts) vive en
  `StateFlow` expuestos como de solo lectura (`val x = _x.asStateFlow()`).

---

## 2. Configuración en Firebase Console

### 2.1 Crear el proyecto y añadir la app Android

1. Entra en [Firebase Console](https://console.firebase.google.com/) y crea
   un proyecto (o usa uno existente).
2. Añade una app Android con el mismo `applicationId` que el
   `namespace`/`applicationId` del `build.gradle.kts` del módulo `app`
   (en este proyecto: `com.example.loginyrealdb_26_27`).
3. Para que **Google Sign-In** funcione hace falta además la huella SHA-1
   (y recomendable SHA-256) del certificado de firma de depuración:

   ```bash
   ./gradlew signingReport
   ```

   Copia el SHA-1 del `variant: debug` y pégalo en **Configuración del
   proyecto > Tus apps > Añadir huella digital** en Firebase.

4. Descarga el fichero `google-services.json` generado y colócalo en
   `app/google-services.json` (ya está en este repo, pero si lo clonas de
   cero recuerda que **no debe subirse a repos públicos** con datos reales).

### 2.2 Activar los métodos de inicio de sesión

En **Authentication > Sign-in method**, activa:

- **Correo electrónico/contraseña**.
- **Google** (te pedirá un correo de soporte del proyecto; Firebase generará
  automáticamente un "Web client ID" que es el que usaremos en el código).

### 2.3 Obtener el `default_web_client_id`

Cuando activas Google Sign-In y tienes el `google-services.json` correcto,
el plugin de Gradle `google-services` genera automáticamente el recurso:

```xml
<string name="default_web_client_id">XXXXXXX.apps.googleusercontent.com</string>
```

Lo encontrarás (generado, no lo escribas a mano) en:
`app/build/generated/res/.../values/values.xml` tras compilar.
Lo usaremos desde código como `R.string.default_web_client_id`.

---

## 3. Configuración en el proyecto Android

### 3.1 Plugin de Google Services

En el `build.gradle.kts` del módulo `app` necesitas el plugin:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}
```

Y declarado en el `build.gradle.kts` raíz (o en `settings.gradle.kts` con
`pluginManagement`, según la plantilla de Android Studio que uses).

### 3.2 Dependencias necesarias

En `app/build.gradle.kts`:

```kotlin
dependencies {
    // Firebase (BoM = gestiona versiones compatibles entre librerías)
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation(libs.firebase.auth)

    // Credential Manager: la API moderna de Android para iniciar sesión
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)

    // Librería de Google para parsear el ID Token de Google dentro de
    // Credential Manager
    implementation(libs.googleid)
}
```

> En este proyecto las versiones están centralizadas en
> `gradle/libs.versions.toml` (el catálogo de versiones de Gradle), por eso
> se usa `libs.xxx` en vez de escribir coordenadas Maven a mano.

### 3.3 Permisos en el `AndroidManifest.xml`

Firebase necesita acceso a internet:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

No hace falta nada más en el manifest para Credential Manager (a diferencia
de la antigua API `GoogleSignIn`, que requería configurar `intent-filter`s
o `meta-data` adicionales en algunos casos).

---

## 4. El `ViewModel`: toda la lógica de autenticación

Fichero: `app/src/main/java/.../ui/Login/LoginViewModel.kt`

### 4.1 Estado observable

```kotlin
class LoginViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess = _loginSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _googleToastMessage = MutableStateFlow<String?>(null)
    val googleToastMessage = _googleToastMessage.asStateFlow()
    ...
}
```

**Patrón backing property**: el campo `_x` es `MutableStateFlow` (privado,
mutable). El campo `x` es la versión pública de solo lectura
(`StateFlow`, inmutable desde fuera). Así la UI puede *leer* el estado
pero no puede *cambiarlo* directamente: solo el ViewModel decide cuándo
cambia.

### 4.2 Login y registro con email/contraseña

```kotlin
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
```

Idea clave: `signInWithEmailAndPassword` y `createUserWithEmailAndPassword`
son funciones **asíncronas basadas en callbacks** (`Task` de Google Play
Services, no son `suspend`). Por eso no hace falta corrutina aquí: solo
escuchamos el resultado con `addOnCompleteListener`.

### 4.3 Login con Google (Credential Manager)

Este es el flujo más complejo. Se divide en dos partes:

1. **`launchGoogle`**: abre el selector de cuentas del sistema operativo y
   obtiene un *ID Token* de Google.
2. **`loginWithGoogle`**: usa ese token para autenticarse en Firebase.

```kotlin
fun launchGoogle(context: Context, isRegistering: Boolean) {
    viewModelScope.launch {
        try {
            val googleClientId = context.getString(R.string.default_web_client_id)
            val credentialManager = CredentialManager.create(context)

            val googleOption = if (isRegistering) {
                // Selector explícito: fuerza a elegir cuenta, útil en el
                // alta para no depender de una sesión ya autorizada antes.
                GetSignInWithGoogleOption.Builder(googleClientId).build()
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

            // getCredential es "suspend": muestra UI del sistema y espera
            // a que el usuario elija una cuenta.
            val result = credentialManager.getCredential(context, request)
            val credential = GoogleIdTokenCredential.createFrom(result.credential.data)

            loginWithGoogle(credential.idToken)
        } catch (_: GetCredentialCancellationException) {
            _googleToastMessage.value = "Inicio con Google cancelado por el usuario"
        } catch (exception: GetCredentialException) {
            _googleToastMessage.value = "Google no pudo iniciar sesion: ${exception.message}"
        } catch (exception: GoogleIdTokenParsingException) {
            _googleToastMessage.value = "La respuesta de Google no es valida"
        } catch (exception: Exception) {
            // Red de seguridad: algunos fallos internos de Play Services no
            // llegan como GetCredentialException y, sin este catch, tiran
            // la app.
            _googleToastMessage.value = "No se pudo iniciar sesion con Google. Intentalo de nuevo."
        }
    }
}

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

fun consumeGoogleToastMessage() {
    _googleToastMessage.value = null
}
```

Puntos didácticos importantes:

- **`viewModelScope.launch`**: `getCredential` es una función `suspend`
  (puede tardar porque muestra un diálogo del sistema), así que hay que
  lanzarla dentro de una corrutina. `viewModelScope` se cancela
  automáticamente cuando se destruye el ViewModel.
- **`context` como parámetro, no como campo**: si guardáramos el `Context`
  de la Activity en una propiedad del ViewModel, tendríamos una fuga de
  memoria (el ViewModel vive más que la Activity en ciertos casos, como al
  rotar la pantalla). Por eso se recibe como parámetro de la función y solo
  se usa dentro de esa misma llamada.
- **Dos opciones de Google distintas** según `isRegistering`:
  - `GetSignInWithGoogleOption`: siempre muestra el selector de cuentas
    (ideal para "crear cuenta").
  - `GetGoogleIdOption`: puede reutilizar una sesión ya autorizada
    previamente (ideal para "iniciar sesión" con una cuenta ya usada antes).
- **Tres tipos de error distintos** y por qué se separan:
  1. `GetCredentialCancellationException`: el usuario cerró el selector. No
     es un error real, solo informamos.
  2. `GetCredentialException`: fallo del propio Credential Manager (por
     ejemplo, SHA-1 no configurado en Firebase, o Google Play Services no
     disponible).
  3. `GoogleIdTokenParsingException`: la respuesta no se pudo interpretar
     como un token válido de Google.
  4. `Exception` genérica: red de seguridad para que un fallo inesperado de
     Play Services no haga *crashear* la app.
- **Separar `googleToastMessage` de `errorMessage`**: `errorMessage` se
  pinta en pantalla de forma persistente (por ejemplo, credenciales
  incorrectas al hacer login con email). `googleToastMessage` es un evento
  puntual (se muestra una vez como Toast y se "consume"), porque no tiene
  sentido dejarlo fijo en pantalla.

### 4.4 Cerrar sesión y consultar el usuario actual

```kotlin
val isUserLoggedIn: Boolean
    get() = auth.currentUser != null

fun getCurrentUser(): FirebaseUser? = auth.currentUser

fun signOut() {
    auth.signOut()
    _loginSuccess.value = false
    _errorMessage.value = null
}
```

---

## 5. La pantalla (`LoginScreen.kt`)

La pantalla **solo**:

1. Observa el estado del ViewModel con `collectAsState()`.
2. Mantiene el estado *local* de los campos de texto (`email`, `password`,
   `isRegistering`) con `remember { mutableStateOf(...) }`, porque eso es
   estado de UI efímero que no necesita sobrevivir a la destrucción de la
   pantalla.
3. Llama a funciones del ViewModel en los `onClick`.
4. Usa `LaunchedEffect` para reaccionar a cambios de estado con
   *side-effects* (mostrar un Toast, navegar a otra pantalla).

```kotlin
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

    // ... TextFields para email y password ...

    Button(onClick = {
        if (isRegistering) {
            loginViewModel.registerWithEmail(email.trim(), password)
        } else {
            loginViewModel.loginWithEmail(email.trim(), password)
        }
    }) { /* ... */ }

    OutlinedButton(onClick = {
        loginViewModel.launchGoogle(context, isRegistering)
    }) { /* ... */ }

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
```

¿Por qué `LaunchedEffect` y no mostrar el Toast directamente en el
`onClick`? Porque el resultado de `loginWithEmail`/`launchGoogle` llega de
forma **asíncrona** (tarda un poco en responder Firebase o Google). El
`onClick` solo *dispara* la petición; cuando el `StateFlow` cambia más
tarde, Compose vuelve a ejecutar el `LaunchedEffect` asociado a esa clave y
reacciona al nuevo valor.

---

## 6. Resumen del flujo completo (para repasar)

### Login/registro con email

1. Usuario escribe email y contraseña, pulsa "Entrar"/"Registrarme".
2. `LoginScreen` llama a `loginViewModel.loginWithEmail(...)` o
   `registerWithEmail(...)`.
3. El ViewModel marca `isLoading = true` y llama a Firebase Auth.
4. Cuando Firebase responde, el ViewModel actualiza `isLoading`,
   `loginSuccess` y `errorMessage`.
5. La UI reacciona: muestra el `CircularProgressIndicator` mientras
   `isLoading == true`, pinta el error si lo hay, y si `loginSuccess ==
   true` navega a la pantalla principal.

### Login/registro con Google

1. Usuario pulsa "Entrar con Google" / "Registrarme con Google".
2. `LoginScreen` llama a `loginViewModel.launchGoogle(context,
   isRegistering)`.
3. El ViewModel abre el selector de cuentas de Android (Credential
   Manager), que muestra una UI del sistema operativo encima de la app.
4. Si el usuario elige una cuenta, se obtiene un *ID Token* de Google y se
   llama internamente a `loginWithGoogle(idToken)`, que repite el mismo
   patrón `prepareRequest`/`finishRequest` que el login con email.
5. Si el usuario cancela o hay un error, el ViewModel publica un mensaje en
   `googleToastMessage`, y la pantalla lo muestra como Toast una sola vez.

---

## 7. Errores típicos al configurarlo (para depurar en clase)

| Síntoma | Causa probable |
|---|---|
| El selector de Google nunca aparece / falla con `GetCredentialException` | Falta añadir el SHA-1 (`./gradlew signingReport`) en Firebase Console |
| `default_web_client_id` no existe / error de compilación | Falta el plugin `com.google.gms.google-services` o el `google-services.json` no está en `app/` |
| Login con email falla con "no autorizado" | No se activó el proveedor "Correo electrónico/contraseña" en Firebase Console |
| La app crashea tras elegir cuenta de Google | Falta el `catch (exception: Exception)` genérico, o falta la dependencia `androidx.credentials.play.services.auth` |
| El Toast de Google aparece duplicado o no desaparece | No se llamó a `consumeGoogleToastMessage()` tras mostrarlo |

---
