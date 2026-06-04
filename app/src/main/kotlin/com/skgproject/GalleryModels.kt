package com.skgproject

import android.net.Uri

data class MediaFile(
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val lastModified: Long,
    val size: Long,
    val albumName: String,
) {
    val isVideo: Boolean = mimeType?.startsWith("video/") == true || name.hasVideoExtension()
    val isImage: Boolean = mimeType?.startsWith("image/") == true || name.hasImageExtension()
}

data class Album(
    val name: String,
    val uri: Uri,
    val items: List<MediaFile>,
) {
    val cover: MediaFile? = items.firstOrNull()
}

data class ViewerState(
    val album: Album,
    val index: Int,
) {
    val item: MediaFile = album.items[index]
}

data class GalleryUiState(
    val rootUri: Uri? = null,
    val albums: List<Album> = emptyList(),
    val selectedAlbum: Album? = null,
    val viewer: ViewerState? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val needsDirectory: Boolean = rootUri == null
}

private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "avif")
private val videoExtensions = setOf("mp4", "m4v", "mkv", "mov", "webm", "3gp", "avi")

private fun String.extension(): String = substringAfterLast('.', missingDelimiterValue = "").lowercase()

private fun String.hasImageExtension(): Boolean = extension() in imageExtensions

private fun String.hasVideoExtension(): Boolean = extension() in videoExtensions

