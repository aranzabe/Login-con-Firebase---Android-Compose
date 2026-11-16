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
│ observa StateFlow   │ <──────── │ isLoading                 │
│ dibuja UI           │           │ loginSuccess              │
│ llama a funciones   │ ────────> │ errorMessage              │
│ del ViewModel       │           │ googleToastMessage        │
└─────────────────────┘           │                           │
                                  │ loginWithEmail()          │
                                  │ registerWithEmail()       │
                                  │ launchGoogle()            │
                                  │ loginWithGoogle()         │
                                  │ signOut()                 │
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
    //...
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

## 7. Errores típicos al configurarlo

| Síntoma | Causa probable |
|---|---|
| El selector de Google nunca aparece / falla con `GetCredentialException` | Falta añadir el SHA-1 (`./gradlew signingReport`) en Firebase Console |
| `default_web_client_id` no existe / error de compilación | Falta el plugin `com.google.gms.google-services` o el `google-services.json` no está en `app/` |
| Login con email falla con "no autorizado" | No se activó el proveedor "Correo electrónico/contraseña" en Firebase Console |
| La app crashea tras elegir cuenta de Google | Falta el `catch (exception: Exception)` genérico, o falta la dependencia `androidx.credentials.play.services.auth` |
| El Toast de Google aparece duplicado o no desaparece | No se llamó a `consumeGoogleToastMessage()` tras mostrarlo |

---

## 8. Realtime Database: listado de usuarios reactivo

Pantalla: `RealTimeDBVentana.kt`. Componentes: `UsuarioRepository`,
`MiViewModelRealtime` y la pantalla en sí.

### 8.1 Arquitectura

```
RealTimeDBVentana (Composable)     MiViewModelRealtime          UsuarioRepository
┌─────────────────────┐           ┌───────────────────────┐      ┌─────────────────────────┐
│ observa StateFlow   │ <──────── │ usuarios              │ <─── │ usuarios: Flow<...>     │
│ dibuja la lista     │           │ codigoError           │      │ (callbackFlow sobre     │
│ llama a funciones   │ ────────> │ usuariosBorrados      │      │  addValueEventListener) │
│ del ViewModel       │           │ errorMessage          │      │                         │
└─────────────────────┘           │                       │      │ agregar()               │
                                  │ agregarUsuario()      │ ───> │ borrarPorNombre()       │
                                  │ borrarUsuario()       │      │ borrarPorId()           │
                                  │ borrarUsuarioPorId()  │      │ usuariosMenoresDeEdad() │
                                  │ borrarUsuariosMenor-  │      │ eliminarSnapshot()      │
                                  │   DeEdad2()           │      └─────────────────────────┘
                                  └───────────────────────┘
```

Regla de oro (la misma que en el login): **la UI no toca Firebase directamente**,
y ahora añadimos una capa más: **el ViewModel tampoco toca Firebase directamente**.
Todo el código que sabe que existe `FirebaseDatabase` vive en el
`UsuarioRepository`. El ViewModel solo conoce el repositorio (una interfaz de
"usuarios" como lista de datos), y la UI solo conoce el ViewModel.

Esto se llama **patrón Repository**: separa "de dónde vienen los datos"
(Firebase, una API REST, una base de datos local...) de "qué se hace con
ellos" (el ViewModel). Si mañana cambiáramos Realtime Database por otra
fuente de datos, solo habría que reescribir el repositorio; el ViewModel y
la pantalla no se enterarían.

### 8.2 El repositorio: `UsuarioRepository`

Fichero: `app/src/main/java/.../ui/RealTimeDB/UsuarioRepository.kt`

```kotlin
class UsuarioRepository {
    private val usuariosReference = FirebaseDatabase.getInstance()
        .getReference(Colecciones.usuarios)

    val usuarios: Flow<Result<List<Usuario>>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val listaDeUsuarios = snapshot.children.mapNotNull { child ->
                    child.getValue(Usuario::class.java)?.let { usuarioSinId ->
                        usuarioSinId.copy(id = child.key)
                    }
                }
                trySend(Result.success(listaDeUsuarios))
            }

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

    fun borrarPorId(usuarioId: String): Task<Void> =
        usuariosReference.child(usuarioId).removeValue()
    // ...
}
```

Puntos didácticos:

- **`callbackFlow`**: convierte una API basada en *listeners* (lo normal en
  Firebase) en un `Flow` de Kotlin, que es lo que se consume con
  corrutinas. Es el puente entre el "mundo callbacks" de Firebase y el
  "mundo `Flow`" de Kotlin.
- **`ValueEventListener`**: Realtime Database lo llama **cada vez que
  cambian los datos del nodo** (`onDataChange`), no solo la primera vez. Por
  eso es "reactivo": si otro dispositivo añade un usuario, todos los
  clientes conectados reciben la lista actualizada automáticamente, sin
  pedirla manualmente.
