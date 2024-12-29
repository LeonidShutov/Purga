package com.leonidshutov.forestaura

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.leonidshutov.forestaura.ui.theme.ForestAuraTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(Timber.DebugTree()) // Plant a debug tree for debug builds
        setContent {
            ForestAuraTheme{
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val soundResources = loadSoundResources(context)
    val mediaPlayersMap = remember { mutableStateMapOf<Int, ButtonData>() }

    // Initialize media players and update the map
    LaunchedEffect(Unit) {
        val initializedMap = initializeMediaPlayers(context, soundResources, mediaPlayersMap)
        mediaPlayersMap.clear()
        mediaPlayersMap.putAll(initializedMap)
        Timber.d("MediaPlayersMap size: ${mediaPlayersMap.size}")
    }

    // Release MediaPlayer resources when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayersMap.values.forEach { buttonData ->
                buttonData.mediaPlayer.release()
            }
        }
    }

    // Observe lifecycle events to pause MediaPlayers when the app goes to the background
    ObserveLifecycle(LocalLifecycleOwner.current, mediaPlayersMap)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = { stopAllPlayers(mediaPlayersMap) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text(text = stringResource(id = R.string.stop_all), fontSize = 14.sp)
        }
        mediaPlayersMap.values.forEach { buttonData ->
            Timber.d("Rendering button for: ${buttonData.fileName}")
            MediaButton(buttonData = buttonData)
        }
    }
}

@Composable
fun MediaButton(buttonData: ButtonData) {
    val sliderValue = remember { mutableStateOf(buttonData.volume) }

    Button(
        onClick = {
            val mediaPlayer = buttonData.mediaPlayer
            if (!mediaPlayer.isPlaying) {
                prepareAndStartMediaPlayer(buttonData)
            } else {
                buttonData.lastPosition = mediaPlayer.currentPosition
                mediaPlayer.pause()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Gray,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                painter = painterResource(id = if (buttonData.mediaPlayer.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = buttonData.fileName,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = sliderValue.value,
                onValueChange = { newValue ->
                    sliderValue.value = newValue
                    buttonData.volume = newValue
                    buttonData.mediaPlayer.setVolume(newValue, newValue)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun loadSoundResources(context: Context): List<Pair<Int, String>> {
    val soundResources = mutableListOf<Pair<Int, String>>()
    val rawClass = R.raw::class.java
    val rawFields = rawClass.fields

    for (field in rawFields) {
        val resourceId = field.getInt(null)
        val fileName = context.resources.getResourceEntryName(resourceId)
        soundResources.add(resourceId to fileName)
        Timber.d("Loaded sound resource: $fileName (ID: $resourceId)") //
    }

    return soundResources
}

private fun initializeMediaPlayers(
    context: Context,
    soundResources: List<Pair<Int, String>>,
    mediaPlayersMap: MutableMap<Int, ButtonData>
): Map<Int, ButtonData> {
    return soundResources.associate { (rawResourceId, fileName) ->
        val mediaPlayer = MediaPlayer.create(context, rawResourceId)
            ?: throw IllegalStateException("Failed to create MediaPlayer for resource $rawResourceId")
        mediaPlayer.setOnCompletionListener {
            mediaPlayersMap[rawResourceId]?.let { buttonData ->
                if (buttonData.mediaPlayer.isPlaying) { // Check if the player is still playing
                    buttonData.lastPosition = 0
                    prepareAndStartMediaPlayer(buttonData)
                }
            }
        }
        Timber.d("Initialized MediaPlayer for: $fileName (ID: $rawResourceId)")

        rawResourceId to ButtonData(
            mediaPlayer = mediaPlayer,
            context = context,
            rawResourceId = rawResourceId,
            fileName = fileName
        )
    }
}
private fun prepareAndStartMediaPlayer(buttonData: ButtonData) {
    val mediaPlayer = buttonData.mediaPlayer
    val context = buttonData.context
    val rawResourceId = buttonData.rawResourceId

    mediaPlayer.reset()
    context.resources.openRawResourceFd(rawResourceId).use { rawFileDescriptor ->
        mediaPlayer.setDataSource(
            rawFileDescriptor.fileDescriptor,
            rawFileDescriptor.startOffset,
            rawFileDescriptor.length
        )
        mediaPlayer.prepare()
        if (buttonData.lastPosition > 0) {
            mediaPlayer.seekTo(buttonData.lastPosition)
        }
        mediaPlayer.start()
    }
}

private fun stopAllPlayers(mediaPlayersMap: Map<Int, ButtonData>) {
    mediaPlayersMap.values.forEach { buttonData ->
        buttonData.mediaPlayer.pause()
        buttonData.mediaPlayer.seekTo(0)
        buttonData.lastPosition = 0
    }
}

@Composable
private fun ObserveLifecycle(lifecycleOwner: LifecycleOwner, mediaPlayersMap: Map<Int, ButtonData>) {
    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                mediaPlayersMap.values.forEach { buttonData ->
                    buttonData.mediaPlayer.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
}

data class ButtonData(
    val mediaPlayer: MediaPlayer,
    var lastPosition: Int = 0,
    val context: Context,
    val rawResourceId: Int,
    val fileName: String,
    var volume: Float = 1.0f
)

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MainScreen()
}