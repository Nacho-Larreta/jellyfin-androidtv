package org.jellyfin.design

object AndroidDesignMapping {
	const val ContractVersion = "2.0.0"
	const val Platform = "android-tv"
	const val Theme = "dark"

	val MappingPaths = listOf(
		"org.jellyfin.design.token.SemanticColorTokens",
		"org.jellyfin.design.token.SemanticComponentTokens",
		"org.jellyfin.design.token.SemanticElevationTokens",
		"org.jellyfin.design.token.SemanticMotionTokens",
		"org.jellyfin.design.token.SemanticShapeTokens",
		"org.jellyfin.design.token.SemanticSpaceTokens",
		"org.jellyfin.design.token.SemanticTypographyTokens",
	)

	val ImplementedComponentIds = listOf(
		"atom.action",
		"atom.avatar",
		"atom.chip",
		"atom.focus-indicator",
		"atom.progress",
		"atom.protection-scrim",
		"molecule.media-card",
		"molecule.modal-layer",
	)

	const val EvidenceRoot = "specs/003-cross-platform-experience-hardening-and-tv-release"
	const val PlatformDivergence = "Android TV remains dark-only and uses 48dp D-pad targets."
}
