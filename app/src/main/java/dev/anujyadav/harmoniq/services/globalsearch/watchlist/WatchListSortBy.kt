package dev.anujyadav.harmoniq.services.globalsearch.watchlist

import dev.anujyadav.harmoniq.utils.AppStrings

enum class WatchListSortBy(val label: String) {
    DateAdded(AppStrings.DateAdded),
    Title(AppStrings.Title),
    Artist(AppStrings.Artist);

    // newest first by default, the others alphabetically
    fun sort(items: List<WatchListItem>, reverse: Boolean): List<WatchListItem> {
        val sorted = when (this) {
            DateAdded -> items.sortedByDescending { it.addedAt }
            Title -> items.sortedBy { it.title.lowercase() }
            Artist -> items.sortedBy { it.artists.joinToString().lowercase() }
        }
        return if (reverse) sorted.reversed() else sorted
    }
}
