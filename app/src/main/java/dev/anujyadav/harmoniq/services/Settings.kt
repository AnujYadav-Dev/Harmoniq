package dev.anujyadav.harmoniq.services

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import dev.anujyadav.harmoniq.Symphony
import dev.anujyadav.harmoniq.services.groove.repositories.AlbumArtistRepository
import dev.anujyadav.harmoniq.services.groove.repositories.AlbumRepository
import dev.anujyadav.harmoniq.services.groove.repositories.ArtistRepository
import dev.anujyadav.harmoniq.services.groove.repositories.GenreRepository
import dev.anujyadav.harmoniq.services.groove.repositories.PlaylistRepository
import dev.anujyadav.harmoniq.services.groove.repositories.SongRepository
import dev.anujyadav.harmoniq.services.radio.RadioQueue
import dev.anujyadav.harmoniq.ui.components.PlaylistAction
import dev.anujyadav.harmoniq.ui.components.ResponsiveGridColumns
import dev.anujyadav.harmoniq.ui.components.SongAction
import dev.anujyadav.harmoniq.ui.theme.ThemeMode
import dev.anujyadav.harmoniq.ui.view.NowPlayingControlsLayout
import dev.anujyadav.harmoniq.ui.view.NowPlayingLyricsLayout
import dev.anujyadav.harmoniq.ui.view.home.ForYou
import dev.anujyadav.harmoniq.ui.view.navigation.LibraryPill
import dev.anujyadav.harmoniq.ui.view.navigation.MainTab
import dev.anujyadav.harmoniq.utils.ImagePreserver
import dev.anujyadav.harmoniq.utils.StringListUtils
import kotlin.enums.EnumEntries
import kotlin.enums.enumEntries
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import dev.anujyadav.harmoniq.ui.components.PlaylistSwipeScope
import dev.anujyadav.harmoniq.ui.components.SongSwipeScope
import dev.anujyadav.harmoniq.ui.components.SwipeDirection
import java.util.concurrent.ConcurrentHashMap
import dev.anujyadav.harmoniq.ui.components.MediaLayout

class Settings(private val symphony: Symphony) {
    abstract class Entry<T>(val key: String) {
        private val mutableFlow by lazy {
            MutableStateFlow(getValueInternal())
        }

        val flow get() = mutableFlow.asStateFlow()
        val value get() = getValueInternal()

        fun setValue(value: T) {
            setValueInternal(value)
            mutableFlow.update { getValueInternal() }
        }

        protected abstract fun getValueInternal(): T
        protected abstract fun setValueInternal(value: T)
    }

    inner class BooleanEntry(key: String, val defaultValue: Boolean) : Entry<Boolean>(key) {
        override fun getValueInternal() = getSharedPreferences().getBoolean(key, defaultValue)

        override fun setValueInternal(value: Boolean) = getSharedPreferences().edit {
            putBoolean(key, value)
        }
    }

    inner class IntEntry(key: String, val defaultValue: Int) : Entry<Int>(key) {
        override fun getValueInternal() = getSharedPreferences().getInt(key, defaultValue)

        override fun setValueInternal(value: Int) = getSharedPreferences().edit {
            putInt(key, value)
        }
    }

    inner class FloatEntry(key: String, val defaultValue: Float) : Entry<Float>(key) {
        override fun getValueInternal() = getSharedPreferences().getFloat(key, defaultValue)

        override fun setValueInternal(value: Float) = getSharedPreferences().edit {
            putFloat(key, value)
        }
    }

    inner class NullableStringEntry(key: String) : Entry<String?>(key) {
        override fun getValueInternal() = getSharedPreferences().getString(key, null)

        override fun setValueInternal(value: String?) = getSharedPreferences().edit {
            putString(key, value)
        }
    }

    inner class EnumEntry<T : Enum<T>>(
        key: String,
        private val values: EnumEntries<T>,
        val defaultValue: T,
    ) : Entry<T>(key) {
        override fun getValueInternal() = getSharedPreferences().getString(key, null)
            ?.let { values.find { x -> x.name == it } }
            ?: defaultValue

        override fun setValueInternal(value: T) = getSharedPreferences().edit {
            putString(key, value.name)
        }
    }

