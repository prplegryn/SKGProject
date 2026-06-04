package com.skgproject

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GalleryRepository(private val context: Context) {
    private val indexFile: File = File(context.filesDir, INDEX_FILE_NAME)
    private val thumbnailDir: File = File(context.filesDir, THUMBNAIL_DIR_NAME)

    suspend fun loadIndex(rootUri: Uri): List<Album>? = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) return@withContext null

        runCatching {
            val json = JSONObject(indexFile.readText())
            val root = json.optString(KEY_ROOT_URI)
            if (root != rootUri.toString()) return@runCatching null

            json.getJSONArray(KEY_ALBUMS)
                .toAlbums()
        }.getOrNull()
    }

    suspend fun scan(rootUri: Uri): List<Album> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
        val albums = root.listFiles()
            .asSequence()
            .filter { it.isDirectory && it.canRead() }
            .mapNotNull { directory -> directory.toAlbumOrNull() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .toList()

        saveIndex(rootUri, albums)
        albums
    }

    suspend fun saveIndex(rootUri: Uri, albums: List<Album>) = withContext(Dispatchers.IO) {
        thumbnailDir.mkdirs()
        val json = JSONObject()
            .put(KEY_VERSION, 1)
            .put(KEY_ROOT_URI, rootUri.toString())
            .put(KEY_ALBUMS, albums.toJson())
        indexFile.writeText(json.toString())
    }

    suspend fun ensureThumbnail(item: MediaFile): MediaFile? = withContext(Dispatchers.IO) {
        thumbnailDir.mkdirs()
        val target = thumbnailFileFor(item)
        if (target.exists() && target.length() > 0L) {
            return@withContext item.copy(thumbnailPath = target.absolutePath)
        }

        val bitmap = if (item.isVideo) createVideoThumbnail(item.uri) else createImageThumbnail(item.uri)
        if (bitmap == null) return@withContext null

        runCatching {
            FileOutputStream(target).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
        }.onFailure {
            target.delete()
        }
        bitmap.recycle()

        target.takeIf { it.exists() && it.length() > 0L }?.let {
            item.copy(thumbnailPath = it.absolutePath)
        }
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
        return media
            .takeIf { it.isImage || it.isVideo }
            ?.let { it.copy(thumbnailPath = existingThumbnailPath(it)) }
    }

    private fun existingThumbnailPath(item: MediaFile): String? =
        thumbnailFileFor(item).takeIf { it.exists() && it.length() > 0L }?.absolutePath

    private fun thumbnailFileFor(item: MediaFile): File {
        val key = "${item.uri}|${item.lastModified}|${item.size}"
        return File(thumbnailDir, "${key.sha256()}.jpg")
    }

    private fun createImageThumbnail(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, THUMBNAIL_SIZE)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        return decoded.scaledDownTo(THUMBNAIL_SIZE)
    }

    private fun createVideoThumbnail(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, uri)
            retriever
                .getFrameAtTime(VIDEO_FRAME_MICROS, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.scaledDownTo(THUMBNAIL_SIZE)
        }.getOrNull().also {
            runCatching { retriever.release() }
        }
    }

    private fun Bitmap.scaledDownTo(maxSide: Int): Bitmap {
        val largest = width.coerceAtLeast(height)
        if (largest <= maxSide) return this

        val scale = maxSide.toFloat() / largest.toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        if (scaled !== this) recycle()
        return scaled
    }

    private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        var nextWidth = width / 2
        var nextHeight = height / 2
        while (nextWidth >= maxSide && nextHeight >= maxSide) {
            sample *= 2
            nextWidth /= 2
            nextHeight /= 2
        }
        return sample
    }

    private fun JSONArray.toAlbums(): List<Album> = buildList {
        for (index in 0 until length()) {
            val albumJson = getJSONObject(index)
            val items = albumJson.getJSONArray(KEY_ITEMS).toMediaFiles()
            if (items.isNotEmpty()) {
                add(
                    Album(
                        name = albumJson.getString(KEY_NAME),
                        uri = Uri.parse(albumJson.getString(KEY_URI)),
                        items = items,
                    ),
                )
            }
        }
    }

    private fun JSONArray.toMediaFiles(): List<MediaFile> = buildList {
        for (index in 0 until length()) {
            val itemJson = getJSONObject(index)
            val thumbnailPath = itemJson.optString(KEY_THUMBNAIL_PATH).takeIf { path ->
                path.isNotBlank() && File(path).exists()
            }
            add(
                MediaFile(
                    uri = Uri.parse(itemJson.getString(KEY_URI)),
                    name = itemJson.getString(KEY_NAME),
                    mimeType = itemJson.optString(KEY_MIME_TYPE).takeIf { it.isNotBlank() },
                    lastModified = itemJson.optLong(KEY_LAST_MODIFIED),
                    size = itemJson.optLong(KEY_SIZE),
                    albumName = itemJson.getString(KEY_ALBUM_NAME),
                    thumbnailPath = thumbnailPath,
                ),
            )
        }
    }

    private fun List<Album>.toJson(): JSONArray {
        val albums = JSONArray()
        forEach { album ->
            albums.put(
                JSONObject()
                    .put(KEY_NAME, album.name)
                    .put(KEY_URI, album.uri.toString())
                    .put(KEY_ITEMS, album.items.toJson()),
            )
        }
        return albums
    }

    private fun List<MediaFile>.toJson(): JSONArray {
        val items = JSONArray()
        forEach { item ->
            items.put(
                JSONObject()
                    .put(KEY_URI, item.uri.toString())
                    .put(KEY_NAME, item.name)
                    .put(KEY_MIME_TYPE, item.mimeType ?: "")
                    .put(KEY_LAST_MODIFIED, item.lastModified)
                    .put(KEY_SIZE, item.size)
                    .put(KEY_ALBUM_NAME, item.albumName)
                    .put(KEY_THUMBNAIL_PATH, item.thumbnailPath ?: ""),
            )
        }
        return items
    }

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return bytes.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        const val INDEX_FILE_NAME = "gallery-index-v1.json"
        const val THUMBNAIL_DIR_NAME = "thumbnails"
        const val THUMBNAIL_SIZE = 512
        const val JPEG_QUALITY = 82
        const val VIDEO_FRAME_MICROS = 650_000L

        const val KEY_VERSION = "version"
        const val KEY_ROOT_URI = "rootUri"
        const val KEY_ALBUMS = "albums"
        const val KEY_ITEMS = "items"
        const val KEY_URI = "uri"
        const val KEY_NAME = "name"
        const val KEY_MIME_TYPE = "mimeType"
        const val KEY_LAST_MODIFIED = "lastModified"
        const val KEY_SIZE = "size"
        const val KEY_ALBUM_NAME = "albumName"
        const val KEY_THUMBNAIL_PATH = "thumbnailPath"
    }
}
