package com.leonidshutov.forestaura

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
            ForestAuraTheme {
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
    ObserveLifecycle(androidx.lifecycle.compose.LocalLifecycleOwner.current, mediaPlayersMap)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF4CAF50), // Green
                            Color(0xFF81C784), // Light Green
                            Color(0xFF388E3C) // Dark Green
                        )
                    )
                )
        ) {
            TopAppBarContent() // Add the Top App Bar

            // Add the "Stop All" button
            Button(
                onClick = { stopAllPlayers(mediaPlayersMap) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(text = "Stop All", fontSize = 14.sp)
            }

            SoundGroups(mediaPlayersMap) // Add the sound groups
        }
    }
}

@Composable
fun SoundGroups(mediaPlayersMap: Map<Int, ButtonData>) {
    val groups = listOf(
        "Birds" to mediaPlayersMap.values.filter { it.fileName.startsWith("bird") },
        "Water" to mediaPlayersMap.values.filter { it.fileName.startsWith("water") },
        "Forest" to mediaPlayersMap.values.filter { it.fileName.startsWith("forest") },
        "Weather" to mediaPlayersMap.values.filter { it.fileName.startsWith("weather") },
        "Animals" to mediaPlayersMap.values.filter { it.fileName.startsWith("animal") }
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        groups.forEach { (groupName, sounds) ->
            item {
                ExpandableSoundGroup(groupName, sounds)
            }
        }
    }
}

@Composable
fun ExpandableSoundGroup(groupName: String, sounds: List<ButtonData>) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            ListItem(
                headlineContent = { Text(text = groupName, style = MaterialTheme.typography.titleMedium) },
                trailingContent = {
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            painter = painterResource(id = if (isExpanded) R.drawable.baseline_expand_less_24 else R.drawable.baseline_expand_more_24),
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
            )
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    sounds.forEach { buttonData ->
                        SoundButton(buttonData = buttonData)
                    }
                }
            }
        }
    }
}

@Composable
fun SoundButton(buttonData: ButtonData) {
    val sliderValue = remember { mutableFloatStateOf(buttonData.volume) }

    // Animate the card's background color
    val backgroundColor by animateColorAsState(
        targetValue = if (buttonData.isPlaying.value) {
            MaterialTheme.colorScheme.primaryContainer // Highlight when playing
        } else {
            MaterialTheme.colorScheme.surface // Default color
        },
        animationSpec = tween(durationMillis = 300), label = "" // Smooth transition
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor // Use the animated color
        )
    ) {
        Column {
            Button(
                onClick = {
                    val mediaPlayer = buttonData.mediaPlayer
                    if (!buttonData.isPlaying.value) {
                        prepareAndStartMediaPlayer(buttonData)
                    } else {
                        buttonData.lastPosition = mediaPlayer.currentPosition
                        mediaPlayer.pause()
                        buttonData.isPlaying.value = false // Update playing state
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent, // Make the button transparent
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp) // Remove button elevation
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        painter = painterResource(id = if (buttonData.isPlaying.value) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = buttonData.fileName,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            AnimatedVisibility(
                visible = buttonData.isPlaying.value,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Slider(
                    value = sliderValue.floatValue,
                    onValueChange = { newValue ->
                        sliderValue.floatValue = newValue
                        buttonData.volume = newValue
                        buttonData.mediaPlayer.setVolume(newValue, newValue)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarContent() {
    TopAppBar(
        title = { Text("Forest Aura") },
        actions = {
            IconButton(onClick = { /* Handle settings/info click */ }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings"
                )
            }
        }
    )
}

private fun loadSoundResources(context: Context): List<Pair<Int, String>> {
    val soundResources = mutableListOf<Pair<Int, String>>()
    val rawClass = R.raw::class.java
    val rawFields = rawClass.fields

    for (field in rawFields) {
        val resourceId = field.getInt(null)
        val fileName = context.resources.getResourceEntryName(resourceId)
        soundResources.add(resourceId to fileName)
        Timber.d("Loaded sound resource: $fileName (ID: $resourceId)")
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
        buttonData.isPlaying.value = true // Update playing state
    }
}

private fun stopAllPlayers(mediaPlayersMap: Map<Int, ButtonData>) {
    mediaPlayersMap.values.forEach { buttonData ->
        buttonData.mediaPlayer.pause()
        buttonData.mediaPlayer.seekTo(0)
        buttonData.lastPosition = 0
        buttonData.isPlaying.value = false // Update playing state
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
    var volume: Float = 1.0f,
    var isPlaying: MutableState<Boolean> = mutableStateOf(false) // Track playing state
)

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MainScreen()
}