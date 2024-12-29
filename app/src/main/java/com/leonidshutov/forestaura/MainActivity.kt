package com.leonidshutov.forestaura

import android.content.Context
import android.content.Intent
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leonidshutov.forestaura.ui.theme.ForestAuraTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(Timber.DebugTree()) // Plant a debug tree for debug builds
        setContent {
            ForestAuraTheme {
                val viewModel: MainViewModel = viewModel()
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val soundResources = loadSoundResources(context)

    // Initialize media players
    LaunchedEffect(Unit) {
        viewModel.initializeMediaPlayers(context, soundResources)
    }

    // Release MediaPlayer resources when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAllPlayers(context)
        }
    }
    // Observe lifecycle events to stop the foreground service when the app is destroyed
    ObserveLifecycle(LocalLifecycleOwner.current, onDestroy = {
        viewModel.stopAllPlayers(context)
    })

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
                onClick = { viewModel.stopAllPlayers(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(text = "Stop All", fontSize = 14.sp)
            }

            SoundGroups(viewModel.mediaPlayersMap, viewModel)
        }
    }
}

@Composable
fun SoundGroups(mediaPlayersMap: Map<Int, ButtonData>, viewModel: MainViewModel) {
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
                ExpandableSoundGroup(groupName, sounds, viewModel)
            }
        }
    }
}

@Composable
fun ExpandableSoundGroup(groupName: String, sounds: List<ButtonData>, viewModel: MainViewModel) {
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
                        SoundButton(buttonData = buttonData, viewModel = viewModel, context = LocalContext.current)
                    }
                }
            }
        }
    }
}

@Composable
fun SoundButton(buttonData: ButtonData, viewModel: MainViewModel, context: Context) {
    val sliderValue = remember { mutableStateOf(buttonData.volume) }

    // Animate the card's background color
    val backgroundColor by animateColorAsState(
        targetValue = if (buttonData.isPlaying.value) {
            MaterialTheme.colorScheme.primaryContainer // Highlight when playing
        } else {
            MaterialTheme.colorScheme.surface // Default color
        },
        animationSpec = tween(durationMillis = 300) // Smooth transition
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
                    if (!buttonData.isPlaying.value) {
                        viewModel.prepareAndStartMediaPlayer(buttonData, context)
                    } else {
                        buttonData.lastPosition = buttonData.mediaPlayer.currentPosition
                        buttonData.mediaPlayer.pause()
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
                    value = sliderValue.value,
                    onValueChange = { newValue ->
                        sliderValue.value = newValue
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

fun startForegroundService(context: Context, soundResourceIds: List<Int>) {
    val intent = Intent(context, ForegroundService::class.java).apply {
        putIntegerArrayListExtra("soundResourceIds", ArrayList(soundResourceIds))
    }
    context.startForegroundService(intent)
}

fun stopForegroundService(context: Context) {
    val intent = Intent(context, ForegroundService::class.java)
    context.stopService(intent)
}

@Composable
fun ObserveLifecycle(lifecycleOwner: LifecycleOwner, onDestroy: () -> Unit) {
    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                onDestroy()
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
    MainScreen(viewModel = MainViewModel())
}