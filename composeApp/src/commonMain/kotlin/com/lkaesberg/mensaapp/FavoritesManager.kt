package com.lkaesberg.mensaapp

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FavoritesManager(private val settings: Settings) {
    private val _favorites = MutableStateFlow<Set<String>>(loadFavorites())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private fun loadFavorites(): Set<String> {
        val favoritesString = settings.getStringOrNull(FAVORITES_KEY) ?: ""
        return if (favoritesString.isNotEmpty()) {
            favoritesString.split(",").toSet()
        } else {
            emptySet()
        }
    }

    private fun saveFavorites(favorites: Set<String>) {
        settings.putString(FAVORITES_KEY, favorites.joinToString(","))
    }

    /**
     * Toggle a meal title in the favourites set. Matching is case- and
     * whitespace-insensitive so the same dish at a different canteen counts as
     * a single favourite even when the scraper produced slightly different
     * strings (extra spaces, casing, etc.). Removing strips every stored entry
     * that normalises to the same key, so any duplicates accumulated before
     * this normalisation existed get cleaned up on the next un-star.
     */
    fun toggleFavorite(title: String) {
        val n = normalizeFavoriteKey(title)
        val current = _favorites.value.toMutableSet()
        val removed = current.removeAll { normalizeFavoriteKey(it) == n }
        if (!removed) current.add(title.trim())
        _favorites.value = current
        saveFavorites(current)
    }

    fun isFavorite(title: String): Boolean = _favorites.value.containsFavorite(title)

    companion object {
        private const val FAVORITES_KEY = "favorite_meals"
    }
}

/** Lowercase + collapse whitespace so titles match across canteens. */
internal fun normalizeFavoriteKey(s: String): String =
    s.trim().lowercase().replace(Regex("\\s+"), " ")

/** Membership check that ignores casing / whitespace differences. */
internal fun Set<String>.containsFavorite(title: String): Boolean {
    if (title.isBlank()) return false
    val n = normalizeFavoriteKey(title)
    return any { normalizeFavoriteKey(it) == n }
}
