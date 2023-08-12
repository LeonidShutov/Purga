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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen()
        }
    }
}

@Composable
fun MainScreen() {
    val soundResources = mutableListOf<Pair<Int, String>>()
    val context = LocalContext.current

    val rawClass = R.raw::class.java

    val rawFields = rawClass.fields

    for (field in rawFields) {
        val resourceId = field.getInt(null)
        val fileName = context.resources.getResourceEntryName(resourceId)
        soundResources.add(resourceId to fileName)
    }

    val buttonsMap = remember { mutableMapOf<Int, ButtonData>() }
    DisposableEffect(Unit) {
        onDispose {
            buttonsMap.values.forEach { buttonData ->
                buttonData.mediaPlayer.release()
            }
        }
    }

    soundResources.forEach { (rawResourceId, fileName) ->
        buttonsMap.getOrPut(rawResourceId) {
            val mediaPlayer = MediaPlayer.create(context, rawResourceId)
            mediaPlayer.setOnCompletionListener {
                val buttonData = buttonsMap[rawResourceId]
                buttonData?.let {
                    it.lastPosition = 0 // Reset the last position when audio completes
                    prepareAndPlaySound(it) // Restart the audio playback
                }
            }
            ButtonData(
                mediaPlayer = mediaPlayer,
                context = context,
                rawResourceId = rawResourceId,
                fileName = fileName
            )
        }
    }

    val verticalScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(verticalScrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        buttonsMap.values.forEach { buttonData ->
            MediaButton(buttonData = buttonData)
        }
    }

    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                buttonsMap.values.forEach { buttonData ->
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
@Composable
fun MediaButton(
    buttonData: ButtonData
) {
    Button(
        onClick = {
            val mediaPlayer = buttonData.mediaPlayer
            if (!mediaPlayer.isPlaying) {
                prepareAndPlaySound(buttonData)
            }
            else {
                // If the media player is playing, pause it and save the current position
                buttonData.lastPosition = mediaPlayer.currentPosition
                mediaPlayer.pause()
            }
        },

        colors = ButtonDefaults.buttonColors(
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(
            painter = painterResource(id = if (buttonData.mediaPlayer.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = buttonData.fileName, // Display the file name
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}


private fun prepareAndPlaySound(buttonData: ButtonData) {
    val mediaPlayer = buttonData.mediaPlayer
    val context = buttonData.context
    val rawResourceId = buttonData.rawResourceId

    mediaPlayer.reset()
    val rawFileDescriptor = context.resources.openRawResourceFd(rawResourceId)
    mediaPlayer.setDataSource(rawFileDescriptor.fileDescriptor, rawFileDescriptor.startOffset, rawFileDescriptor.length)
    mediaPlayer.prepare()
    if (buttonData.lastPosition > 0) {
        mediaPlayer.seekTo(buttonData.lastPosition)
    }
    mediaPlayer.start()
}

data class ButtonData(
    val mediaPlayer: MediaPlayer,
    var lastPosition: Int = 0,
    val context: Context,
    val rawResourceId: Int,
    val fileName: String
)

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MainScreen()
}