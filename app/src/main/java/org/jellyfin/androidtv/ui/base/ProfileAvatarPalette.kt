package org.jellyfin.androidtv.ui.base

import androidx.compose.ui.graphics.Color

data class ProfileAvatarPalette(
	val start: Color,
	val end: Color,
)

val ProfileAvatarActiveColor = Color(0xFF5BD66A)

fun profileAvatarPalette(index: Int?, seed: String): ProfileAvatarPalette {
	val palette = listOf(
		ProfileAvatarPalette(Color(0xFFD51F28), Color(0xFF8F1017)),
		ProfileAvatarPalette(Color(0xFF4CB7F5), Color(0xFF176C9F)),
		ProfileAvatarPalette(Color(0xFF9D4EDD), Color(0xFF4F1D75)),
		ProfileAvatarPalette(Color(0xFFF7B733), Color(0xFFD56D15)),
		ProfileAvatarPalette(Color(0xFF00B894), Color(0xFF00695F)),
	)

	val paletteIndex = index?.mod(palette.size) ?: seed.hashCode().mod(palette.size)
	return palette[paletteIndex]
}
