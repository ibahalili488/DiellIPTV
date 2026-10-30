package com.diell.iptv.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.diell.iptv.model.SourceKind
import com.diell.iptv.ui.theme.Night
import com.diell.iptv.ui.theme.PanelHigh
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diell.iptv.model.Playlist
import com.diell.iptv.ui.theme.Danger
import com.diell.iptv.ui.theme.InkMuted
import com.diell.iptv.ui.theme.Sun
import java.text.DateFormat
import java.util.Date

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    loaded: Boolean,
    busy: Boolean,
    vm: AppViewModel,
    onOpen: (Playlist) -> Unit,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showNew by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addFromFile(uri)
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val id = exportId
        if (uri != null && id != null) vm.export(id, uri)
        exportId = null
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DiellWordmark()
            Spacer(Modifier.weight(1f))
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Sun)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = GapS, modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Button(onClick = { showAdd = true }, modifier = Modifier.focusRing(RoundedCornerShape(50))) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.size(6.dp)); Text("Add playlist")
                    }
                    OutlinedButton(
                        onClick = { openFile.launch(arrayOf("*/*")) },
                        modifier = Modifier.focusRing(RoundedCornerShape(50)),
                    ) { Text("Open .m3u file") }
                    OutlinedButton(onClick = { showNew = true }, modifier = Modifier.focusRing(RoundedCornerShape(50))) {
                        Text("Empty list")
                    }
                }
            }

            if (loaded && playlists.isEmpty()) {
                item { EmptyWelcome() }
            }

            items(playlists, key = { it.id }) { p ->
                PlaylistCard(
                    playlist = p,
                    onOpen = { onOpen(p) },
                    onRename = { renaming = p },
                    onReload = { vm.reload(p.id) },
                    onRestore = { vm.restoreRemoved(p.id) },
                    onExport = { exportId = p.id; saveFile.launch("${p.name}.m3u") },
                    onDelete = { deleting = p },
                )
            }
        }
    }

    if (showAdd) {
        AddPlaylistDialog(
            deviceMac = vm.deviceMac,
            onDismiss = { showAdd = false },
            onLink = { name, url -> showAdd = false; vm.addFromUrl(name, url) },
            onXtream = { name, server, user, pass -> showAdd = false; vm.addXtream(name, server, user, pass) },
            onPortal = { name, url -> showAdd = false; vm.addPortal(name, url) },
        )
    }
    if (showNew) {
        TextPromptDialog(
            title = "New empty playlist",
            label = "Playlist name",
            initial = "",
            confirm = "Create",
            onDismiss = { showNew = false },
            onConfirm = { showNew = false; vm.createEmpty(it) },
        )
    }
    renaming?.let { p ->
        TextPromptDialog(
            title = "Rename playlist",
            label = "Playlist name",
            initial = p.name,
            confirm = "Rename",
            onDismiss = { renaming = null },
            onConfirm = { renaming = null; vm.rename(p.id, it) },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${p.name}”?") },
            text = { Text("This removes the playlist and its ${p.channels.size} channels from Diell IPTV. Your original file or link is not touched.") },
            confirmButton = {
                TextButton(onClick = { deleting = null; vm.deletePlaylist(p.id) }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyWelcome() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = GapM,
    ) {
        SunMark(96.dp)
        Text("Add your first playlist", style = MaterialTheme.typography.titleLarge)
        Text(
            "Use the M3U link, Xtream login or portal address from your TV provider, or open an .m3u file saved on this device.",
            color = InkMuted,
            modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onReload: () -> Unit,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(shape)
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = GapM) {
            Icon(Icons.Default.List, null, tint = Sun, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                val source = when (playlist.kind) {
                    SourceKind.FILE -> "from file"
                    SourceKind.M3U_LINK -> "M3U link"
                    SourceKind.XTREAM -> "Xtream login"
                    SourceKind.PORTAL -> "MAC portal"
                }
                Text(
                    "${playlist.channels.size} channels · ${playlist.groups.size} groups · $source" +
                        (if (playlist.updatesOnline) " · updated " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(playlist.updatedAt)) else ""),
                    color = InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (playlist.info.isNotBlank()) {
                    Text(playlist.info, color = Sun, style = MaterialTheme.typography.labelSmall)
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.focusRing(RoundedCornerShape(50))) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Playlist options")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    if (playlist.updatesOnline) {
                        DropdownMenuItem(text = { Text("Update now") }, onClick = { menu = false; onReload() })
                    }
                    if (playlist.updatesOnline && playlist.removedKeys.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Bring back removed channels (${playlist.removedKeys.size})") },
                            onClick = { menu = false; onRestore() },
                        )
                    }
                    if (playlist.kind != SourceKind.PORTAL) {
                        DropdownMenuItem(text = { Text("Save as .m3u file") }, onClick = { menu = false; onExport() })
                    }
                    DropdownMenuItem(text = { Text("Delete", color = Danger) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

private enum class AddType(val label: String) { LINK("M3U link"), XTREAM("Xtream login"), PORTAL("MAC portal") }

@Composable
private fun AddPlaylistDialog(
    deviceMac: String,
    onDismiss: () -> Unit,
    onLink: (name: String, url: String) -> Unit,
    onXtream: (name: String, server: String, user: String, pass: String) -> Unit,
    onPortal: (name: String, url: String) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(AddType.LINK) }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val ready = address.isNotBlank() && (type != AddType.XTREAM || (user.isNotBlank() && pass.isNotBlank()))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add playlist") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = GapM) {
                Row(horizontalArrangement = GapS, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    AddType.entries.forEach { t ->
                        FilterChip(
                            selected = t == type,
                            onClick = { type = t },
                            label = { Text(t.label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Sun, selectedLabelColor = Night),
                        )
                    }
                }
                Text(
                    when (type) {
                        AddType.LINK -> "Paste the M3U link your provider gave you. It updates itself when you open the app."
                        AddType.XTREAM -> "Enter the server address, username and password from your provider."
                        AddType.PORTAL -> "Give your provider this device's MAC address to activate it, then enter their portal address."
                    },
                    color = InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (type == AddType.PORTAL) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(PanelHigh, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("This device's MAC address", color = InkMuted, style = MaterialTheme.typography.labelSmall)
                            Text(deviceMac, color = Sun, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = { clipboard.setText(AnnotatedString(deviceMac)) }) { Text("Copy") }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    address, { address = it },
                    label = {
                        Text(
                            when (type) {
                                AddType.LINK -> "M3U link"
                                AddType.XTREAM -> "Server address"
                                AddType.PORTAL -> "Portal address"
                            },
                        )
                    },
                    placeholder = {
                        Text(
                            when (type) {
                                AddType.LINK -> "http://…/playlist.m3u"
                                AddType.XTREAM -> "http://provider.com:8080"
                                AddType.PORTAL -> "http://provider.com:8080/c/"
                            },
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type == AddType.XTREAM) {
                    OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        pass, { pass = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = { TextButton(onClick = { showPass = !showPass }) { Text(if (showPass) "Hide" else "Show") } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = {
                    when (type) {
                        AddType.LINK -> onLink(name, address)
                        AddType.XTREAM -> onXtream(name, address, user, pass)
                        AddType.PORTAL -> onPortal(name, address)
                    }
                },
            ) { Text(if (type == AddType.LINK) "Add" else "Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextPromptDialog(
    title: String,
    label: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