    /**
     * An enum entry that can also be explicitly set to `null` ("none"), which is
     * different from never being set (falls back to [defaultValue]).
     */
    inner class OptionalEnumEntry<T : Enum<T>>(
        key: String,
        private val values: EnumEntries<T>,
        val defaultValue: T?,
        // read when this entry was never set, e.g. a setting it replaced
        private val fallbackKey: String? = null,
    ) : Entry<T?>(key) {
        private fun getStored() = getSharedPreferences().run {
            getString(key, null) ?: fallbackKey?.let { getString(it, null) }
        }

        override fun getValueInternal() = when (val stored = getStored()) {
            null -> defaultValue
            NONE_VALUE -> null
            else -> values.find { it.name == stored } ?: defaultValue
        }

        override fun setValueInternal(value: T?) = getSharedPreferences().edit {
            putString(key, value?.name ?: NONE_VALUE)
        }

        fun reset() = setValue(defaultValue)
    }

    inner class StringListEntry(key: String) : Entry<List<String>>(key) {
        override fun getValueInternal() = getSharedPreferences().getString(key, null)
            ?.let { runCatching { JSONArray(it) }.getOrNull() }
            ?.let { json -> List(json.length()) { json.getString(it) } }
            ?: emptyList()

        override fun setValueInternal(value: List<String>) = getSharedPreferences().edit {
            putString(key, JSONArray(value).toString())
        }
    }

