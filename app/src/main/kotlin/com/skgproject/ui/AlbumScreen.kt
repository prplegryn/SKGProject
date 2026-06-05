package com.skgproject.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skgproject.Album
import com.skgproject.MediaFile

@Composable
fun AlbumScreen(
    album: Album,
    onBack: () -> Unit,
    onMediaClick: (Int) -> Unit,
    onPickBackground: () -> Unit,
    onPickHomeCover: () -> Unit,
) {
    var heroHeightPx by remember(album.uri) { mutableStateOf(1f) }
    val gridState = rememberLazyGridState()
    val surfaceColor = album.albumSurfaceColor()
    val mediaGap = with(LocalDensity.current) { 2f.toDp() }
    val collapseFraction by remember {
        derivedStateOf {
            if (gridState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (gridState.firstVisibleItemScrollOffset / (heroHeightPx * 0.62f)).coerceIn(0f, 1f)
            }
        }
    }
    val topBarAlpha by remember {
        derivedStateOf {
            ((collapseFraction - 0.42f) / 0.58f).coerceIn(0f, 1f)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor),
    ) {
        val heroHeight = maxHeight * 0.5f

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceColor),
            contentPadding = PaddingValues(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(mediaGap),
            verticalArrangement = Arrangement.spacedBy(mediaGap),
        ) {
            item(
                key = "album-hero",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                AlbumHero(
                    album = album,
                    surfaceColor = surfaceColor,
                    height = heroHeight,
                    collapseFraction = collapseFraction,
                    onBack = onBack,
                    onPickBackground = onPickBackground,
                    onPickHomeCover = onPickHomeCover,
                    modifier = Modifier.onSizeChanged { size ->
                        heroHeightPx = size.height.toFloat().coerceAtLeast(1f)
                    },
                )
            }
            itemsIndexed(album.items, key = { _, item -> item.uri.toString() }) { index, item ->
                MediaTile(
                    item = item,
                    onClick = { onMediaClick(index) },
                )
            }
        }

        AnimatedVisibility(
            visible = topBarAlpha > 0.01f,
            enter = fadeIn(tween(120, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(90)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            CompactAlbumBar(
                album = album,
                surfaceColor = surfaceColor,
                alpha = topBarAlpha,
                onBack = onBack,
                onPickBackground = onPickBackground,
                onPickHomeCover = onPickHomeCover,
            )
        }
    }
}

@Composable
private fun AlbumHero(
    album: Album,
    surfaceColor: Color,
    height: androidx.compose.ui.unit.Dp,
    collapseFraction: Float,
    onBack: () -> Unit,
    onPickBackground: () -> Unit,
    onPickHomeCover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = album.background

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(surfaceColor),
    ) {
        if (background != null) {
            MediaThumbnail(
                item = background,
                contentDescription = album.name,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = 1f - collapseFraction * 0.58f
                        scaleX = 1f + collapseFraction * 0.05f
                        scaleY = 1f + collapseFraction * 0.05f
                    },
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.34f),
                        0.42f to Color.Black.copy(alpha = 0.08f),
                        0.78f to Color.Black.copy(alpha = 0.26f),
                        1f to surfaceColor,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(124.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.46f to surfaceColor.copy(alpha = 0.74f),
                        1f to surfaceColor,
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, bottom = 20.dp)
                .graphicsLayer {
                    alpha = 1f - collapseFraction
                    translationY = -28f * collapseFraction
                    scaleX = 1f - 0.05f * collapseFraction
                    scaleY = 1f - 0.05f * collapseFraction
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AlbumChromeButton(onClick = onBack) {
                Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "返回")
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${album.items.size} 个项目",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AlbumOverflowButton(
                onPickBackground = onPickBackground,
                onPickHomeCover = onPickHomeCover,
            )
        }
    }
}

@Composable
private fun CompactAlbumBar(
    album: Album,
    surfaceColor: Color,
    alpha: Float,
    onBack: () -> Unit,
    onPickBackground: () -> Unit,
    onPickHomeCover: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceColor.copy(alpha = 0.86f * alpha))
            .statusBarsPadding()
            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AlbumOverflowButton(
            onPickBackground = onPickBackground,
            onPickHomeCover = onPickHomeCover,
        )
    }
}

@Composable
private fun AlbumOverflowButton(
    onPickBackground: () -> Unit,
    onPickHomeCover: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        AlbumChromeButton(onClick = { expanded = true }) {
            Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "相册设置")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(text = "设置背景") },
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.FolderOpen, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onPickBackground()
                },
            )
            DropdownMenuItem(
                text = { Text(text = "设置主页封面") },
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.FolderOpen, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onPickHomeCover()
                },
            )
        }
    }
}

@Composable
private fun MediaTile(
    item: MediaFile,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
    ) {
        MediaThumbnail(
            item = item,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.44f),
                contentColor = Color.White,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AlbumChromeButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.32f),
        contentColor = Color.White,
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

private fun Album.albumSurfaceColor(): Color =
    Color(backgroundColor ?: DEFAULT_ALBUM_SURFACE_COLOR)

private const val DEFAULT_ALBUM_SURFACE_COLOR = 0xff151310L
