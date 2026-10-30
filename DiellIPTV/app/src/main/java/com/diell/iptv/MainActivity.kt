package com.diell.iptv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.diell.iptv.ui.AppViewModel
import com.diell.iptv.ui.ChannelsScreen
import com.diell.iptv.ui.PlayerScreen
import com.diell.iptv.ui.PlaylistsScreen
import com.diell.iptv.ui.theme.DiellTheme
import com.diell.iptv.ui.theme.Night

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleOpenFile(intent)
        setContent { DiellTheme { DiellApp(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenFile(intent)
    }

    /** Someone chose "Open with Diell IPTV" on an .m3u file. */
    private fun handleOpenFile(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW) vm.addFromFile(uri)
    }
}

@Composable
private fun DiellApp(vm: AppViewModel) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var openPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var playingId by rememberSaveable { mutableStateOf<String?>(null) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            val result = snackbar.showSnackbar(
                message = msg.text,
                actionLabel = if (msg.canUndo) "Undo" else null,
                duration = if (msg.canUndo) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) vm.undo()
        }
    }

    val current = vm.playlist(openPlaylistId)
    if (openPlaylistId != null && current == null && loaded) openPlaylistId = null

    // The player covers the whole screen, outside the Scaffold.
    val playing = playingId
    if (playing != null && vm.playQueue.isNotEmpty()) {
        PlayerScreen(
            channels = vm.playQueue,
            startId = playing,
            resolve = { vm.streamUrl(it) },
            onBack = { playingId = null },
        )
        return
    } else if (playing != null) {
        playingId = null
    }

    Scaffold(
        containerColor = Night,
        snackbarHost = { SnackbarHost(snackbar) },
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
    ) { padding ->
        val inner = Modifier.padding(padding)
        if (current == null) {
            androidx.compose.foundation.layout.Box(inner) {
                PlaylistsScreen(
                    playlists = playlists,
                    loaded = loaded,
                    busy = busy,
                    vm = vm,
                    onOpen = { openPlaylistId = it.id },
                )
            }
        } else {
            androidx.compose.foundation.layout.Box(inner) {
                ChannelsScreen(
                    playlist = current,
                    vm = vm,
                    onBack = { openPlaylistId = null },
                    onPlay = { visible, channel ->
                        vm.playQueue = visible
                        vm.playPlaylistId = current.id
                        playingId = channel.id
                    },
                )
            }
        }
    }
}
