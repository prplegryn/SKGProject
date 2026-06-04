package com.skgproject.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import com.skgproject.MediaFile
import java.io.File

@Composable
fun MediaThumbnail(
    item: MediaFile,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val model = remember(item.uri, item.thumbnailPath, item.isVideo, context) {
        val cached = item.thumbnailPath?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
        ImageRequest.Builder(context)
            .data(cached ?: item.uri)
            .apply {
                if (cached == null && item.isVideo) {
                    videoFrameMillis(650)
                }
            }
            .build()
    }
    val painter = rememberAsyncImagePainter(model = model)
    val painterState by painter.state.collectAsState()
    val isReady = painterState is AsyncImagePainter.State.Success

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AnimatedVisibility(
            visible = isReady,
            enter = fadeIn(tween(180)) + scaleIn(
                initialScale = 1.025f,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
            ),
        ) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}
