package com.shneyni.nokiaplayer4

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore.Audio.Media as M
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: Long, val uri: Uri, val title: String, val artist: String,
    val album: String, val albumId: Long, val track: Int,
    val durationMs: Long, val path: String, val size: Long, val mime: String,
) {
    val folder get() = path.substringBeforeLast('/', "")

    fun toMediaItem(): MediaItem = mediaItemOf(id, uri, title, artist, album, albumId)
}

fun mediaItemOf(id: Long, uri: Uri, title: String, artist: String, album: String, albumId: Long): MediaItem =
    MediaItem.Builder()
        .setUri(uri).setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
                .setArtworkUri(ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId))
                .build()
        ).build()

object Library {
    @Volatile var loaded = false; private set
    var songs: List<Song> = emptyList(); private set
    var artists: List<String> = emptyList(); private set
    var albums: List<Pair<Long, String>> = emptyList(); private set   // (albumId, "אלבום – אמן")
    var folders: List<String> = emptyList(); private set
    var titleDigits: List<String> = emptyList(); private set
    var titleNorm: List<String> = emptyList(); private set

    fun scan(ctx: Context) {
        val out = ArrayList<Song>()
        val proj = arrayOf(M._ID, M.TITLE, M.ARTIST, M.ALBUM, M.ALBUM_ID, M.TRACK,
            M.DURATION, M.DATA, M.SIZE, M.MIME_TYPE)
        ctx.contentResolver.query(M.EXTERNAL_CONTENT_URI, proj, "${M.IS_MUSIC} != 0", null,
            null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                fun clean(s: String?) = if (s.isNullOrBlank() || s == "<unknown>") "לא ידוע" else s
                out += Song(
                    id, ContentUris.withAppendedId(M.EXTERNAL_CONTENT_URI, id),
                    c.getString(1) ?: "—", clean(c.getString(2)), clean(c.getString(3)),
                    c.getLong(4), c.getInt(5), c.getLong(6),
                    c.getString(7) ?: "", c.getLong(8), c.getString(9) ?: "",
                )
            }
        }
        out.sortBy { it.title.lowercase() }
        songs = out
        artists = out.map { it.artist }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        albums = out.groupBy { it.albumId }
            .map { (id, l) -> id to "${l[0].album} – ${l[0].artist}" }
            .sortedBy { it.second.lowercase() }
        folders = out.map { it.folder }.distinct().sorted()
        titleDigits = out.map { KeypadMap.toDigits(it.title) }
        titleNorm = out.map { KeypadMap.norm(it.title) }
        loaded = true
    }

    fun albumLabel(id: Long) = albums.firstOrNull { it.first == id }?.second ?: "אלבום"

    /** הקבוצה הבאה אחרי src (תיקייה/אלבום/אמן). null אם אין (למשל כל השירים או רשימה). */
    fun nextGroup(src: String): Pair<String, List<Song>>? {
        fun <T> nxt(list: List<T>, cur: T): T? {
            val i = list.indexOf(cur)
            return if (i < 0 || list.size < 2) null else list[(i + 1) % list.size]
        }
        val r: Pair<String, List<Song>>? = when {
            src.startsWith("folder:") -> nxt(folders, src.removePrefix("folder:"))
                ?.let { f -> "folder:$f" to songs.filter { it.folder == f } }
            src.startsWith("album:") -> {
                val cur = src.removePrefix("album:").toLongOrNull()
                if (cur == null) null else nxt(albums.map { it.first }, cur)
                    ?.let { a -> "album:$a" to songs.filter { it.albumId == a }.sortedBy { it.track } }
            }
            src.startsWith("artist:") -> nxt(artists, src.removePrefix("artist:"))
                ?.let { a -> "artist:$a" to songs.filter { it.artist == a } }
            else -> null
        }
        return r?.takeIf { it.second.isNotEmpty() }
    }
}