- **`trySend(...)`**: envía un valor nuevo al `Flow` cada vez que llega un
  evento. Aquí se envía un `Result<List<Usuario>>` en vez de una simple
  lista, para poder distinguir "lista vacía" (éxito, pero sin datos) de
  "ha fallado la lectura" (por ejemplo, sin permisos o sin conexión).
- **`awaitClose { ... }`**: se ejecuta cuando el `Flow` se cancela (por
  ejemplo, al destruirse el ViewModel). Aquí se usa para **darse de baja**
  del listener (`removeEventListener`); si no lo hiciéramos, seguiríamos
  escuchando cambios en Firebase aunque ya nadie los necesite (fuga de
  listeners).
- **El `id` no viene en los datos**: en Realtime Database, la clave de cada
  usuario (`child.key`, algo como `-Nabc123`) es la ruta del nodo, no un
  campo más dentro de él. Por eso se copia manualmente con
  `usuarioSinId.copy(id = child.key)`.
- **Operaciones de escritura devuelven `Task`**: `agregar`, `borrarPorId`,
  etc. no manejan el error dentro del repositorio, simplemente devuelven el
  `Task` que da Firebase. Es el ViewModel quien decide qué hacer si falla
  (loguear, mostrar un mensaje...). El repositorio solo sabe **cómo pedirle
  los datos a Firebase**, no qué hacer con los resultados.

### 8.3 El ViewModel: `MiViewModelRealtime`

```kotlin
class MiViewModelRealtime(
    private val repository: UsuarioRepository = UsuarioRepository()
) : ViewModel() {

    private val _usuarios = MutableStateFlow<List<Usuario>>(emptyList())
    val usuarios = _usuarios.asStateFlow()

    private val _codigoError = MutableStateFlow(0)
    val codigoError = _codigoError.asStateFlow()

    init {
        viewModelScope.launch {
            repository.usuarios.collectLatest { resultado ->
                resultado
                    .onSuccess { lista ->
                        _usuarios.value = lista
                        _codigoError.value = if (lista.isEmpty()) 2 else 0
                    }
                    .onFailure { exception ->
                        _codigoError.value = 1
                        _errorMessage.value = exception.message
                    }
            }
        }
    }

    fun agregarUsuario(usuario: Usuario) {
        repository.agregar(usuario).addOnFailureListener { error ->
            Log.w(TAG, "Error al agregar usuario", error)
        }
    }
}
```

Nota el **inyección de dependencias por constructor con valor por defecto**:
`private val repository: UsuarioRepository = UsuarioRepository()`. Esto
permite que en producción el ViewModel cree su propio repositorio
automáticamente (`MiViewModelRealtime()`), pero en un test se podría pasar
un repositorio falso (`MiViewModelRealtime(repositorioFalso)`) sin tocar
Firebase de verdad.

- **`collectLatest` dentro de `init`**: en cuanto se crea el ViewModel, se
  suscribe al `Flow` del repositorio y empieza a recibir listas de usuarios
  para siempre (hasta que el ViewModel se destruye). Como el `Flow` viene de
  un `callbackFlow` "en caliente" conectado a Firebase, cada cambio en la
  base de datos dispara una nueva emisión y el `StateFlow` `_usuarios` se
  actualiza solo.
- **`codigoError` como código numérico** (`0` = todo bien, `1` = error de
  lectura, `2` = lista vacía): es una forma sencilla (aunque poco elegante)
  de que la UI distinga varios estados sin necesitar una `sealed class`.
  Se podría mejorar con un `enum class` o una `sealed interface
  EstadoUsuarios`, pero el código numérico es más fácil de seguir para
  empezar.
- Las funciones `agregarUsuario`, `borrarUsuario`, `borrarUsuarioPorId` y
  `borrarUsuariosMenoresDeEdad2()` **no tocan el `Flow` de usuarios**: solo
  piden al repositorio que escriba o borre datos en Firebase. La lista
  (`_usuarios`) se actualiza sola gracias al `ValueEventListener` que ya
  está escuchando; no hace falta refrescarla a mano.

### 8.4 La pantalla

```kotlin
val usuarios by miViewModelRTDB.usuarios.collectAsState()
val codigoError by miViewModelRTDB.codigoError.collectAsState()

if (codigoError == 2) {
    Text("Lista vacía", color = MaterialTheme.colorScheme.error)
} else {
    LazyColumn { items(usuarios) { usuario -> /* ... */ } }
}

Button(onClick = { miViewModelRTDB.agregarUsuario(Factoria.getUsuario()) }) {
    Text("Agregar usuario aleatorio")
}
```

