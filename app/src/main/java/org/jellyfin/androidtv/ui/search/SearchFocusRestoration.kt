package org.jellyfin.androidtv.ui.search

internal suspend fun restoreSearchFocusAfterInputExit(
	clearFocus: () -> Unit,
	awaitFocusTreeCommit: suspend () -> Unit,
	reclaimFocus: () -> Boolean,
): Boolean {
	clearFocus()
	awaitFocusTreeCommit()
	return reclaimFocus()
}
