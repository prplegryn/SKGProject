package com.skgproject.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skgproject.Album

@Composable
fun SetupScreen(onPickDirectory: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = RoundedCornerShape(8.dp),
                color = Ink,
                contentColor = Paper,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SKGProject",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "选择一个读取目录开始浏览相册",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onPickDirectory,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(text = "设置读取目录")
            }
        }
    }
}

@Composable
fun HomeScreen(
    albums: List<Album>,
    isLoading: Boolean,
    error: String?,
    onAlbumClick: (Album) -> Unit,
    onRefresh: () -> Unit,
    onChangeDirectory: () -> Unit,
) {
    var settingsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        GalleryTopBar(
            title = "SKGProject",
            subtitle = "${albums.size} 个相册",
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "刷新",
                    )
                }
                Box {
                    IconButton(onClick = { settingsExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "设置",
                        )
                    }
                    DropdownMenu(
                        expanded = settingsExpanded,
                        onDismissRequest = { settingsExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = "更换读取目录") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                settingsExpanded = false
                                onChangeDirectory()
                            },
                        )
                    }
                }
            },
        )

        AnimatedVisibility(visible = isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = "正在读取目录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (albums.isEmpty() && !isLoading) {
            EmptyHome(error = error, onChangeDirectory = onChangeDirectory)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 124.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(albums, key = { it.uri.toString() }) { album ->
                    AlbumTile(album = album, onClick = { onAlbumClick(album) })
                }
            }
        }
    }
}

@Composable
private fun EmptyHome(error: String?, onChangeDirectory: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = error ?: "当前目录没有可读取相册",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Button(
                onClick = onChangeDirectory,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(text = "重新选择目录")
            }
        }
    }
}

@Composable
private fun AlbumTile(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val cover = album.cover
            if (cover != null) {
                MediaThumbnail(
                    item = cover,
                    contentDescription = album.name,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.58f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.48f),
                            ),
                        ),
                    ),
            )
            if (album.items.any { it.isVideo }) {
                VideoBadge(modifier = Modifier.align(Alignment.TopEnd))
            }
            Text(
                text = "${album.items.size}",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(9.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = mediaSummary(album),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun VideoBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(7.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.52f),
        contentColor = Color.White,
    ) {
        Icon(
            imageVector = Icons.Rounded.VideoLibrary,
            contentDescription = null,
            modifier = Modifier
                .padding(6.dp)
                .size(14.dp),
        )
    }
}

private fun mediaSummary(album: Album): String {
    val videos = album.items.count { it.isVideo }
    val photos = album.items.size - videos
    return when {
        videos == 0 -> "$photos 张照片"
        photos == 0 -> "$videos 个视频"
        else -> "$photos 张照片 · $videos 个视频"
    }
}
