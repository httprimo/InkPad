package com.personal.inkpad.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personal.inkpad.InkPadApp
import com.personal.inkpad.R
import com.personal.inkpad.ui.theme.isAppDarkTheme
import com.personal.inkpad.ui.theme.libraryBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SignInScreen(
    onAuthenticated: () -> Unit,
    onGoToSignUp: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val sync = InkPadApp.instance.syncProvider

    fun runEmailSignIn() {
        focus.clearFocus()
        error = null
        if (email.isBlank() || password.length < 6) {
            error = "Enter email and a password (6+ characters)."
            return
        }
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { sync.signIn(email.trim(), password) }
                onAuthenticated()
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }

    fun runGoogle() {
        focus.clearFocus()
        error = null
        scope.launch {
            busy = true
            try {
                val google = GoogleSignInHelper.requestIdToken(context)
                withContext(Dispatchers.IO) {
                    sync.signInWithGoogleIdToken(google.idToken, google.rawNonce)
                }
                onAuthenticated()
            } catch (e: Exception) {
                error = e.message ?: "Google sign-in failed"
            } finally {
                busy = false
            }
        }
    }

    AuthScaffold(
        title = null,
        subtitle = "Log in to open your notebooks"
    ) {
        AuthEmailFields(
            email = email,
            password = password,
            onEmailChange = { email = it; error = null },
            onPasswordChange = { password = it; error = null },
            onDone = { runEmailSignIn() },
            enabled = !busy
        )
        AuthError(error)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { runEmailSignIn() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !busy
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.height(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Log in")
            }
        }
        AuthGoogleSection(busy = busy, onGoogle = { runGoogle() })
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onGoToSignUp, enabled = !busy) {
            Text("Create an account")
        }
    }
}

@Composable
fun SignUpScreen(
    onAuthenticated: () -> Unit,
    onGoToSignIn: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val sync = InkPadApp.instance.syncProvider

    fun runSignUp() {
        focus.clearFocus()
        error = null
        when {
            email.isBlank() || password.length < 6 ->
                error = "Enter email and a password (6+ characters)."
            password != confirm ->
                error = "Passwords don’t match."
            else -> scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) { sync.signUp(email.trim(), password) }
                    onAuthenticated()
                } catch (e: Exception) {
                    error = e.message ?: "Something went wrong"
                } finally {
                    busy = false
                }
            }
        }
    }

    fun runGoogle() {
        focus.clearFocus()
        error = null
        scope.launch {
            busy = true
            try {
                val google = GoogleSignInHelper.requestIdToken(context)
                withContext(Dispatchers.IO) {
                    sync.signInWithGoogleIdToken(google.idToken, google.rawNonce)
                }
                onAuthenticated()
            } catch (e: Exception) {
                error = e.message ?: "Google sign-in failed"
            } finally {
                busy = false
            }
        }
    }

    AuthScaffold(
        title = "Create account",
        subtitle = "Sign up to keep notebooks synced"
    ) {
        AuthEmailFields(
            email = email,
            password = password,
            onEmailChange = { email = it; error = null },
            onPasswordChange = { password = it; error = null },
            onDone = { runSignUp() },
            enabled = !busy,
            passwordIme = ImeAction.Next
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Confirm password") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { runSignUp() }),
            enabled = !busy
        )
        AuthError(error)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { runSignUp() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !busy
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.height(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Sign up")
            }
        }
        AuthGoogleSection(busy = busy, onGoogle = { runGoogle() })
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onGoToSignIn, enabled = !busy) {
            Text("Already have an account? Log in")
        }
    }
}

@Composable
private fun AuthScaffold(
    title: String?,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(libraryBackdrop(isAppDarkTheme))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 56.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.inkpad_logo),
                contentDescription = "InkPad",
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(168.dp),
                contentScale = ContentScale.Fit
            )
            if (!title.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(36.dp))
            content()
        }
    }
}

@Composable
private fun AuthEmailFields(
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDone: () -> Unit,
    enabled: Boolean,
    passwordIme: ImeAction = ImeAction.Done
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Email") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        enabled = enabled
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Password") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = passwordIme
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        enabled = enabled
    )
}

@Composable
private fun AuthError(error: String?) {
    if (error == null) return
    Spacer(Modifier.height(12.dp))
    Text(
        error,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun AuthGoogleSection(
    busy: Boolean,
    onGoogle: () -> Unit
) {
    Spacer(Modifier.height(20.dp))
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            "  or  ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(Modifier.weight(1f))
    }
    Spacer(Modifier.height(16.dp))
    OutlinedButton(
        onClick = onGoogle,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        enabled = !busy
    ) {
        Text(
            if (GoogleSignInHelper.isConfigured) "Continue with Google"
            else "Continue with Google (setup needed)"
        )
    }
}
