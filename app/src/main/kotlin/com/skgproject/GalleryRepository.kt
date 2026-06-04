package com.skgproject

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GalleryRepository(private val context: Context) {
    suspend fun scan(rootUri: Uri): List<Album> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
        root.listFiles()
            .asSequence()
            .filter { it.isDirectory && it.canRead() }
            .mapNotNull { directory -> directory.toAlbumOrNull() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .toList()
    }

    private fun DocumentFile.toAlbumOrNull(): Album? {
        val albumName = name?.takeIf { it.isNotBlank() } ?: return null
        val media = listFiles()
            .asSequence()
            .filter { it.isFile && it.canRead() }
            .mapNotNull { file -> file.toMediaFile(albumName) }
            .sortedWith(
                compareByDescending<MediaFile> { it.lastModified }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
            .toList()

        return media.takeIf { it.isNotEmpty() }?.let {
            Album(name = albumName, uri = uri, items = it)
        }
    }

    private fun DocumentFile.toMediaFile(albumName: String): MediaFile? {
        val fileName = name?.takeIf { it.isNotBlank() } ?: return null
        val media = MediaFile(
            uri = uri,
            name = fileName,
            mimeType = type,
            lastModified = lastModified(),
            size = length(),
            albumName = albumName,
        )
        return media.takeIf { it.isImage || it.isVideo }
    }
}

