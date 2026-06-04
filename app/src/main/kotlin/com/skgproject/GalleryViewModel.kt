package com.skgproject

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val repository = GalleryRepository(application)
    private val _uiState = MutableStateFlow(GalleryUiState(rootUri = restoredRootUri(application)))
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        _uiState.value.rootUri?.let(::scan)
    }

    fun setRootDirectory(uri: Uri) {
        val context = getApplication<Application>()
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        prefs.edit().putString(KEY_ROOT_URI, uri.toString()).apply()
        _uiState.update {
            it.copy(
                rootUri = uri,
                selectedAlbum = null,
                viewer = null,
                error = null,
            )
        }
        scan(uri)
    }

    fun refresh() {
        _uiState.value.rootUri?.let(::scan)
    }

    fun openAlbum(album: Album) {
        _uiState.update { it.copy(selectedAlbum = album, viewer = null) }
    }

    fun closeAlbum() {
        _uiState.update { it.copy(selectedAlbum = null, viewer = null) }
    }

    fun openViewer(album: Album, index: Int) {
        if (index !in album.items.indices) return
        _uiState.update { it.copy(viewer = ViewerState(album = album, index = index)) }
    }

    fun showNext() {
        val viewer = _uiState.value.viewer ?: return
        val nextIndex = (viewer.index + 1).coerceAtMost(viewer.album.items.lastIndex)
        _uiState.update { it.copy(viewer = viewer.copy(index = nextIndex)) }
    }

    fun showPrevious() {
        val viewer = _uiState.value.viewer ?: return
        val nextIndex = (viewer.index - 1).coerceAtLeast(0)
        _uiState.update { it.copy(viewer = viewer.copy(index = nextIndex)) }
    }

    fun closeViewer() {
        _uiState.update { it.copy(viewer = null) }
    }

    fun changeDirectory() {
        prefs.edit().remove(KEY_ROOT_URI).apply()
        _uiState.update {
            it.copy(
                rootUri = null,
                albums = emptyList(),
                selectedAlbum = null,
                viewer = null,
                error = null,
                isLoading = false,
            )
        }
    }

    private fun scan(uri: Uri) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.scan(uri) }
                .onSuccess { albums ->
                    _uiState.update { state ->
                        val selected = state.selectedAlbum?.let { current ->
                            albums.firstOrNull { it.uri == current.uri }
                        }
                        state.copy(
                            albums = albums,
                            selectedAlbum = selected,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "目录读取失败",
                        )
                    }
                }
        }
    }

    private fun restoredRootUri(context: Context): Uri? {
        val stored = prefs.getString(KEY_ROOT_URI, null) ?: return null
        val uri = Uri.parse(stored)
        val hasPermission = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        return uri.takeIf { hasPermission }
    }

    private companion object {
        const val PREFS_NAME = "skgproject_gallery"
        const val KEY_ROOT_URI = "root_uri"
    }
}

