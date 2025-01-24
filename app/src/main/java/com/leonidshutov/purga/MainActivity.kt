package com.leonidshutov.purga

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.leonidshutov.purga.ui.theme.PurgaTheme
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Timber.d("Notification permission granted")
            // Permission granted, start the foreground service if needed
            val intent = Intent(this, ForegroundService::class.java).apply {
                putIntegerArrayListExtra("soundResourceIds", ArrayList(emptyList())) // Pass empty list for now
            }

            startForegroundService(intent)

        } else {
            Timber.d("Notification permission denied")
            // Permission denied, show a message to the user
            Toast.makeText(
                this,
                getString(R.string.notification_permission_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val themeManager by lazy { ThemeManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(Timber.DebugTree()) // Plant a debug tree for debug builds

        // Request notification permission for Android 13 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            PurgaTheme(
                darkTheme = themeManager.getSelectedTheme() == "dark",
                dynamicColor = false // Disable dynamic color for now
            ) {
                MainScreen(
                    onThemeSelected = { theme ->
                        themeManager.setSelectedTheme(theme)
                        recreate() // Restart the activity to apply the new theme
                    },
                    onNotificationPermissionGranted = {
                        // Start the foreground service if needed
                        val intent = Intent(this, ForegroundService::class.java).apply {
                            putIntegerArrayListExtra("soundResourceIds", ArrayList(emptyList())) // Pass empty list for now
                        }
                        startForegroundService(intent)
                    }
                )
            }
        }
    }
}

