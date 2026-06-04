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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skgproject.GalleryUiState
import com.skgproject.GalleryViewModel

@Composable
fun SKGProjectApp(viewModel: GalleryViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val directoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let(viewModel::setRootDirectory)
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
            )

            AnimatedVisibility(
                visible = state.viewer != null,
                enter = fadeIn(tween(110)) + scaleIn(
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    initialScale = 0.92f,
                ),
                exit = fadeOut(tween(120)) + scaleOut(
                    animationSpec = tween(140, easing = FastOutSlowInEasing),
                    targetScale = 0.98f,
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
            )
        }
    }
}

private fun pageForward(): ContentTransform =
    (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { width -> width / 4 } + fadeIn(tween(180)))
        .togetherWith(slideOutHorizontally(tween(180)) { width -> -width / 8 } + fadeOut(tween(120)))

private fun pageBack(): ContentTransform =
    (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { width -> -width / 5 } + fadeIn(tween(160)))
        .togetherWith(slideOutHorizontally(tween(160)) { width -> width / 5 } + fadeOut(tween(120)))

