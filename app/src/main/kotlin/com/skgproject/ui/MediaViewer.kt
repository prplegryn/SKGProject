package com.skgproject.ui

import android.os.SystemClock
import android.view.ViewGroup
import androidx.annotation.OptIn
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ScrubbingModeParameters
import androidx.media3.exoplayer.SeekParameters
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
@OptIn(UnstableApi::class)
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
    var previewSeekPosition by remember(item.uri) { mutableStateOf<Long?>(null) }
    var screenDragStartPosition by remember(item.uri) { mutableLongStateOf(0L) }
    var screenDragOffset by remember(item.uri) { mutableStateOf(0f) }
    var wasPlayingBeforeScrub by remember(item.uri) { mutableStateOf(false) }
    var isScrubbing by remember(item.uri) { mutableStateOf(false) }
    var finalSeekPosition by remember(item.uri) { mutableLongStateOf(0L) }
    var pendingSeekPosition by remember(item.uri) { mutableLongStateOf(NO_PENDING_SEEK) }
    var lastSentSeekPosition by remember(item.uri) { mutableLongStateOf(NO_PENDING_SEEK) }
    var lastSeekDispatchTime by remember(item.uri) { mutableLongStateOf(0L) }

    val player = remember(item.uri) {
        val renderersFactory = DefaultRenderersFactory(context)
            .forceEnableMediaCodecAsynchronousQueueing()
        ExoPlayer.Builder(context, renderersFactory).build().apply {
            setMediaItem(MediaItem.fromUri(item.uri))
            setScrubbingModeParameters(ScrubbingModeParameters.DEFAULT)
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
            if (previewSeekPosition == null) {
                position = player.currentPosition.coerceAtLeast(0L)
            }
            buffered = player.bufferedPosition.coerceAtLeast(0L)
            playing = player.isPlaying || player.playWhenReady
            delay(40)
        }
    }

    fun pushPendingSeekToPlayer(force: Boolean = false) {
        val target = pendingSeekPosition
        if (target == NO_PENDING_SEEK) return

        val now = SystemClock.uptimeMillis()
        val distance = if (lastSentSeekPosition == NO_PENDING_SEEK) {
            Long.MAX_VALUE
        } else {
            abs(target - lastSentSeekPosition)
        }
        if (force || now - lastSeekDispatchTime >= LIVE_SEEK_DISPATCH_MS || distance >= LIVE_SEEK_MIN_DISTANCE_MS) {
            player.seekTo(target)
            lastSentSeekPosition = target
            lastSeekDispatchTime = now
        }
    }

    LaunchedEffect(player, isScrubbing) {
        while (isScrubbing) {
            pushPendingSeekToPlayer()
            delay(LIVE_SEEK_DISPATCH_MS)
        }
    }

    fun liveSeekTo(target: Long, forcePlayerSeek: Boolean = false) {
        if (duration <= 0L) return
        val nextPosition = target.coerceIn(0L, duration)
        previewSeekPosition = nextPosition
        finalSeekPosition = nextPosition
        position = nextPosition
        pendingSeekPosition = nextPosition
        pushPendingSeekToPlayer(forcePlayerSeek)
    }

    fun startLiveSeek(target: Long) {
        if (duration <= 0L) return
        if (!isScrubbing) {
            wasPlayingBeforeScrub = player.isPlaying || player.playWhenReady
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            player.setScrubbingModeEnabled(true)
            isScrubbing = true
            lastSentSeekPosition = NO_PENDING_SEEK
            lastSeekDispatchTime = 0L
        }
        liveSeekTo(target, forcePlayerSeek = true)
    }

    fun finishLiveSeek() {
        val safeDuration = duration.takeIf { it > 0L } ?: finalSeekPosition
        val target = finalSeekPosition.coerceIn(0L, safeDuration)
        pendingSeekPosition = target
        player.setSeekParameters(SeekParameters.EXACT)
        player.seekTo(target)
        lastSentSeekPosition = target
        lastSeekDispatchTime = SystemClock.uptimeMillis()
        isScrubbing = false
        player.setScrubbingModeEnabled(false)
        previewSeekPosition = null
        pendingSeekPosition = NO_PENDING_SEEK
        player.playWhenReady = wasPlayingBeforeScrub
        playing = wasPlayingBeforeScrub
    }

    fun cancelLiveSeek() {
        isScrubbing = false
        player.setScrubbingModeEnabled(false)
        player.setSeekParameters(SeekParameters.DEFAULT)
        previewSeekPosition = null
        pendingSeekPosition = NO_PENDING_SEEK
        player.playWhenReady = wasPlayingBeforeScrub
        playing = wasPlayingBeforeScrub
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
                    detectDragGestures(
                        onDragStart = {
                            val startPosition = previewSeekPosition ?: player.currentPosition
                            screenDragStartPosition = startPosition
                            screenDragOffset = 0f
                            startLiveSeek(startPosition)
                        },
                        onDragEnd = {
                            finishLiveSeek()
                            screenDragOffset = 0f
                        },
                        onDragCancel = {
                            cancelLiveSeek()
                            screenDragOffset = 0f
                        },
                        onDrag = { change, dragAmount ->
                            if (duration > 0L && abs(dragAmount.x) >= abs(dragAmount.y)) {
                                change.consume()
                                screenDragOffset += dragAmount.x
                                val delta = ((screenDragOffset / size.width) * SCREEN_SCRUB_RANGE_MS).roundToLong()
                                liveSeekTo(screenDragStartPosition + delta)
                            }
                        },
                    )
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
                position = (previewSeekPosition ?: position).coerceAtMost(duration.takeIf { it > 0L } ?: position),
                duration = duration,
                buffered = buffered,
                onPlayPause = { playing = !playing },
                onLoopToggle = { loop = !loop },
                onSeekStart = { startLiveSeek(it) },
                onSeekMove = { liveSeekTo(it) },
                onSeekEnd = { finishLiveSeek() },
                onSeekCancel = { cancelLiveSeek() },
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
    onSeekStart: (Long) -> Unit,
    onSeekMove: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    onSeekCancel: () -> Unit,
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
            onSeekStart = onSeekStart,
            onSeekMove = onSeekMove,
            onSeekEnd = onSeekEnd,
            onSeekCancel = onSeekCancel,
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
    onSeekStart: (Long) -> Unit,
    onSeekMove: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    onSeekCancel: () -> Unit,
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
                        if (duration > 0L) {
                            val target = (duration * (offset.x / size.width)).roundToLong().coerceIn(0L, duration)
                            onSeekStart(target)
                        }
                    },
                    onDragEnd = { if (duration > 0L) onSeekEnd() },
                    onDragCancel = { onSeekCancel() },
                    onDrag = { change, _ ->
                        if (duration > 0L) {
                            change.consume()
                            val target = (duration * (change.position.x / size.width)).roundToLong().coerceIn(0L, duration)
                            onSeekMove(target)
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

private const val SCREEN_SCRUB_RANGE_MS = 5_000L
private const val LIVE_SEEK_DISPATCH_MS = 16L
private const val LIVE_SEEK_MIN_DISTANCE_MS = 24L
private const val NO_PENDING_SEEK = -1L
