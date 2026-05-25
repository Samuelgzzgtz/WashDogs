package com.washdog.app.ui.screens

import android.Manifest
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.ui.components.WDPhoneField
import com.washdog.app.ui.components.digitsToPhone
import com.washdog.app.ui.theme.WDBg
import com.washdog.app.ui.theme.WDInk
import com.washdog.app.ui.theme.WDInkMute
import com.washdog.app.ui.theme.WDPrimary
import com.washdog.app.viewmodel.AppViewModel

@Composable
fun AddClientScreen(vm: AppViewModel, onBack: () -> Unit) {
    val config   = LocalConfiguration.current
    val isTablet = config.screenWidthDp >= 600
    val ph       = if (isTablet) 24.dp else 16.dp
    val maxW     = if (isTablet) 600.dp else Dp.Unspecified

    var ownerName   by remember { mutableStateOf("") }
    var petName     by remember { mutableStateOf("") }
    var breed       by remember { mutableStateOf("") }
    var phoneDigits by remember { mutableStateOf("") }
    var petType     by remember { mutableStateOf(PetType.DOG) }

    val context = LocalContext.current

    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.query(uri,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts._ID),
            null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                ownerName = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
                val cid   = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(cid), null)?.use { pc ->
                    if (pc.moveToFirst()) {
                        val raw = pc.getString(pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        phoneDigits = raw.filter { it.isDigit() }.takeLast(10)
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) contactPickerLauncher.launch(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(WDBg)
    ) {
        LazyColumn(
            modifier            = Modifier
                .widthIn(max = maxW)
                .align(Alignment.TopCenter),
            contentPadding      = androidx.compose.foundation.layout.PaddingValues(
                horizontal = ph,
                vertical   = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = null,
                            tint               = WDInkMute
                        )
                    }
                    Text(
                        text       = "Nuevo cliente",
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color      = WDInk
                    )
                }
            }
            item(key = "ownerName") {
                OutlinedTextField(
                    value         = ownerName,
                    onValueChange = { ownerName = it },
                    label         = { Text("Nombre del dueño") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    trailingIcon  = {
                        IconButton(onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                            Icon(Icons.Default.Contacts, contentDescription = "Importar contacto", tint = WDPrimary)
                        }
                    }
                )
            }
            item(key = "petName") {
                OutlinedTextField(
                    value         = petName,
                    onValueChange = { petName = it },
                    label         = { Text("Nombre de la mascota") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )
            }
            item(key = "breed") {
                OutlinedTextField(
                    value         = breed,
                    onValueChange = { breed = it },
                    label         = { Text("Raza") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )
            }
            item(key = "phone") {
                WDPhoneField(
                    digits         = phoneDigits,
                    onDigitsChange = { phoneDigits = it },
                    modifier       = Modifier.fillMaxWidth()
                )
            }
            item(key = "petType") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text     = "Tipo de mascota",
                        fontSize = 13.sp,
                        color    = WDInkMute
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val dogSelected = petType == PetType.DOG
                        OutlinedButton(
                            onClick  = { petType = PetType.DOG },
                            modifier = Modifier.weight(1f),
                            border   = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (dogSelected) WDPrimary else WDInkMute
                            ),
                            colors   = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (dogSelected) WDPrimary else WDInkMute
                            )
                        ) {
                            Text("Perro 🐶")
                        }
                        val catSelected = petType == PetType.CAT
                        OutlinedButton(
                            onClick  = { petType = PetType.CAT },
                            modifier = Modifier.weight(1f),
                            border   = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (catSelected) WDPrimary else WDInkMute
                            ),
                            colors   = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (catSelected) WDPrimary else WDInkMute
                            )
                        ) {
                            Text("Gato 🐱")
                        }
                    }
                }
            }
            item(key = "save") {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick  = {
                        if (ownerName.isBlank() || petName.isBlank()) return@Button
                        val client = Client(
                            id              = java.util.UUID.randomUUID().toString(),
                            ownerName       = ownerName.trim(),
                            petName         = petName.trim(),
                            breed           = breed.trim(),
                            lastVisit       = java.time.LocalDate.now().toString(),
                            needsReschedule = false,
                            petType         = petType,
                            phone           = digitsToPhone(phoneDigits)
                        )
                        vm.addClient(client)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = WDPrimary)
                ) {
                    Text("Guardar cliente", color = Color.White)
                }
            }
        }
    }
}