Mismo patrón que en el login: la pantalla observa `StateFlow` con
`collectAsState()` y llama a funciones del ViewModel en los `onClick`. No
hay ni una sola línea de Firebase en este fichero.

---

## 9. El chat: dos formas de implementar lo mismo

El chat (`ChatUI.kt`) se puede alimentar de **dos bases de datos distintas**
sin que la pantalla lo note, porque ambos ViewModels exponen exactamente la
misma "interfaz" (`mensajes` y `sendMessage(sender, text)`):

- `ChatViewModel` + `MensajeRepository` → **Realtime Database**.
- `ChatViewModel_FireStore` + `MensajeFirestoreRepository` → **Firestore**.

```kotlin
// En MainActivity.kt, basta con cambiar el ViewModel que se le pasa a la
// pantalla para cambiar de base de datos sin tocar ChatUI.kt:
val miChatVM: ChatViewModel = viewModel()
ChatScreen(navController = navController, viewModel = miChatVM, loginViewModel = viewModel)
// ChatScreen(navController = navController, viewModel = miChatVM_Firestore, loginViewModel = viewModel)
```

Esto es la prueba de que el patrón Repository funciona: **la UI depende de
una abstracción (un ViewModel con `mensajes` y `sendMessage`), no de una
implementación concreta de base de datos**.

### 9.1 Chat con Realtime Database (`MensajeRepository`)

```kotlin
class MensajeRepository {
    private val mensajesReference = FirebaseDatabase.getInstance()
        .getReference(Colecciones.mensajes)

    val mensajes: Flow<List<Mensaje>> = callbackFlow {
        val query = mensajesReference.orderByChild("timestamp").limitToLast(50)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = snapshot.children
                    .mapNotNull { it.getValue(Mensaje::class.java) }
                    .sortedByDescending { it.timestamp }
                trySend(lista)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    fun enviar(sender: String, text: String): Task<Void> {
        val id = mensajesReference.push().key
            ?: return Tasks.forException(IllegalStateException("..."))

        return mensajesReference.child(id).setValue(
            mapOf(
                "id" to id,
                "sender" to sender,
                "text" to text,
                "timestamp" to ServerValue.TIMESTAMP
            )
        )
    }
}
```

Detalles propios de Realtime Database:

- **`limitToLast(50)`**: Realtime Database no tiene un concepto de "primeros
  N según orden descendente" tan directo como Firestore; para traer los
  últimos 50 mensajes por fecha hay que pedir `orderByChild("timestamp")` +
  `limitToLast(50)` y luego **ordenar manualmente en el cliente**
  (`.sortedByDescending { it.timestamp }`), porque Realtime Database siempre
  devuelve los resultados en orden ascendente.
- **`push().key`**: genera una clave única ordenable por tiempo
  (`-Nabc123...`) sin tener que consultar antes a Firebase ni usar un
  contador propio. Es el equivalente, en Realtime Database, al
  "autoincremental" de otras bases de datos.
- **`ServerValue.TIMESTAMP`**: en vez de usar `System.currentTimeMillis()`
  del móvil (que podría tener la hora mal puesta), se le pide al
  **servidor** de Firebase que ponga la hora real en el momento de guardar
  el dato. Muy importante en un chat, donde el orden de los mensajes
  depende de esto.

### 9.2 Chat con Firestore (`MensajeFirestoreRepository`)

```kotlin
class MensajeFirestoreRepository {
    private val mensajesCollection = FirebaseFirestore.getInstance()
        .collection(Colecciones.mensajes)

    val mensajes: Flow<List<Mensaje>> = callbackFlow {
        val registration = mensajesCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val mensajes = snapshot?.documents.orEmpty()
                    .mapNotNull { it.toObject(Mensaje::class.java) }
                trySend(mensajes)
            }

        awaitClose { registration.remove() }
    }

    fun enviar(sender: String, text: String): Task<Void> {
        val mensaje = Mensaje(
            id = mensajesCollection.document().id,
            sender = sender,
            text = text
        )
        return mensajesCollection.document(mensaje.id).set(mensaje)
    }
}
```

Diferencias respecto a la versión de Realtime Database:

- **`orderBy(...).limit(50)` ya devuelve el orden correcto**: no hace falta
  ordenar a mano en el cliente, Firestore puede ordenar descendente
  directamente en el servidor (con el índice adecuado).
- **`addSnapshotListener` en vez de `ValueEventListener`**: es el
  equivalente de Firestore, también se dispara cada vez que cambian los
  documentos de la consulta, y también hay que llamar a
  `registration.remove()` dentro de `awaitClose` para darse de baja.
- **`document().id` en vez de `push().key`**: Firestore genera un ID
  aleatorio único para el documento (no ordenable por tiempo como el de
  Realtime Database); el orden lo da el campo `timestamp`, no el propio id.
