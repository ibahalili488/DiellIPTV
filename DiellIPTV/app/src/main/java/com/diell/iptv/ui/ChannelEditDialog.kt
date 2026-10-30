package com.diell.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.diell.iptv.model.Channel
import com.diell.iptv.ui.theme.InkMuted
import com.diell.iptv.ui.theme.Night
import com.diell.iptv.ui.theme.Sun

/** Add a new channel (initial == null) or edit an existing one. */
@Composable
fun ChannelEditDialog(
    initial: Channel?,
    presetGroup: String,
    existingGroups: List<String>,
    onDismiss: () -> Unit,
    onSave: (Channel, atTop: Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(initial?.url.orEmpty()) }
    var group by rememberSaveable { mutableStateOf(initial?.group ?: presetGroup) }
    var logo by rememberSaveable { mutableStateOf(initial?.logo.orEmpty()) }
    var tvgId by rememberSaveable { mutableStateOf(initial?.tvgId.orEmpty()) }
    var atTop by rememberSaveable { mutableStateOf(false) }

    val valid = name.isNotBlank() && url.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add channel" else "Edit channel") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Channel name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    url, { url = it },
                    label = { Text("Stream link") },
                    placeholder = { Text("http://…/live/channel.m3u8") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(group, { group = it }, label = { Text("Group") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (existingGroups.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(existingGroups) { g ->
                            AssistChip(onClick = { group = g }, label = { Text(g) })
                        }
                    }
                }
                OutlinedTextField(logo, { logo = it }, label = { Text("Logo link (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tvgId, { tvgId = it }, label = { Text("TV guide ID, tvg-id (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (initial == null) {
                    Text("Put it", color = InkMuted, style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = Sun, selectedLabelColor = Night)
                        FilterChip(selected = !atTop, onClick = { atTop = false }, label = { Text("At the end") }, colors = chipColors)
                        FilterChip(selected = atTop, onClick = { atTop = true }, label = { Text("At the top") }, colors = chipColors)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val cleanName = name.trim()
                    val base = initial ?: Channel(name = cleanName, url = url.trim())
                    // Keep tvg-name in step with the visible name when the playlist uses it.
                    val attrs = if ("tvg-name" in base.attrs) base.attrs + ("tvg-name" to cleanName) else base.attrs
                    onSave(
                        base.copy(
                            name = cleanName,
                            url = url.trim(),
                            group = group.trim(),
                            logo = logo.trim(),
                            tvgId = tvgId.trim(),
                            attrs = attrs,
                        ),
                        atTop,
                    )
                },
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
