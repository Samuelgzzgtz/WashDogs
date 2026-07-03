package com.washdog.app.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.washdog.app.ui.theme.WDBg
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.ui.theme.WDPrimary
import com.washdog.app.util.BiometricAuth
import com.washdog.app.viewmodel.AppViewModel

@Composable
fun LoginScreen(vm: AppViewModel) {
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var remember_ by remember { mutableStateOf(vm.hasRememberedCredentials()) }
    var errorMsg by remember { mutableStateOf("") }

    val context  = LocalContext.current
    val activity = context as? FragmentActivity
    val canBiometric = activity != null &&
        vm.hasRememberedCredentials() &&
        BiometricAuth.canAuthenticate(activity)

    // Autocompletar tras desbloqueo biométrico (huella/PIN) y entrar.
    fun unlockWithBiometrics() {
        val act = activity ?: return
        BiometricAuth.prompt(
            activity  = act,
            title     = "Desbloquear WashDog",
            subtitle  = "Usa tu huella o PIN para entrar",
            onSuccess = {
                val creds = vm.rememberedCredentials()
                if (creds != null) {
                    email = creds.first; password = creds.second
                    vm.login(creds.first, creds.second, remember = true, onError = { errorMsg = it })
                }
            },
            onError   = { /* el usuario puede escribir sus datos manualmente */ }
        )
    }

    // Al abrir el login con credenciales guardadas, pedir biometría automáticamente una vez.
    LaunchedEffect(Unit) {
        if (canBiometric) unlockWithBiometrics()
    }

    Box(
        Modifier.fillMaxSize().background(WDBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("🐾 WashDog", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = WDPrimary)
            Text("Iniciar sesión", fontSize = 18.sp, color = WDInkMute)

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value         = email,
                onValueChange = { email = it; errorMsg = "" },
                label         = { Text("Correo electrónico") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            OutlinedTextField(
                value         = password,
                onValueChange = { password = it; errorMsg = "" },
                label         = { Text("Contraseña") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                visualTransformation = if (showPass) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPass) "Ocultar" else "Mostrar"
                        )
                    }
                }
            )

            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = remember_, onCheckedChange = { remember_ = it })
                Text("Recordar mis datos", fontSize = 13.sp, color = WDInkMute)
            }

            if (errorMsg.isNotBlank()) {
                Text(errorMsg, color = Color.Red, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    vm.login(email.trim(), password, remember = remember_, onError = { errorMsg = it })
                },
                enabled  = email.isNotBlank() && password.isNotBlank() && !vm.isLoggingIn,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = WDPrimary)
            ) {
                if (vm.isLoggingIn) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(22.dp),
                        color       = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Iniciar sesión", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            if (canBiometric) {
                OutlinedButton(
                    onClick  = { unlockWithBiometrics() },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = WDPrimary)
                    Spacer(Modifier.size(8.dp))
                    Text("Entrar con huella / PIN", color = WDPrimary)
                }
            }
        }
    }
}
