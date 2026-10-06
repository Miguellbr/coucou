package com.miguellbr.coucou.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CoucouScreen(vm: CoucouViewModel = viewModel()) {
    var host by remember { mutableStateOf("http://192.168.1.100:8765") }
    var token by remember { mutableStateOf("") }
    val sessions by vm.sessions.collectAsState()
    val connected by vm.connected.collectAsState()

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Coucou")
        Text(if (connected) "● Conectado ao Mac" else "○ Desconectado")
        OutlinedTextField(host, { host = it }, label = { Text("Endereço do Mac") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(token, { token = it }, label = { Text("Token do relay") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { vm.connect(host, token) }) { Text("Conectar") }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(sessions, key = { it.pillId }) { session ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(session.name)
                        Text(session.state)
                        if (session.finalLine.isNotBlank()) Text(session.finalLine)
                        if (session.needsApproval) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.approve(session) }) { Text("Permitir") }
                                Button(onClick = { vm.deny(session) }) { Text("Negar") }
                            }
                        }
                    }
                }
            }
        }
    }
}
