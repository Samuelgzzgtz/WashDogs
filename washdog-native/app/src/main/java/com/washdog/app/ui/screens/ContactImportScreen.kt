package com.washdog.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.PetType
import com.washdog.app.ui.theme.*
import com.washdog.app.util.CsvParser
import com.washdog.app.util.PhoneNormalizer
import com.washdog.app.util.VcfParser
import com.washdog.app.viewmodel.AppViewModel
import java.util.UUID

private data class ImportContact(
    val name: String,
    val phone: String,
    val duplicate: Boolean
)

@Composable
fun ContactImportScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf<List<ImportContact>>(emptyList()) }
    var hasFile  by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var imported  by remember { mutableStateOf(0) }

    val existingPhones = remember(vm.clients) {
        vm.clients.mapNotNull { PhoneNormalizer.normalize(it.phone) }.toSet()
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val raw = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()?.readText() ?: return@rememberLauncherForActivityResult
        val fileName = uri.lastPathSegment?.lowercase() ?: ""
        val rawContacts = when {
            fileName.endsWith(".vcf") || raw.trimStart().startsWith("BEGIN:VCARD") ->
                VcfParser.parse(raw)
            else -> CsvParser.parse(raw)
        }
        contacts = rawContacts.mapNotNull { rc ->
            val phone = PhoneNormalizer.normalize(rc.phone) ?: return@mapNotNull null
            ImportContact(
                name      = rc.name,
                phone     = phone,
                duplicate = phone in existingPhones
            )
        }
        hasFile = true
    }

    val newContacts  = contacts.filter { !it.duplicate }
    val dupContacts  = contacts.filter { it.duplicate }

    Column(
        Modifier
            .fillMaxSize()
            .background(WDBg)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, null, tint = WDInk)
            }
            Text(
                "Importar contactos",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = WDInk
            )
        }
        Spacer(Modifier.height(16.dp))

        Button(
            onClick  = { filePicker.launch("*/*") },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = WDPrimary)
        ) {
            Text("Seleccionar archivo (.vcf / .csv)", color = Color.White)
        }

        if (hasFile) {
            Spacer(Modifier.height(12.dp))
            Text(
                "${newContacts.size} nuevos · ${dupContacts.size} duplicados",
                fontSize = 13.sp,
                color    = WDInkMute
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(contacts, key = { it.phone }) { contact ->
                    Card(
                        shape  = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (contact.duplicate) WDBgAlt else WDBg
                        ),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(contact.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = WDInk)
                                Text(contact.phone, fontSize = 12.sp, color = WDInkMute)
                            }
                            if (contact.duplicate) {
                                Icon(Icons.Filled.Warning, null, tint = WDInkMute, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Filled.CheckCircle, null, tint = WDPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (imported > 0) {
                Text(
                    "$imported contacto(s) importado(s)",
                    fontSize = 13.sp,
                    color    = WDPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Button(
                onClick  = {
                    importing = true
                    newContacts.forEach { ic ->
                        vm.addClient(
                            Client(
                                id              = UUID.randomUUID().toString(),
                                ownerName       = ic.name,
                                petName         = "",
                                breed           = "",
                                phone           = ic.phone,
                                address         = "",
                                lastVisit       = "",
                                needsReschedule = false,
                                petType         = PetType.DOG
                            )
                        )
                    }
                    imported  = newContacts.size
                    importing = false
                    contacts  = contacts.map { it.copy(duplicate = true) }
                },
                enabled  = newContacts.isNotEmpty() && !importing,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = WDPrimary)
            ) {
                if (importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(
                        "Importar ${newContacts.size} contacto(s)",
                        color = Color.White
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Selecciona un archivo .vcf o .csv\nde tus contactos de WhatsApp Business",
                    fontSize = 14.sp,
                    color    = WDInkMute
                )
            }
        }
    }
}
