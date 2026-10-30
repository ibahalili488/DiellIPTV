package com.diell.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diell.iptv.model.Channel
import com.diell.iptv.model.Playlist
import com.diell.iptv.ui.theme.Danger
import com.diell.iptv.ui.theme.InkMuted
import com.diell.iptv.ui.theme.Night
import com.diell.iptv.ui.theme.Panel
import com.diell.iptv.ui.theme.PanelHigh
import com.diell.iptv.ui.theme.Sun

private const val ALL = "\u0000all"
private const val FAVORITES = "\u0000fav"
private const val NO_GROUP = "\u0000none"

private data class GroupItem(val key: String, val label: String, val count: Int)

@Composable
fun ChannelsScreen(
    playlist: Playlist,
    vm: AppViewModel,
    onBack: () -> Unit,
    onPlay: (visible: List<Channel>, channel: Channel) -> Unit,
) {
    var group by rememberSaveable { mutableStateOf(ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var editing by remember { mutableStateOf<Channel?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Set<String>?>(null) }

    val groups = remember(playlist) {
        buildList {
            add(GroupItem(ALL, "All channels", playlist.channels.size))
            add(GroupItem(FAVORITES, "Favorites", playlist.channels.count { it.favorite }))
            playlist.groups.forEach { g -> add(GroupItem(g, g, playlist.channels.count { it.group == g })) }
            val ungrouped = playlist.channels.count { it.group.isBlank() }
            if (ungrouped > 0 && playlist.groups.isNotEmpty()) add(GroupItem(NO_GROUP, "No group", ungrouped))
        }
    }
    if (groups.none { it.key == group }) group = ALL

    val visible = remember(playlist, group, query) {
        playlist.channels.filter { c ->
            val inGroup = when (group) {
                ALL -> true
                FAVORITES -> c.favorite
                NO_GROUP -> c.group.isBlank()
                else -> c.group == group
            }
            inGroup && (query.isBlank() || c.name.contains(query, true) || c.url.contains(query, true))
        }
    }
    val numbers = remember(playlist) { playlist.channels.withIndex().associate { it.value.id to it.index + 1 } }

    BackHandler {
        when {
            selecting -> { selecting = false; selected = emptySet() }
            searching -> { searching = false; query = "" }
            else -> onBack()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize()) {
            // ---- Top bar ----
            if (selecting) {
                Row(
                    Modifier.fillMaxWidth().background(PanelHigh).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = GapS,
                ) {
                    IconButton(onClick = { selecting = false; selected = emptySet() }, modifier = Modifier.focusRing(RoundedCornerShape(50))) {
                        Icon(Icons.Default.Close, "Stop selecting")
                    }
                    Text("${selected.size} selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            val ids = visible.map { it.id }.toSet()
                            selected = if (selected.containsAll(ids)) selected - ids else selected + ids
                        },
                        modifier = Modifier.focusRing(RoundedCornerShape(50)),
                    ) { Text("Select all shown") }
                    Button(
                        onClick = { confirmRemove = selected },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.focusRing(RoundedCornerShape(50)),
                    ) {
                        Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("Remove")
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = GapS,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.focusRing(RoundedCornerShape(50))) {
                        Icon(Icons.Default.ArrowBack, "Back to playlists")
                    }
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search channels") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { searching = false; query = "" }, modifier = Modifier.focusRing(RoundedCornerShape(50))) {
                            Icon(Icons.Default.Close, "Close search")
                        }
                    } else {
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${playlist.channels.size} channels", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { searching = true }, modifier = Modifier.focusRing(RoundedCornerShape(50))) {
                            Icon(Icons.Default.Search, "Search")
                        }
                        OutlinedButton(onClick = { selecting = true }, modifier = Modifier.focusRing(RoundedCornerShape(50))) {
                            Text("Select")
                        }
                        Button(onClick = { adding = true }, modifier = Modifier.focusRing(RoundedCornerShape(50))) {
                            Icon(Icons.Default.Add, null); if (wide) { Spacer(Modifier.width(6.dp)); Text("Add channel") }
                        }
                    }
                }
            }

            // ---- Body ----
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.width(260.dp).fillMaxHeight().background(Panel),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(groups, key = { it.key }) { g ->
                            GroupRailItem(g, g.key == group) { group = g.key }
                        }
                    }
                    ChannelList(
                        visible, numbers, selecting, selected, Modifier.weight(1f),
                        onClick = { c ->
                            if (selecting) selected = selected.toggle(c.id) else onPlay(visible, c)
                        },
                        onEdit = { editing = it },
                        onMove = { c, d -> vm.moveChannel(playlist.id, c.id, d) },
                        onFavorite = { vm.toggleFavorite(playlist.id, it.id) },
                        onRemove = { confirmRemove = setOf(it.id) },
                    )
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = GapS,
                ) {
                    items(groups, key = { it.key }) { g ->
                        FilterChip(
                            selected = g.key == group,
                            onClick = { group = g.key },
                            label = { Text("${g.label} · ${g.count}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Sun,
                                selectedLabelColor = Night,
                            ),
                        )
                    }
                }
                ChannelList(
                    visible, numbers, selecting, selected, Modifier.fillMaxSize(),
                    onClick = { c ->
                        if (selecting) selected = selected.toggle(c.id) else onPlay(visible, c)
                    },
                    onEdit = { editing = it },
                    onMove = { c, d -> vm.moveChannel(playlist.id, c.id, d) },
                    onFavorite = { vm.toggleFavorite(playlist.id, it.id) },
                    onRemove = { confirmRemove = setOf(it.id) },
                )
            }
        }
    }

    if (adding || editing != null) {
        val presetGroup = if (group == ALL || group == FAVORITES || group == NO_GROUP) "" else group
        ChannelEditDialog(
            initial = editing,
            presetGroup = presetGroup,
            existingGroups = playlist.groups,
            onDismiss = { adding = false; editing = null },
            onSave = { channel, atTop ->
                vm.saveChannel(playlist.id, channel, atTop)
                adding = false; editing = null
            },
        )
    }

    confirmRemove?.let { ids ->
        val single = if (ids.size == 1) playlist.channels.firstOrNull { it.id in ids } else null
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(if (single != null) "Remove “${single.name}”?" else "Remove ${ids.size} channels?") },
            text = { Text("You can undo this right after.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeChannels(playlist.id, ids)
                    confirmRemove = null; selecting = false; selected = emptySet()
                }) { Text("Remove", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

@Composable
private fun GroupRailItem(item: GroupItem, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .focusRing(shape)
            .background(if (active) Sun else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.label,
            color = if (active) Night else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("${item.count}", color = if (active) Night else InkMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChannelList(
    channels: List<Channel>,
    numbers: Map<String, Int>,
    selecting: Boolean,
    selected: Set<String>,
    modifier: Modifier,
    onClick: (Channel) -> Unit,
    onEdit: (Channel) -> Unit,
    onMove: (Channel, Int) -> Unit,
    onFavorite: (Channel) -> Unit,
    onRemove: (Channel) -> Unit,
) {
    if (channels.isEmpty()) {
        Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No channels here. Try another group or search, or add a channel.", color = InkMuted, modifier = Modifier.widthIn(max = 360.dp))
        }
        return
    }
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(channels, key = { it.id }) { c ->
            ChannelRow(
                channel = c,
                number = numbers[c.id] ?: 0,
                selecting = selecting,
                checked = c.id in selected,
                onClick = { onClick(c) },
                onEdit = { onEdit(c) },
                onMove = { d -> onMove(c, d) },
                onFavorite = { onFavorite(c) },
                onRemove = { onRemove(c) },
            )
        }
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    number: Int,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onFavorite: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .focusRing(shape)
            .background(if (checked) PanelHigh else Panel, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = GapM,
    ) {
        if (selecting) {
            Checkbox(checked = checked, onCheckedChange = { onClick() }, colors = CheckboxDefaults.colors(checkedColor = Sun, checkmarkColor = Night))
        }
        Text(
            "$number",
            color = InkMuted,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(40.dp),
        )
        ChannelLogo(channel.name, channel.logo)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.name.ifBlank { "(no name)" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (channel.favorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Favorite, "Favorite", tint = Sun, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                if (channel.group.isNotBlank()) channel.group.uppercase() else channel.url,
                color = InkMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (!selecting) {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.focusRing(RoundedCornerShape(50))) {
                    Icon(Icons.Default.MoreVert, "Options for ${channel.name}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (channel.favorite) "Remove from favorites" else "Add to favorites") }, onClick = { menu = false; onFavorite() })
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMove(-1) })
                    DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMove(1) })
                    DropdownMenuItem(text = { Text("Remove", color = Danger) }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}