val timerDurations = listOf(10, 30, 60, 180) // In minutes

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(),
               onThemeSelected: (String) -> Unit,
               onNotificationPermissionGranted: () -> Unit = {}) {
    val context = LocalContext.current
    val soundResources = loadSoundResources(context)
    var showTimerDialog by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) } // State for theme dialog
    var selectedTimerDuration by remember { mutableStateOf<Int?>(null) }
    var remainingTime by remember { mutableStateOf<Int?>(null) }
    var isTimerActive by remember { mutableStateOf(false) }

    // Initialize media players
    LaunchedEffect(Unit) {
        viewModel.initialize(context, soundResources)
    }

    // Start the timer when a duration is selected
    LaunchedEffect(selectedTimerDuration) {
        selectedTimerDuration?.let { duration ->
            isTimerActive = true
            remainingTime = duration * 60
            while (isTimerActive && remainingTime!! > 0) {
                delay(1000L)
                remainingTime = remainingTime!! - 1
            }
            if (isTimerActive) {
                viewModel.stopAllPlayers()
                showTimerEndNotification(context)
            }
            selectedTimerDuration = null
            remainingTime = null
            isTimerActive = false
        }
    }

    // Save last saved playing sounds when the app is backgrounded or destroyed
    ObserveLifecycle(
        lifecycleOwner = LocalLifecycleOwner.current,
        viewModel = viewModel,
        onStop = {
            Timber.d("App is being backgrounded. Saving last saved playing sounds.")
            viewModel.savePlayingSounds(saveLastSaved = true)
        },
        onDestroy = {
            Timber.d("App is being destroyed. Saving last saved playing sounds.")
            viewModel.savePlayingSounds(saveLastSaved = true)
            viewModel.stopAllPlayers()
            stopForegroundService(context)
        }
    )

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
                onSettingsClick = { showSettingsScreen = true },
                remainingTime = remainingTime,
                onCancelTimer = { isTimerActive = false }
            )

            // Add the "Stop All" or "Resume" button
            val isAnySoundPlaying = viewModel.mediaPlayersMap.values.any { it.isPlaying.value }
            Button(
                onClick = {
                    if (isAnySoundPlaying) {
                        viewModel.stopAllPlayers()
                    } else {
                        viewModel.resumePlayers(context)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(text = if (isAnySoundPlaying) stringResource(R.string.stop_all) else stringResource(R.string.play), fontSize = 14.sp)
            }

            SoundGroups(viewModel.mediaPlayersMap, viewModel)
        }

        // Show the settings screen with a background
        if (showSettingsScreen) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                color = Color.Transparent
            ) {
                SettingsScreen(
                    onLanguageClick = {
                        showSettingsScreen = false
                        showLanguageDialog = true
                    },
                    onThemeClick = {
                        showSettingsScreen = false
                        showThemeDialog = true
                    },
                    onDismiss = { showSettingsScreen = false },
                    viewModel = viewModel
                )
            }
        }
    }

    // Show the sleep timer dialog
    if (showTimerDialog) {
        SleepTimerDialog(
            onTimerSelected = { duration ->
                selectedTimerDuration = duration
                showTimerDialog = false
            },
            onDismiss = { showTimerDialog = false }
        )
    }

    // Show the language selection dialog
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            onLanguageSelected = { languageCode ->
                setAppLanguage(context, languageCode)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    // Show the theme selection dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            onThemeSelected = { theme ->
                onThemeSelected(theme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
fun SettingsScreen(
    onLanguageClick: () -> Unit,
    onThemeClick: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val playInBackground by viewModel.playInBackground

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Language Option
        Button(
            onClick = onLanguageClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(text = context.getString(R.string.language),
                color = MaterialTheme.colorScheme.onSurface)
        }

        // Theme Option
        Button(
            onClick = onThemeClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(text = context.getString(R.string.theme),
                color = MaterialTheme.colorScheme.onSurface)
        }

        // Play sounds in background switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = context.getString(R.string.play_in_background),
                color = MaterialTheme.colorScheme.onSurface)
            Switch(
                checked = playInBackground,
                onCheckedChange = { enabled ->
                    viewModel.setPlayInBackground(enabled)
                }
            )
        }

        // Close Button
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(text = context.getString(R.string.close),
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun ThemeSelectionDialog(
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themes = listOf(
        "system" to stringResource(R.string.system_theme),
        "light" to stringResource(R.string.light_theme),
        "dark" to stringResource(R.string.dark_theme)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme)) },
        text = {
            Column {
                themes.forEach { (themeCode, themeName) ->
                    Button(
                        onClick = {
                            onThemeSelected(themeCode)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(themeName)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun LanguageSelectionDialog(
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val languages = listOf(
        "system" to stringResource(R.string.system_language),
        "en" to stringResource(R.string.english),
        "ru" to stringResource(R.string.russian)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                languages.forEach { (code, name) ->
                    Button(
                        onClick = {
                            onLanguageSelected(code)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(name)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SleepTimerDialog(
    onTimerSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var customDuration by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.set_sleep_timer)) },
        text = {
            Column {
                timerDurations.forEach { duration ->
                    Button(
                        onClick = { onTimerSelected(duration) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text("$duration ${context.getString(R.string.minutes)}")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = customDuration,
                    onValueChange = { customDuration = it },
                    label = { Text(context.getString(R.string.custom_duration)) },
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
                    Text(context.getString(R.string.set_custom_timer))
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(context.getString(R.string.cancel))
            }
        }
    )
}

@Composable
fun SoundGroups(mediaPlayersMap: Map<Int, ButtonData>, viewModel: MainViewModel) {
    val context = LocalContext.current

    val groups = listOf(
        context.getString(R.string.group_birds) to mediaPlayersMap.values.filter { it.fileName.startsWith("bird") },
        context.getString(R.string.group_water) to mediaPlayersMap.values.filter { it.fileName.startsWith("water") },
        context.getString(R.string.group_forest) to mediaPlayersMap.values.filter { it.fileName.startsWith("forest") },
        context.getString(R.string.group_weather) to mediaPlayersMap.values.filter { it.fileName.startsWith("weather") },
        context.getString(R.string.group_animals) to mediaPlayersMap.values.filter { it.fileName.startsWith("animal") }
    )

    Timber.d("SoundGroups: mediaPlayersMap size=${mediaPlayersMap.size}")
    groups.forEach { (groupName, sounds) ->
        Timber.d("SoundGroups: group=$groupName, sounds size=${sounds.size}")
    }

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

    Timber.d("ExpandableSoundGroup: group=$groupName, sounds size=${sounds.size}")

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
    val sliderValue = remember { mutableFloatStateOf(buttonData.volume) }

    Timber.d("SoundButton: label=${buttonData.label}, isPlaying=${buttonData.isPlaying.value}")

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
                        text = buttonData.label, // Use the localized label
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
fun TopAppBarContent(
    onTimerClick: () -> Unit,
    onSettingsClick: () -> Unit, // New parameter for settings click
    remainingTime: Int?,
    onCancelTimer: () -> Unit
) {
    TopAppBar(
        title = { Text("Purga") },
        actions = {
            if (remainingTime != null) {
                val minutes = remainingTime / 60
                val seconds = remainingTime % 60
                val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                Text(
                    text = stringResource(R.string.remaining_time, formattedTime),
                    modifier = Modifier.padding(end = 16.dp)
                )
                IconButton(onClick = onCancelTimer) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel_timer)
                    )
                }
            }
            IconButton(onClick = onTimerClick) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = stringResource(R.string.sleep_timer)
                )
            }
            IconButton(onClick = onSettingsClick) { // Settings button
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings)
                )
            }
        }
    )
}

private fun loadSoundResources(context: Context): List<Triple<Int, String, String>> {
    val soundResources = mutableListOf<Triple<Int, String, String>>()
    val rawClass = R.raw::class.java
    val rawFields = rawClass.fields

    for (field in rawFields) {
        val resourceId = field.getInt(null)
        val fileName = context.resources.getResourceEntryName(resourceId)
        val labelResId = context.resources.getIdentifier("sound_${fileName}", "string", context.packageName)
        val label = if (labelResId != 0) context.getString(labelResId) else fileName
        Timber.d("Loaded sound: resourceId=$resourceId, fileName=$fileName, label=$label")
        soundResources.add(Triple(resourceId, fileName, label))
    }

    return soundResources
}

fun setAppLanguage(context: Context, languageCode: String) {
    val locale = when (languageCode) {
        "system" -> Locale.getDefault() // Use system language
        "en" -> Locale("en") // English
        "ru" -> Locale("ru") // Spanish
        // Add more languages as needed
        else -> Locale.getDefault()
    }

    val resources = context.resources
    val configuration = resources.configuration
    configuration.setLocale(locale)
    resources.updateConfiguration(configuration, resources.displayMetrics)

    // Restart the activity to apply the language change
    (context as Activity).recreate()
}

private fun showTimerEndNotification(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        "timer_channel",
        "Timer Notifications",
        NotificationManager.IMPORTANCE_LOW // Low importance ensures no sound or vibration
    ).apply {
        enableVibration(false) // Disable vibration
        setSound(null, null)   // Disable sound
    }
    notificationManager.createNotificationChannel(channel)

    // Build the silent notification
    val notification = NotificationCompat.Builder(context, "timer_channel")
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(context.getString(R.string.sleep_timer_ended))
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority ensures no sound or vibration
        .setSound(null) // Disable sound
        .setVibrate(null) // Disable vibration
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
fun ObserveLifecycle(
    lifecycleOwner: LifecycleOwner,
    viewModel: MainViewModel,
    onStart: () -> Unit = {},
    onStop: () -> Unit = {},
    onDestroy: () -> Unit = {}
) {
    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> onStart()
                Lifecycle.Event.ON_STOP -> {
                    if (!viewModel.playInBackground.value) {
                        viewModel.stopAllPlayers() // Stop sounds if background play is disabled
                    }
                    onStop()
                }
                Lifecycle.Event.ON_DESTROY -> onDestroy()
                else -> {}
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
    val fileName: String, // Original file name
    val label: String,    // Localized label
    var volume: Float = 1.0f,
    var isPlaying: MutableState<Boolean> = mutableStateOf(false) // Track playing state
)

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MainScreen(viewModel = MainViewModel(), onThemeSelected = {}, onNotificationPermissionGranted = {})
}