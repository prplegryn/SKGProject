package com.skgproject.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skgproject.Album
import com.skgproject.GalleryUiState
import com.skgproject.GalleryViewModel

@Composable
fun SKGProjectApp(viewModel: GalleryViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingAlbumPick by remember { mutableStateOf<AlbumPickRequest?>(null) }
    val directoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let(viewModel::setRootDirectory)
    }
    val mediaDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val request = pendingAlbumPick
        pendingAlbumPick = null
        if (uri != null && request != null) {
            when (request.target) {
                AlbumPickTarget.Background -> viewModel.setAlbumBackground(request.album, uri)
                AlbumPickTarget.HomeCover -> viewModel.setAlbumHomeCover(request.album, uri)
            }
        }
    }

    SKGProjectTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            GallerySurface(
                state = state,
                onPickDirectory = { directoryLauncher.launch(null) },
                onChangeDirectory = {
                    viewModel.changeDirectory()
                    directoryLauncher.launch(null)
                },
                onRefresh = viewModel::refresh,
                onAlbumClick = viewModel::openAlbum,
                onBackToHome = viewModel::closeAlbum,
                onMediaClick = viewModel::openViewer,
                onPickAlbumBackground = { album ->
                    pendingAlbumPick = AlbumPickRequest(album, AlbumPickTarget.Background)
                    mediaDocumentLauncher.launch(MEDIA_PICK_MIME_TYPES)
                },
                onPickAlbumHomeCover = { album ->
                    pendingAlbumPick = AlbumPickRequest(album, AlbumPickTarget.HomeCover)
                    mediaDocumentLauncher.launch(MEDIA_PICK_MIME_TYPES)
                },
            )

            AnimatedVisibility(
                visible = state.viewer != null,
                enter = fadeIn(tween(140)) + scaleIn(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    initialScale = 0.82f,
                ),
                exit = fadeOut(tween(120)) + scaleOut(
                    animationSpec = tween(160, easing = FastOutSlowInEasing),
                    targetScale = 0.92f,
                ),
            ) {
                val viewer = state.viewer
                if (viewer != null) {
                    MediaViewer(
                        viewer = viewer,
                        onClose = viewModel::closeViewer,
                        onNext = viewModel::showNext,
                        onPrevious = viewModel::showPrevious,
                    )
                }
            }
        }
    }

    BackHandler(enabled = state.viewer != null) {
        viewModel.closeViewer()
    }
    BackHandler(enabled = state.viewer == null && state.selectedAlbum != null) {
        viewModel.closeAlbum()
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun GallerySurface(
    state: GalleryUiState,
    onPickDirectory: () -> Unit,
    onChangeDirectory: () -> Unit,
    onRefresh: () -> Unit,
    onAlbumClick: (com.skgproject.Album) -> Unit,
    onBackToHome: () -> Unit,
    onMediaClick: (com.skgproject.Album, Int) -> Unit,
    onPickAlbumBackground: (com.skgproject.Album) -> Unit,
    onPickAlbumHomeCover: (com.skgproject.Album) -> Unit,
) {
    AnimatedContent(
        targetState = state.selectedAlbum,
        transitionSpec = {
            if (targetState != null) {
                pageForward()
            } else {
                pageBack()
            }
        },
        label = "gallery-pages",
    ) { selectedAlbum ->
        when {
            state.needsDirectory -> SetupScreen(onPickDirectory = onPickDirectory)
            selectedAlbum == null -> HomeScreen(
                albums = state.albums,
                isLoading = state.isLoading,
                error = state.error,
                onAlbumClick = onAlbumClick,
                onRefresh = onRefresh,
                onChangeDirectory = onChangeDirectory,
            )
            else -> AlbumScreen(
                album = selectedAlbum,
                onBack = onBackToHome,
                onMediaClick = { index -> onMediaClick(selectedAlbum, index) },
                onPickBackground = { onPickAlbumBackground(selectedAlbum) },
                onPickHomeCover = { onPickAlbumHomeCover(selectedAlbum) },
            )
        }
    }
}

private data class AlbumPickRequest(
    val album: Album,
    val target: AlbumPickTarget,
)

private enum class AlbumPickTarget {
    Background,
    HomeCover,
}

private val MEDIA_PICK_MIME_TYPES = arrayOf("image/*", "video/*")

private fun pageForward(): ContentTransform =
    (fadeIn(tween(180)) + scaleIn(tween(260, easing = FastOutSlowInEasing), initialScale = 0.92f))
        .togetherWith(fadeOut(tween(120)) + scaleOut(tween(180, easing = FastOutSlowInEasing), targetScale = 1.04f))

private fun pageBack(): ContentTransform =
    (fadeIn(tween(130)) + scaleIn(tween(180, easing = FastOutSlowInEasing), initialScale = 1.02f))
        .togetherWith(fadeOut(tween(110)) + scaleOut(tween(150, easing = FastOutSlowInEasing), targetScale = 0.94f))
