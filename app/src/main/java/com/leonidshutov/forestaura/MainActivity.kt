package com.leonidshutov.forestaura

import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leonidshutov.forestaura.ui.theme.ForestAuraTheme
import kotlinx.coroutines.delay
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

val timerDurations = listOf(10, 30, 60, 180) // In minutes

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val soundResources = loadSoundResources(context)
    var showTimerDialog by remember { mutableStateOf(false) }
    var selectedTimerDuration by remember { mutableStateOf<Int?>(null) } // Timer duration in minutes
    var remainingTime by remember { mutableStateOf<Int?>(null) } // Remaining time in seconds
    var isTimerActive by remember { mutableStateOf(false) } // Whether the timer is active

    // Initialize media players
    LaunchedEffect(Unit) {
        viewModel.initializeMediaPlayers(context, soundResources)
    }

    // Start the timer when a duration is selected
    LaunchedEffect(selectedTimerDuration) {
        selectedTimerDuration?.let { duration ->
            isTimerActive = true
            remainingTime = duration * 60 // Convert minutes to seconds
            while (isTimerActive && remainingTime != null && remainingTime!! > 0) {
                delay(1000L) // Wait for 1 second
                remainingTime = remainingTime!! - 1
            }
            if (isTimerActive) {
                // Timer ended, stop all players
                viewModel.stopAllPlayers(context)
                showTimerEndNotification(context)
            }
            selectedTimerDuration = null
            remainingTime = null
            isTimerActive = false
        }
    }

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
            TopAppBarContent(
                onTimerClick = { showTimerDialog = true },
                remainingTime = remainingTime,
                onCancelTimer = { isTimerActive = false }
            )

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

    // Show the sleep timer dialog
    if (showTimerDialog) {
        SleepTimerDialog(
            onTimerSelected = { duration ->
                selectedTimerDuration = duration
                showTimerDialog = false
            },
            onCancel = { isTimerActive = false },
            onDismiss = { showTimerDialog = false }
        )
    }
}

@Composable
fun SleepTimerDialog(
    onTimerSelected: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    var customDuration by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Sleep Timer") },
        text = {
            Column {
                timerDurations.forEach { duration ->
                    Button(
                        onClick = { onTimerSelected(duration) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text("$duration minutes")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = customDuration,
                    onValueChange = { customDuration = it },
                    label = { Text("Custom duration (minutes)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val duration = customDuration.toIntOrNull()
                        if (duration != null && duration > 0) {
                            onTimerSelected(duration)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("Set Custom Timer")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
fun TopAppBarContent(
    onTimerClick: () -> Unit,
    remainingTime: Int?,
    onCancelTimer: () -> Unit
) {
    TopAppBar(
        title = { Text("Forest Aura") },
        actions = {
            if (remainingTime != null) {
                val minutes = remainingTime / 60
                val seconds = remainingTime % 60
                val formattedTime = String.format("%02d:%02d", minutes, seconds) // Format with leading zeros
                Text(
                    text = "Remaining: $formattedTime",
                    modifier = Modifier.padding(end = 16.dp)
                )
                IconButton(onClick = onCancelTimer) {
                    Icon(
                        imageVector = Icons.Default.Close, // or Icons.Default.Close
                        contentDescription = "Cancel Timer"
                    )
                }
            }
            IconButton(onClick = onTimerClick) {
                Icon(
                    imageVector = Icons.Default.DateRange, // or Icons.Default.AccessTime
                    contentDescription = "Sleep Timer"
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

private fun showTimerEndNotification(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        "timer_channel",
        "Timer Notifications",
        NotificationManager.IMPORTANCE_DEFAULT
    )
    notificationManager.createNotificationChannel(channel)

    val notification = NotificationCompat.Builder(context, "timer_channel")
        .setContentTitle("Forest Aura")
        .setContentText("Sleep timer has ended")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    notificationManager.notify(1, notification)
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