    inner class StringLongMapEntry(key: String) : Entry<Map<String, Long>>(key) {
        override fun getValueInternal() = getSharedPreferences().getString(key, null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let { json -> json.keys().asSequence().associateWith { json.optLong(it) } }
            ?: emptyMap()

        override fun setValueInternal(value: Map<String, Long>) = getSharedPreferences().edit {
            putString(key, JSONObject(value).toString())
        }
    }

    inner class StringSetEntry(
        key: String,
        val defaultValue: Set<String>,
    ) : Entry<Set<String>>(key) {
        override fun getValueInternal() =
            getSharedPreferences().getStringSet(key, null) ?: defaultValue

        override fun setValueInternal(value: Set<String>) = getSharedPreferences().edit {
            putStringSet(key, value)
        }
    }

    inner class EnumSetEntry<T : Enum<T>>(
        key: String,
        values: EnumEntries<T>,
        val defaultValue: Set<T>,
    ) : Entry<Set<T>>(key) {
        private val entries = values.associateBy { it.name }

        override fun getValueInternal() = getSharedPreferences().getString(key, null)
            ?.split(",")
            ?.mapNotNull { entries[it] }
            ?.toSet()
            ?: defaultValue

        override fun setValueInternal(value: Set<T>) = getSharedPreferences().edit {
            putString(key, value.joinToString(",") { it.name })
        }
    }

    val themeMode = EnumEntry("theme_mode", enumEntries<ThemeMode>(), ThemeMode.SYSTEM)
    val useMaterialYou = BooleanEntry("material_you", true)
    val showBottomNavLabels = BooleanEntry("show_bottom_nav_labels", true)
    val lastUsedSongsSortBy = EnumEntry(
        "last_used_song_sort_by",
        enumEntries<SongRepository.SortBy>(),
        SongRepository.SortBy.TITLE,
    )
    val lastUsedSongsSortReverse = BooleanEntry("last_used_song_sort_reverse", false)
    val lastUsedArtistsSortBy = EnumEntry(
        "last_used_artists_sort_by",
        enumEntries<ArtistRepository.SortBy>(),
        ArtistRepository.SortBy.ARTIST_NAME,
    )
    val lastUsedArtistsSortReverse = BooleanEntry("last_used_artists_sort_reverse", false)
    val lastUsedArtistsHorizontalGridColumns = IntEntry(
        "last_used_artists_horizontal_grid_columns",
        ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
    )
    val lastUsedArtistsVerticalGridColumns = IntEntry(
        "last_used_artists_vertical_grid_columns",
        ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
    )
    val lastUsedAlbumArtistsSortBy = EnumEntry(
        "last_used_album_artists_sort_by",
        enumEntries<AlbumArtistRepository.SortBy>(),
        AlbumArtistRepository.SortBy.ARTIST_NAME,
    )
    val lastUsedAlbumArtistsSortReverse =
        BooleanEntry("last_used_album_artists_sort_reverse", false)
    val lastUsedAlbumArtistsHorizontalGridColumns = IntEntry(
        "last_used_album_artists_horizontal_grid_columns",
        ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
    )
    val lastUsedAlbumArtistsVerticalGridColumns = IntEntry(
        "last_used_album_artists_vertical_grid_columns",
        ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
    )
    val lastUsedAlbumsSortBy = EnumEntry(
        "last_used_albums_sort_by",
        enumEntries<AlbumRepository.SortBy>(),
        AlbumRepository.SortBy.ALBUM_NAME,
    )
    val lastUsedAlbumsSortReverse = BooleanEntry("last_used_albums_sort_reverse", false)
    val lastUsedAlbumsHorizontalGridColumns = IntEntry(
        "last_used_albums_horizontal_grid_columns",
        ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
    )
    val lastUsedAlbumsVerticalGridColumns = IntEntry(
        "last_used_albums_vertical_grid_columns",
        ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
    )
    val lastUsedGenresSortBy = EnumEntry(
        "last_used_genres_sort_by",
        enumEntries<GenreRepository.SortBy>(),
        GenreRepository.SortBy.GENRE,
    )
    val lastUsedGenresSortReverse = BooleanEntry("last_used_genres_sort_reverse", false)
    val lastUsedGenresHorizontalGridColumns = IntEntry(
        "last_used_genres_horizontal_grid_columns",
        ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
    )
    val lastUsedGenresVerticalGridColumns = IntEntry(
        "last_used_genres_vertical_grid_columns",
        ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
    )
    val lastUsedBrowserSortBy = EnumEntry(
        "last_used_folder_sort_by",
        enumEntries<SongRepository.SortBy>(),
        SongRepository.SortBy.FILENAME,
    )
    val lastUsedBrowserSortReverse = BooleanEntry("last_used_folder_sort_reverse", false)
    val lastUsedBrowserPath = NullableStringEntry("last_used_folder_path")
    val lastUsedPlaylistsSortBy = EnumEntry(
        "last_used_playlists_sort_by",
        enumEntries<PlaylistRepository.SortBy>(),
        PlaylistRepository.SortBy.CUSTOM,
    )
    val lastUsedPlaylistsSortReverse = BooleanEntry("last_used_playlists_sort_reverse", false)
    val lastUsedPlaylistsHorizontalGridColumns = IntEntry(
        "last_used_playlists_horizontal_grid_columns",
        ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
    )
    val lastUsedPlaylistsVerticalGridColumns = IntEntry(
        "last_used_playlists_vertical_grid_columns",
        ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
    )
    val lastUsedPlaylistSongsSortBy = EnumEntry(
        "last_used_playlist_songs_sort_by",
        enumEntries<SongRepository.SortBy>(),
        SongRepository.SortBy.CUSTOM,
    )
    val lastUsedPlaylistSongsSortReverse =
        BooleanEntry("last_used_playlist_songs_sort_reverse", false)
    val lastUsedAlbumSongsSortBy = EnumEntry(
        "last_used_album_songs_sort_by",
        enumEntries<SongRepository.SortBy>(),
        SongRepository.SortBy.TRACK_NUMBER,
    )
    val lastUsedAlbumSongsSortReverse = BooleanEntry("last_used_album_songs_sort_reverse", false)
    val lastUsedTreePathSortBy = EnumEntry(
        "last_used_tree_path_sort_by",
        enumEntries<StringListUtils.SortBy>(),
        StringListUtils.SortBy.NAME,
    )
    val lastUsedTreePathSortReverse = BooleanEntry("last_used_tree_path_sort_reverse", false)
    val lastUsedFoldersSortBy = EnumEntry(
        "last_used_folders_sort_by",
        enumEntries<StringListUtils.SortBy>(),
        StringListUtils.SortBy.NAME,
    )
    val lastUsedFoldersSortReverse = BooleanEntry("last_used_folders_sort_reverse", false)
    val lastUsedFoldersHorizontalGridColumns = IntEntry(
        "last_used_folders_horizontal_grid_columns",
        ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
    )
    val lastUsedFoldersVerticalGridColumns = IntEntry(
        "last_used_folders_vertical_grid_columns",
        ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
    )
    val lastDisabledTreePaths = StringSetEntry("last_disabled_tree_paths", emptySet())
    val previousSongQueue = object : Entry<RadioQueue.Serialized?>("previous_song_queue") {
        override fun getValueInternal() = getSharedPreferences().getString(key, null)?.let {
            RadioQueue.Serialized.parse(it)
        }

        override fun setValueInternal(value: RadioQueue.Serialized?) =
            getSharedPreferences().edit {
                putString(key, value?.serialize())
            }
    }
    val libraryPillOrder = object : Entry<List<LibraryPill>>("library_pill_order") {
        private val default = LibraryPill.defaultOrder
        override fun getValueInternal() =
            getSharedPreferences().getString(key, null)
                ?.split(",")
                ?.mapNotNull { name -> LibraryPill.entries.find { it.name == name } }
                ?.let { saved -> saved + (default - saved.toSet()) }
                ?: default
        override fun setValueInternal(value: List<LibraryPill>) =
            getSharedPreferences().edit { putString(key, value.joinToString(",") { it.name }) }
    }
    // entries are created on demand per scope, cached so each keeps a single flow
    private val swipeActionEntries = ConcurrentHashMap<String, OptionalEnumEntry<*>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Enum<T>> swipeActionEntry(
        kind: String,
        scopeKey: String,
        direction: SwipeDirection,
        values: EnumEntries<T>,
        defaultValue: T?,
    ) = swipeActionEntries.getOrPut("${kind}_${direction.key}_$scopeKey") {
        OptionalEnumEntry(
            "${kind}_swipe_${direction.key}_action_$scopeKey",
            values,
            defaultValue,
            // the app wide setting used before swipe actions became per screen
            fallbackKey = "${kind}_swipe_${direction.key}_action",
        )
    } as OptionalEnumEntry<T>

    fun songSwipeAction(scope: SongSwipeScope, direction: SwipeDirection) = swipeActionEntry(
        SONG_SWIPE_KIND,
        scope.key,
        direction,
        enumEntries<SongAction>(),
        scope.defaultAction(direction),
    )

    fun playlistSwipeAction(scope: PlaylistSwipeScope, direction: SwipeDirection) = swipeActionEntry(
        PLAYLIST_SWIPE_KIND,
        scope.key,
        direction,
        enumEntries<PlaylistAction>(),
        scope.defaultAction(direction),
    )

    val homePlaylistsSortBy = EnumEntry(
        "home_playlists_sort_by",
        enumEntries<PlaylistRepository.HomeSortBy>(),
        PlaylistRepository.HomeSortBy.RECENTLY_CREATED,
    )
    val homePlaylistsLimit = IntEntry(
        "home_playlists_limit",
        PlaylistRepository.HOME_PLAYLISTS_DEFAULT_LIMIT,
    )
    val homeShowFavorites = BooleanEntry("home_show_favorites", true)
    val homePlaylistIds = StringListEntry("home_playlist_ids")
    val playlistLastPlayed = StringLongMapEntry("playlist_last_played")
    val lastMainTab = EnumEntry("main_last_tab", enumEntries<MainTab>(), MainTab.HOME)
    val songsFilterPattern = NullableStringEntry("songs_filter_pattern")
    val minSongDuration = IntEntry("min_song_duration", 0)

    val fadePlayback = BooleanEntry("fade_playback", false)
    val requireAudioFocus = BooleanEntry("require_audio_focus", true)
    val ignoreAudioFocusLoss = BooleanEntry("ignore_audio_focus_loss", false)
    val playOnHeadphonesConnect = BooleanEntry("play_on_headphones_connect", false)
    val pauseOnHeadphonesDisconnect = BooleanEntry("pause_on_headphones_disconnect", true)
    val primaryColor = NullableStringEntry("primary_color")
    val randomizePrimaryColor = BooleanEntry("randomize_primary_color", false)

    // pre-picked random color for the next launch, so its splash screen can match
    val nextPrimaryColor = NullableStringEntry("next_primary_color")
    val fadePlaybackDuration = FloatEntry("fade_playback_duration", 1f)
    val forYouContents = EnumSetEntry(
        "for_you_contents",
        enumEntries<ForYou>(),
        setOf(ForYou.Songs, ForYou.Albums, ForYou.Artists),
    )
    val suggestedSongsCategory = NullableStringEntry("suggested_songs_category")
    val suggestedSongsLayout = EnumEntry(
        "suggested_songs_layout",
        enumEntries<MediaLayout>(),
        MediaLayout.List,
    )
    val lastUsedAlbumsLayout = mediaLayoutEntry("last_used_albums_layout")
    val lastUsedArtistsLayout = mediaLayoutEntry("last_used_artists_layout")
    val lastUsedAlbumArtistsLayout = mediaLayoutEntry("last_used_album_artists_layout")
    val lastUsedGenresLayout = mediaLayoutEntry("last_used_genres_layout")
    val lastUsedPlaylistsLayout = mediaLayoutEntry("last_used_playlists_layout")
    val lastUsedFoldersLayout = mediaLayoutEntry("last_used_folders_layout")

    // library sections keep their grid unless the user switches
    private fun mediaLayoutEntry(key: String) =
        EnumEntry(key, enumEntries<MediaLayout>(), MediaLayout.Grid)
    val blacklistFolders = StringSetEntry("blacklist_folders", emptySet())
    val whitelistFolders = StringSetEntry("whitelist_folders", emptySet())

    val nowPlayingAdditionalInfo = BooleanEntry("show_now_playing_additional_info", true)
    val nowPlayingSeekControls = BooleanEntry("enable_seek_controls", false)
    val seekBackDuration = IntEntry("seek_back_duration", 15)
    val seekForwardDuration = IntEntry("seek_back_duration", 30)
    val miniPlayerTrackControls = BooleanEntry("mini_player_extended_controls", false)
    val miniPlayerSeekControls = BooleanEntry("mini_player_seek_controls", false)
    val fontFamily = NullableStringEntry("font_family")
    val nowPlayingControlsLayout = EnumEntry(
        "now_playing_controls_layout",
        enumEntries<NowPlayingControlsLayout>(),
        NowPlayingControlsLayout.CompactLeft,
    )

    val fontScale = FloatEntry("font_scale", 1f)
    val contentScale = FloatEntry("content_scale", 1f)
    val nowPlayingLyricsLayout = EnumEntry(
        "now_playing_lyrics_layout",
        enumEntries<NowPlayingLyricsLayout>(),
        NowPlayingLyricsLayout.ReplaceArtwork,
    )
    val artistTagSeparators = StringSetEntry("artist_tag_separators", setOf(";", "/", ",", "+"))
    val genreTagSeparators = StringSetEntry("genre_tag_separators", setOf(";", "/", ",", "+"))
    val miniPlayerTextMarquee = BooleanEntry("mini_player_text_marquee", true)
    val mediaFolders = object : Entry<Set<Uri>>("media_folders") {
        override fun getValueInternal() = getSharedPreferences().getStringSet(key, null)
            ?.map { Uri.parse(it) }
            ?.toSet()
            ?: emptySet()

        override fun setValueInternal(value: Set<Uri>) =
            getSharedPreferences().edit {
                putStringSet(key, value.map { it.toString() }.toSet())
            }
    }
    val artworkQuality = EnumEntry(
        "artwork_quality",
        enumEntries<ImagePreserver.Quality>(),
        ImagePreserver.Quality.Medium,
    )
    val useMetaphony = BooleanEntry("use_metaphony", true)
    val gaplessPlayback = BooleanEntry("gapless_playback", true)
    val caseSensitiveSorting = BooleanEntry("case_sensitive_sorting", false)
    val lyricsKeepScreenAwake = BooleanEntry("lyrics_keep_screen_awake", true)

    private fun getSharedPreferences() = symphony.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    companion object {
        private const val NONE_VALUE = ""
        private const val SONG_SWIPE_KIND = "song"
        private const val PLAYLIST_SWIPE_KIND = "playlist"
    }
}
