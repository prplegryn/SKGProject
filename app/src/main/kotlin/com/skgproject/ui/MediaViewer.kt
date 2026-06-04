package com.skgproject.ui

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.skgproject.MediaFile
import com.skgproject.ViewerState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
fun MediaViewer(
    viewer: ViewerState,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val item = viewer.item
    var controlsVisible by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (item.isVideo) {
            VideoViewer(
                item = item,
                controlsVisible = controlsVisible,
                onToggleControls = { controlsVisible = !controlsVisible },
            )
        } else {
            ZoomableImage(
                item = item,
                onToggleControls = { controlsVisible = !controlsVisible },
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ViewerChrome(
                title = item.name,
                indexLabel = "${viewer.index + 1}/${viewer.album.items.size}",
                canGoPrevious = viewer.index > 0,
                canGoNext = viewer.index < viewer.album.items.lastIndex,
                onClose = onClose,
                onPrevious = onPrevious,
                onNext = onNext,
            )
        }
    }
}

@Composable
private fun ZoomableImage(
    item: MediaFile,
    onToggleControls: () -> Unit,
) {
    var scale by remember(item.uri) { mutableStateOf(1f) }
    var offset by remember(item.uri) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.uri) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = nextScale
                    offset = if (nextScale == 1f) Offset.Zero else offset + pan
                }
            }
            .pointerInput(item.uri) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.4f
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun VideoViewer(
    item: MediaFile,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
) {
    val context = LocalContext.current
    var loop by remember(item.uri) { mutableStateOf(false) }
    var playing by remember(item.uri) { mutableStateOf(true) }
    var duration by remember(item.uri) { mutableLongStateOf(0L) }
    var position by remember(item.uri) { mutableLongStateOf(0L) }
    var buffered by remember(item.uri) { mutableLongStateOf(0L) }

    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(item.uri))
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    LaunchedEffect(player, loop) {
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(player, playing) {
        if (playing) player.play() else player.pause()
    }

    LaunchedEffect(player) {
        while (true) {
            val currentDuration = player.duration.takeIf { it > 0L } ?: 0L
            duration = currentDuration
            position = player.currentPosition.coerceAtLeast(0L)
            buffered = player.bufferedPosition.coerceAtLeast(0L)
            playing = player.isPlaying || player.playWhenReady
            delay(250)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(duration) {
                    detectTapGestures(onTap = { onToggleControls() })
                }
                .pointerInput(duration) {
                    detectDragGestures { change, dragAmount ->
                        if (duration > 0L && abs(dragAmount.x) >= abs(dragAmount.y)) {
                            change.consume()
                            val delta = ((dragAmount.x / size.width) * 90_000L).roundToLong()
                            player.seekTo((player.currentPosition + delta).coerceIn(0L, duration))
                        }
                    }
                },
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            VideoControls(
                playing = playing,
                loop = loop,
                position = position.coerceAtMost(duration.takeIf { it > 0L } ?: position),
                duration = duration,
                buffered = buffered,
                onPlayPause = { playing = !playing },
                onLoopToggle = { loop = !loop },
                onSeek = { player.seekTo(it) },
            )
        }
    }
}

@Composable
private fun ViewerChrome(
    title: String,
    indexLabel: String,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassIconButton(onClick = onClose) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "关闭")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = indexLabel,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                onClick = onPrevious,
                enabled = canGoPrevious,
            ) {
                Icon(imageVector = Icons.Rounded.ArrowBackIosNew, contentDescription = "上一项")
            }
            GlassIconButton(
                onClick = onNext,
                enabled = canGoNext,
            ) {
                Icon(imageVector = Icons.Rounded.ArrowForwardIos, contentDescription = "下一项")
            }
        }
    }
}

@Composable
private fun VideoControls(
    playing: Boolean,
    loop: Boolean,
    position: Long,
    duration: Long,
    buffered: Long,
    onPlayPause: () -> Unit,
    onLoopToggle: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProgressScrubber(
            position = position,
            duration = duration,
            buffered = buffered,
            onSeek = onSeek,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassIconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "暂停" else "播放",
                )
            }
            Text(
                text = "${position.formatTime()} / ${duration.formatTime()}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            GlassIconButton(
                onClick = onLoopToggle,
                selected = loop,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AllInclusive,
                    contentDescription = if (loop) "关闭循环" else "开启循环",
                )
            }
        }
    }
}

@Composable
private fun ProgressScrubber(
    position: Long,
    duration: Long,
    buffered: Long,
    onSeek: (Long) -> Unit,
) {
    val primary = Color.White
    val secondary = Color.White.copy(alpha = 0.35f)
    val track = Color.White.copy(alpha = 0.16f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(duration) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (duration > 0L) onSeek((duration * (offset.x / size.width)).roundToLong().coerceIn(0L, duration))
                    },
                    onDrag = { change, _ ->
                        if (duration > 0L) {
                            change.consume()
                            onSeek((duration * (change.position.x / size.width)).roundToLong().coerceIn(0L, duration))
                        }
                    },
                )
            },
    ) {
        val centerY = size.height / 2f
        val trackHeight = 3.dp.toPx()
        val progressFraction = if (duration > 0L) position.toFloat() / duration else 0f
        val bufferedFraction = if (duration > 0L) buffered.toFloat() / duration else 0f

        drawRoundRect(
            color = track,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight, trackHeight),
        )
        drawRoundRect(
            color = secondary,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width * bufferedFraction.coerceIn(0f, 1f), trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight, trackHeight),
        )
        drawRoundRect(
            color = primary,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width * progressFraction.coerceIn(0f, 1f), trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight, trackHeight),
        )
        drawCircle(
            color = primary,
            radius = 5.dp.toPx(),
            center = Offset(size.width * progressFraction.coerceIn(0f, 1f), centerY),
        )
    }
}

@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        enabled = enabled,
        shape = CircleShape,
        color = when {
            selected -> Teal.copy(alpha = 0.95f)
            else -> Color.White.copy(alpha = 0.14f)
        },
        contentColor = Color.White.copy(alpha = if (enabled) 1f else 0.32f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

private fun Long.formatTime(): String {
    val safeValue = coerceAtLeast(0L) / 1000L
    val seconds = safeValue % 60
    val minutes = (safeValue / 60) % 60
    val hours = safeValue / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
