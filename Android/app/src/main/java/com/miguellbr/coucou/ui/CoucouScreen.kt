package com.miguellbr.coucou.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CoucouScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Coucou", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
        Text("Android client inicial")
        Text("Próximo: conectar ao Mac e receber sessões e aprovações.")

        Button(onClick = { }) {
            Text("Conectar ao Mac")
        }
    }
}
