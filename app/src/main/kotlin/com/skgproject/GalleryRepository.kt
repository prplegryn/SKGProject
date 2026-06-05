package com.skgproject

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
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
        val savedSettings = loadAlbumSettings(rootUri)
        val albums = root.listFiles()
            .asSequence()
            .filter { it.isDirectory && it.canRead() }
            .mapNotNull { directory -> directory.toAlbumOrNull() }
            .map { album -> album.withSettings(savedSettings[album.uri.toString()]) }
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
            .put(KEY_ALBUMS, albums.albumsToJson())
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

    suspend fun mediaFileForPickedUri(uri: Uri, albumName: String): MediaFile? = withContext(Dispatchers.IO) {
        val document = DocumentFile.fromSingleUri(context, uri)
        val fileName = document
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
            ?: "Selected media"
        val media = MediaFile(
            uri = uri,
            name = fileName,
            mimeType = context.contentResolver.getType(uri) ?: document?.type,
            lastModified = document?.lastModified()?.takeIf { it > 0L } ?: 0L,
            size = document?.length()?.takeIf { it > 0L } ?: 0L,
            albumName = albumName,
        )
        media.takeIf { it.isImage || it.isVideo }
    }

    suspend fun extractAlbumSurfaceColor(item: MediaFile): Long? = withContext(Dispatchers.IO) {
        val bitmap = item.thumbnailPath
            ?.let(::File)
            ?.takeIf { it.exists() && it.length() > 0L }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
            ?: if (item.isVideo) createVideoThumbnail(item.uri) else createImageThumbnail(item.uri)

        bitmap?.let { decoded ->
            val color = dominantSurfaceColor(decoded)
            decoded.recycle()
            color
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

    private fun dominantSurfaceColor(bitmap: Bitmap): Long {
        val step = (bitmap.width.coerceAtLeast(bitmap.height) / COLOR_SAMPLE_GRID).coerceAtLeast(1)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L

        var y = step / 2
        while (y < bitmap.height) {
            var x = step / 2
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (AndroidColor.alpha(pixel) > 32) {
                    red += AndroidColor.red(pixel)
                    green += AndroidColor.green(pixel)
                    blue += AndroidColor.blue(pixel)
                    count++
                }
                x += step
            }
            y += step
        }

        if (count == 0L) return DEFAULT_ALBUM_SURFACE_COLOR

        val average = AndroidColor.rgb(
            (red / count).toInt(),
            (green / count).toInt(),
            (blue / count).toInt(),
        )
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(average, hsv)
        hsv[1] = (hsv[1] * 0.42f).coerceIn(0.08f, 0.34f)
        hsv[2] = (hsv[2] * 0.32f).coerceIn(0.09f, 0.24f)
        return AndroidColor.HSVToColor(hsv).toUnsignedLong()
    }

    private fun Int.toUnsignedLong(): Long = toLong() and 0xffffffffL

    private fun loadAlbumSettings(rootUri: Uri): Map<String, AlbumSettings> {
        if (!indexFile.exists()) return emptyMap()

        return runCatching {
            val json = JSONObject(indexFile.readText())
            if (json.optString(KEY_ROOT_URI) != rootUri.toString()) return@runCatching emptyMap()

            val albums = json.getJSONArray(KEY_ALBUMS)
            buildMap {
                for (index in 0 until albums.length()) {
                    val albumJson = albums.getJSONObject(index)
                    put(
                        albumJson.getString(KEY_URI),
                        AlbumSettings(
                            backgroundMedia = albumJson.optJSONObject(KEY_BACKGROUND_MEDIA)?.toMediaFile(),
                            homeCoverMedia = albumJson.optJSONObject(KEY_HOME_COVER_MEDIA)?.toMediaFile(),
                            backgroundColor = albumJson.optLongOrNull(KEY_BACKGROUND_COLOR),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun Album.withSettings(settings: AlbumSettings?): Album =
        if (settings == null) {
            this
        } else {
            copy(
                backgroundMedia = settings.backgroundMedia?.copy(albumName = name),
                homeCoverMedia = settings.homeCoverMedia?.copy(albumName = name),
                backgroundColor = settings.backgroundColor,
            )
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
                        backgroundMedia = albumJson.optJSONObject(KEY_BACKGROUND_MEDIA)?.toMediaFile(),
                        homeCoverMedia = albumJson.optJSONObject(KEY_HOME_COVER_MEDIA)?.toMediaFile(),
                        backgroundColor = albumJson.optLongOrNull(KEY_BACKGROUND_COLOR),
                    ),
                )
            }
        }
    }

    private fun JSONArray.toMediaFiles(): List<MediaFile> = buildList {
        for (index in 0 until length()) {
            add(getJSONObject(index).toMediaFile())
        }
    }

    private fun JSONObject.toMediaFile(): MediaFile {
        val thumbnailPath = optString(KEY_THUMBNAIL_PATH).takeIf { path ->
            path.isNotBlank() && File(path).exists()
        }
        return MediaFile(
            uri = Uri.parse(getString(KEY_URI)),
            name = getString(KEY_NAME),
            mimeType = optString(KEY_MIME_TYPE).takeIf { it.isNotBlank() },
            lastModified = optLong(KEY_LAST_MODIFIED),
            size = optLong(KEY_SIZE),
            albumName = getString(KEY_ALBUM_NAME),
            thumbnailPath = thumbnailPath,
        )
    }

    private fun List<Album>.albumsToJson(): JSONArray {
        val albums = JSONArray()
        forEach { album ->
            albums.put(
                JSONObject()
                    .put(KEY_NAME, album.name)
                    .put(KEY_URI, album.uri.toString())
                    .put(KEY_BACKGROUND_MEDIA, album.backgroundMedia?.toJson() ?: JSONObject.NULL)
                    .put(KEY_HOME_COVER_MEDIA, album.homeCoverMedia?.toJson() ?: JSONObject.NULL)
                    .put(KEY_BACKGROUND_COLOR, album.backgroundColor ?: JSONObject.NULL)
                    .put(KEY_ITEMS, album.items.mediaFilesToJson()),
            )
        }
        return albums
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private data class AlbumSettings(
        val backgroundMedia: MediaFile?,
        val homeCoverMedia: MediaFile?,
        val backgroundColor: Long?,
    )

    private fun List<MediaFile>.mediaFilesToJson(): JSONArray {
        val items = JSONArray()
        forEach { item ->
            items.put(item.toJson())
        }
        return items
    }

    private fun MediaFile.toJson(): JSONObject =
        JSONObject()
            .put(KEY_URI, uri.toString())
            .put(KEY_NAME, name)
            .put(KEY_MIME_TYPE, mimeType ?: "")
            .put(KEY_LAST_MODIFIED, lastModified)
            .put(KEY_SIZE, size)
            .put(KEY_ALBUM_NAME, albumName)
            .put(KEY_THUMBNAIL_PATH, thumbnailPath ?: "")

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
        const val COLOR_SAMPLE_GRID = 28
        const val DEFAULT_ALBUM_SURFACE_COLOR = 0xff151310L

        const val KEY_VERSION = "version"
        const val KEY_ROOT_URI = "rootUri"
        const val KEY_ALBUMS = "albums"
        const val KEY_ITEMS = "items"
        const val KEY_BACKGROUND_MEDIA = "backgroundMedia"
        const val KEY_HOME_COVER_MEDIA = "homeCoverMedia"
        const val KEY_BACKGROUND_COLOR = "backgroundColor"
        const val KEY_URI = "uri"
        const val KEY_NAME = "name"
        const val KEY_MIME_TYPE = "mimeType"
        const val KEY_LAST_MODIFIED = "lastModified"
        const val KEY_SIZE = "size"
        const val KEY_ALBUM_NAME = "albumName"
        const val KEY_THUMBNAIL_PATH = "thumbnailPath"
    }
}
