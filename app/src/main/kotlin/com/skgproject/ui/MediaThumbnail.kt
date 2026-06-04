package com.skgproject.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import com.skgproject.MediaFile

@Composable
fun MediaThumbnail(
    item: MediaFile,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val request = remember(item.uri, item.isVideo, context) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .apply {
                if (item.isVideo) {
                    videoFrameMillis(650)
                }
            }
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