- **`.set(mensaje)` guarda el objeto completo** (Firestore sabe serializar
  `data class` directamente a un documento), mientras que en Realtime
  Database se construyó un `Map` a mano para poder meter
  `ServerValue.TIMESTAMP` (un marcador especial que Realtime Database
  sustituye por la hora del servidor al guardar).

### 9.3 El `ChatViewModel`: igual de delgado en ambos casos

```kotlin
class ChatViewModel(
    private val repository: MensajeRepository = MensajeRepository()
) : ViewModel() {
    val mensajes: Flow<List<Mensaje>> = repository.mensajes

    fun sendMessage(sender: String, text: String) {
        repository.enviar(sender, text).addOnFailureListener { error ->
            Log.w("ChatViewModel", "Error al enviar el mensaje", error)
        }
    }
}
```

`ChatViewModel_FireStore` es idéntico, cambiando solo el tipo del
repositorio. El ViewModel ya no sabe (ni le importa) si los mensajes vienen
de Realtime Database o de Firestore: solo expone un `Flow<List<Mensaje>>`
y una función para enviar. Esta es la ventaja real del patrón Repository:
**el mismo ViewModel (o uno casi idéntico) sirve para cualquier fuente de
datos** que se ajuste a la misma forma.

---

## 10. Realtime Database vs. Firestore: ¿cuál elegir?

Ambas son bases de datos NoSQL de Firebase, reactivas (avisan a los
clientes cuando algo cambia) y con SDKs muy parecidos, pero no son
intercambiables en todos los casos. Esta tabla resume las diferencias más
importantes para un proyecto de clase:

| Aspecto | Realtime Database | Firestore |
|---|---|---|
| Modelo de datos | Un único árbol JSON gigante | Colecciones de documentos (como "carpetas" de JSON), que a su vez pueden tener subcolecciones |
| Consultas | Muy limitadas: solo se puede ordenar/filtrar por **un** campo a la vez (`orderByChild` + un filtro) | Mucho más potentes: se pueden combinar varios `where`, rangos, `orderBy` en varios campos (con índices compuestos) |
| Orden de resultados | Siempre ascendente; para "los últimos N" hay que pedir `limitToLast` y ordenar en el cliente | Se puede pedir `orderBy(..., DESCENDING)` directamente en el servidor |
| Escalado / estructura | Al crecer, el árbol único se puede volver difícil de organizar y es fácil des-normalizar mal los datos | Las colecciones/documentos escalan mejor y son más fáciles de estructurar en proyectos grandes |
| Latencia | Muy baja, pensada para datos que cambian constantemente (juegos, presencia online, chats en tiempo real) | Ligeramente mayor, pero sigue siendo "tiempo real" para la mayoría de apps |
| Precio | Se cobra sobre todo por **ancho de banda** (cuántos datos se transfieren) | Se cobra sobre todo por **número de lecturas/escrituras/borrados**, independientemente del tamaño |
| Consultas offline | Limitado | Soporte offline más completo y robusto (cache local automática) |
| Seguridad (reglas) | Reglas en forma de árbol JSON, pueden ser difíciles de leer en estructuras grandes | Reglas por colección/documento, generalmente más legibles |
| Curva de aprendizaje | Más sencilla para cosas pequeñas (todo es un `Map`/`JSON`) | Un poco más de conceptos (colección, documento, subcolección), pero se entiende bien con la analogía de "carpetas y ficheros" |

### ¿Cuándo usar cada una?

- **Realtime Database** encaja bien cuando los datos son simples, cambian
  muy rápido y no necesitas consultas complejas: presencia ("¿quién está
  conectado ahora?"), contadores en vivo, chats sencillos, juegos
  multijugador ligeros. En este proyecto, el chat con Realtime Database
  (`ChatViewModel`) es un buen ejemplo: solo necesitamos "los últimos 50
  mensajes ordenados por fecha", nada más complejo.
- **Firestore** encaja mejor cuando la app va a crecer, necesitas filtrar
  por varios campos a la vez (como hace `UsuarioRepository.usuariosConParte()`
  o `usuariosEntreEdades()` en el proyecto de ejemplo del repositorio de
  Firestore), quieres soporte offline sólido, o simplemente prefieres
  trabajar con una estructura de "colecciones y documentos" más parecida a
  una base de datos tradicional.

En este proyecto convivir ambas en el mismo chat es sobre todo un ejercicio
didáctico: ver **el mismo problema** (guardar y escuchar una lista de
mensajes en tiempo real) resuelto con dos tecnologías distintas, usando
**exactamente la misma arquitectura** (Repository + ViewModel delgado +
pantalla "tonta"). Eso es lo más importante de esta sección: la base de
datos cambia, pero la forma de organizar el código no tiene por qué
cambiar.

---
