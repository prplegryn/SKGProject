package com.skgproject

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    private var thumbnailJob: Job? = null

    init {
        _uiState.value.rootUri?.let(::loadCachedGallery)
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

    fun setAlbumBackground(album: Album, uri: Uri) {
        val rootUri = _uiState.value.rootUri ?: return
        persistReadPermission(uri)
        viewModelScope.launch {
            val media = repository.mediaFileForPickedUri(uri, album.name) ?: return@launch
            val mediaWithThumbnail = repository.ensureThumbnail(media) ?: media
            val surfaceColor = repository.extractAlbumSurfaceColor(mediaWithThumbnail)
            if (_uiState.value.rootUri != rootUri) return@launch
            updateAlbum(album.uri) {
                it.copy(
                    backgroundMedia = mediaWithThumbnail,
                    backgroundColor = surfaceColor ?: it.backgroundColor,
                )
            }
            repository.saveIndex(rootUri, _uiState.value.albums)
        }
    }

    fun setAlbumHomeCover(album: Album, uri: Uri) {
        val rootUri = _uiState.value.rootUri ?: return
        persistReadPermission(uri)
        viewModelScope.launch {
            val media = repository.mediaFileForPickedUri(uri, album.name) ?: return@launch
            val mediaWithThumbnail = repository.ensureThumbnail(media) ?: media
            if (_uiState.value.rootUri != rootUri) return@launch
            updateAlbum(album.uri) {
                it.copy(homeCoverMedia = mediaWithThumbnail)
            }
            repository.saveIndex(rootUri, _uiState.value.albums)
        }
    }

    private fun persistReadPermission(uri: Uri) {
        val context = getApplication<Application>()
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
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
        scanJob?.cancel()
        thumbnailJob?.cancel()
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

    private fun loadCachedGallery(uri: Uri) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val cachedAlbums = repository.loadIndex(uri)
            if (cachedAlbums != null) {
                _uiState.update {
                    it.copy(
                        albums = cachedAlbums,
                        selectedAlbum = null,
                        viewer = null,
                        isLoading = false,
                        error = null,
                    )
                }
                startThumbnailBuild(uri, cachedAlbums)
            } else {
                scanWithoutCancelling(uri)
            }
        }
    }

    private fun scan(uri: Uri) {
        scanJob?.cancel()
        thumbnailJob?.cancel()
        scanJob = viewModelScope.launch {
            scanWithoutCancelling(uri)
        }
    }

    private suspend fun scanWithoutCancelling(uri: Uri) {
        thumbnailJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            val albums = repository.scan(uri)
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
            startThumbnailBuild(uri, albums)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = throwable.message ?: "目录读取失败",
                )
            }
        }
    }

    private fun startThumbnailBuild(rootUri: Uri, albums: List<Album>) {
        thumbnailJob?.cancel()
        thumbnailJob = viewModelScope.launch(Dispatchers.IO) {
            val items = albums
                .flatMap { it.items }
                .filter { it.thumbnailPath == null }

            try {
                items.chunked(THUMBNAIL_BATCH_SIZE).forEach { batch ->
                    val generated = batch
                        .map { item ->
                            async {
                                runCatching { repository.ensureThumbnail(item) }.getOrNull()
                            }
                        }
                        .awaitAll()
                        .filterNotNull()

                    if (generated.isNotEmpty()) {
                        if (_uiState.value.rootUri != rootUri) return@launch
                        _uiState.update { state -> state.withGeneratedThumbnails(generated) }
                        repository.saveIndex(rootUri, _uiState.value.albums)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    private fun GalleryUiState.withGeneratedThumbnails(generated: List<MediaFile>): GalleryUiState {
        val byUri = generated.associateBy { it.uri }
        val updatedAlbums = albums.map { album -> album.withGeneratedThumbnails(byUri) }
        val updatedSelected = selectedAlbum?.let { selected ->
            updatedAlbums.firstOrNull { it.uri == selected.uri }
        }
        val updatedViewer = viewer?.let { current ->
            val updatedAlbum = updatedAlbums.firstOrNull { it.uri == current.album.uri } ?: current.album
            current.copy(album = updatedAlbum)
        }
        return copy(
            albums = updatedAlbums,
            selectedAlbum = updatedSelected,
            viewer = updatedViewer,
        )
    }

    private fun updateAlbum(albumUri: Uri, transform: (Album) -> Album) {
        _uiState.update { state ->
            val updatedAlbums = state.albums.map { album ->
                if (album.uri == albumUri) transform(album) else album
            }
            val updatedSelected = state.selectedAlbum?.let { selected ->
                updatedAlbums.firstOrNull { it.uri == selected.uri }
            }
            val updatedViewer = state.viewer?.let { current ->
                val updatedAlbum = updatedAlbums.firstOrNull { it.uri == current.album.uri } ?: current.album
                current.copy(album = updatedAlbum)
            }
            state.copy(
                albums = updatedAlbums,
                selectedAlbum = updatedSelected,
                viewer = updatedViewer,
            )
        }
    }

    private fun Album.withGeneratedThumbnails(generated: Map<Uri, MediaFile>): Album =
        copy(items = items.map { item -> generated[item.uri] ?: item })

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
        val THUMBNAIL_PARALLELISM: Int = min(max(Runtime.getRuntime().availableProcessors() - 1, 2), 6)
        val THUMBNAIL_BATCH_SIZE: Int = THUMBNAIL_PARALLELISM
    }
}
