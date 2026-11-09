package com.staninna.aufmachen.ui.proof

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.staninna.aufmachen.R
import com.staninna.aufmachen.data.ServiceView

/** Search dialog over all services. */
@Composable
fun ServicePickerDialog(
    services: List<ServiceView>,
    onPick: (ServiceView) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(services, query) { services.search(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.proof_search_all)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(stringResource(R.string.proof_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (shown.isEmpty()) {
                    Text(stringResource(R.string.proof_search_empty), Modifier.padding(top = 12.dp))
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(shown, key = { it.domain }) { s ->
                            ListItem(
                                headlineContent = { Text(s.label()) },
                                supportingContent = { if (s.label() != s.domain) Text(s.domain) },
                                modifier = Modifier.clickable { onPick(s) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
