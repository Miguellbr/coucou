package com.miguellbr.coucou.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miguellbr.coucou.model.Session

@Composable
fun CoucouScreen(vm: CoucouViewModel = viewModel()) {
    var host by remember { mutableStateOf("http://192.168.1.100:8765") }
    var token by remember { mutableStateOf("") }
    val sessions by vm.sessions.collectAsState()
    val connected by vm.connected.collectAsState()

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Coucou", style = MaterialTheme.typography.headlineMedium)
        Text(if (connected) "● Conectado ao Mac" else "○ Desconectado")
        OutlinedTextField(host, { host = it }, label = { Text("Endereço do Mac") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(token, { token = it }, label = { Text("Token do relay") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.discoverAndConnect() }) { Text("Encontrar Mac") }
            Button(onClick = { vm.connect(host, token) }) { Text("Manual") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(sessions, key = { it.pillId }) { session ->
                SessionCard(session, vm)
            }
        }
    }
}

@Composable
private fun SessionCard(session: Session, vm: CoucouViewModel) {
    var instruction by remember(session.pillId) { mutableStateOf("") }
    var selections by remember(session.questionFingerprint) {
        mutableStateOf(List(session.questionPayload?.items?.size ?: 0) { emptyList<String>() })
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(session.name, style = MaterialTheme.typography.titleMedium)
            Text(session.state)
            if (session.finalLine.isNotBlank()) Text(session.finalLine)

            if (session.acceptsInstructions) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Enviar instrução", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ex.: continue a tarefa...") },
                    minLines = 2,
                    maxLines = 5
                )
                Button(
                    onClick = {
                        vm.sendInstruction(session, instruction)
                        instruction = ""
                    },
                    enabled = instruction.isNotBlank() && instruction.length <= 8000
                ) {
                    Text("Enviar")
                }
            }

            if (session.needsApproval) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.approve(session) }) { Text("Permitir") }
                    Button(onClick = { vm.deny(session) }) { Text("Negar") }
                }
            }

            if (session.needsAnswer && session.questionPayload != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Pergunta", style = MaterialTheme.typography.titleMedium)

                session.questionPayload.items.forEachIndexed { index, item ->
                    Text(item.question, style = MaterialTheme.typography.bodyLarge)
                    item.options.forEach { option ->
                        val selected = option.label in selections[index]
                        if (item.multiSelect) {
                            Row(Modifier.fillMaxWidth()) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = {
                                        selections = selections.toMutableList().also { all ->
                                            all[index] = if (it) all[index] + option.label
                                            else all[index] - option.label
                                        }
                                    }
                                )
                                Column {
                                    Text(option.label)
                                    if (option.description.isNotBlank()) Text(option.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth()) {
                                RadioButton(
                                    selected = selected,
                                    onClick = {
                                        selections = selections.toMutableList().also { all ->
                                            all[index] = listOf(option.label)
                                        }
                                    }
                                )
                                Column {
                                    Text(option.label)
                                    if (option.description.isNotBlank()) Text(option.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { vm.answer(session, selections) },
                    enabled = selections.size == session.questionPayload.items.size &&
                        selections.all { it.isNotEmpty() }
                ) {
                    Text("Enviar resposta")
                }
            }
        }
    }
}
