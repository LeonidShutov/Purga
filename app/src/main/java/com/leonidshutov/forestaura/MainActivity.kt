package com.leonidshutov.forestaura

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.stringResource
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
    val mediaPlayer1 = remember { MediaPlayer() }
    val mediaPlayer2 = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer1.release()
            mediaPlayer2.release()
        }
    }

    val context = LocalContext.current
    var isPlaying1 by remember { mutableStateOf(false) }
    var isPlaying2 by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MediaButton(
            isPlaying = isPlaying1,
            mediaPlayer = mediaPlayer1,
            context = context,
            rawResourceId = R.raw.rainforest,
            onToggle = { isPlaying1 = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        MediaButton(
            isPlaying = isPlaying2,
            mediaPlayer = mediaPlayer2,
            context = context,
            rawResourceId = R.raw.humpbackwhale,
            onToggle = { isPlaying2 = it }
        )
    }

    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                mediaPlayer1.pause()
                mediaPlayer2.pause()
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
    isPlaying: Boolean,
    mediaPlayer: MediaPlayer,
    context: Context,
    rawResourceId: Int,
    onToggle: (Boolean) -> Unit
) {
    Button(
        onClick = {
            onToggle(!isPlaying) // Toggle the state before handling playback
            if (!isPlaying) { // Check the updated state
                mediaPlayer.reset()
                val rawFileDescriptor = context.resources.openRawResourceFd(rawResourceId)
                mediaPlayer.setDataSource(rawFileDescriptor.fileDescriptor, rawFileDescriptor.startOffset, rawFileDescriptor.length)
                mediaPlayer.prepare()
                mediaPlayer.start()
            } else {
                mediaPlayer.pause()
            }
        },
        colors = ButtonDefaults.buttonColors(
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(
            painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(id = if (isPlaying) R.string.pause else R.string.play),
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MainScreen()
}