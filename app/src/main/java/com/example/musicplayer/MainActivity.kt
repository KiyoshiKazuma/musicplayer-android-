package com.example.musicplayer

import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.musicplayer.ui.theme.MusicPlayerTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusicPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicPlayer(context = this)
                }
            }
        }
    }
}

@Composable
fun MusicPlayer(context: Context) {
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    var isPlaying by remember { mutableStateOf(false) }
    var filename by remember { mutableStateOf("ファイル未選択") }
    var metadata by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                filename = getFileName(context, it) // ファイル名を取得
                metadata = getMp3Metadata(context, it) // 🔹 ファイル名を取得
                exoPlayer.setMediaItem(MediaItem.fromUri(it))
                exoPlayer.prepare()
                exoPlayer.play()
                isPlaying = true
            }
        }

    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(1L) }
    var sliderPosition by remember { mutableStateOf(0f) }
    var isUserSeeking by remember { mutableStateOf(false) } // シーク中かどうか

    // 0.5s 毎に ExoPlayer の再生時間情報を取得
    LaunchedEffect(exoPlayer, isUserSeeking) {
        while (!isUserSeeking) { // ユーザーがシーク中でない時のみ更新
            position = exoPlayer.currentPosition
            duration = exoPlayer.duration.takeIf { it > 0 } ?: 1L
            sliderPosition = position.toFloat() // スライダーの位置を更新
            delay(500)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        PlayerDisplay(
            filename = filename,
            metadata = metadata,
            position = position,
            duration = duration,
            sliderPosition = sliderPosition,
            onSeekChanged = { newPosition -> sliderPosition = newPosition }, // スライダー移動
            onSeekEnd = { newPosition ->
                isUserSeeking = false
                exoPlayer.seekTo(newPosition.toLong()) // シーク完了時にシーク
            }
        )
        if (isPlaying) {
            Button(onClick = {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                } else {
                    exoPlayer.play()
                }
                isPlaying = exoPlayer.isPlaying
            }) {
                Text(if (isPlaying) "||" else "▶")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(onClick = { launcher.launch("audio/*") }) {
            Text("音楽を選択")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
}

@Composable
fun PlayerDisplay(
    filename : String,
    metadata : Map<String, String>,
    position: Long,
    duration: Long,
    sliderPosition: Float,
    onSeekChanged: (Float) -> Unit,
    onSeekEnd: (Float) -> Unit
) {
    if (duration > 1L) { // 音楽が選択された場合のみ表示
        Text(text = "ファイル名 : ${filename}")
        Text(text = "タイトル : ${metadata["title"]}")
        Text(text = "アーティスト : ${metadata["artist"]}")
        Text(text = "アルバム名 : ${metadata["album"]}")
        Text("再生時間: ${formatTime(position)} / ${formatTime(duration)}")

        Slider(
            value = sliderPosition,
            onValueChange = { newPosition ->
                onSeekChanged(newPosition) // スライダー移動時に更新
            },
            onValueChangeFinished = {
                onSeekEnd(sliderPosition) // シーク完了時に再生位置を変更
            },
            valueRange = 0f..duration.toFloat()
        )
    }
}

// 時間フォーマット（ミリ秒 → "mm:ss"）
fun formatTime(ms: Long): String {
    val minutes = (ms / 1000) / 60
    val seconds = (ms / 1000) % 60
    return "%02d:%02d".format(minutes, seconds)
}

fun getFileName(context: Context, uri: Uri): String {
    var name = "unknown"
    val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                name = it.getString(nameIndex)
            }
        }
    }
    return name
}

fun getMp3Metadata(context: Context, uri: Uri): Map<String, String> {
    val retriever = MediaMetadataRetriever()
    val metadata = mutableMapOf<String, String>()

    try {
        // Uri をセット
        context.contentResolver.openFileDescriptor(uri, "r")?.fileDescriptor?.let {
            retriever.setDataSource(it)
        }

        // メタデータを取得
        metadata["title"] = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "Unknown Title"
        metadata["artist"] = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
        metadata["album"] = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
        metadata["genre"] = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: "Unknown Genre"
        metadata["duration"] = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "Unknown Duration"

        // デバッグログ追加
        Log.d("Metadata", "Title: ${metadata["title"]}, Artist: ${metadata["artist"]}, Album: ${metadata["album"]}")

    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        retriever.release()
    }

    return metadata
}