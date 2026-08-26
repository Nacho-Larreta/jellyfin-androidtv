package org.jellyfin.design

import org.jellyfin.design.token.ColorTokens
import org.jellyfin.design.token.SemanticComponentTokens
import org.jellyfin.design.token.SemanticColorTokens
import org.jellyfin.design.token.SemanticElevationTokens
import org.jellyfin.design.token.SemanticMotionTokens
import org.jellyfin.design.token.SemanticShapeTokens
import org.jellyfin.design.token.SemanticSpaceTokens
import org.jellyfin.design.token.SemanticTypographyTokens
import org.jellyfin.design.token.RadiusTokens
import org.jellyfin.design.token.SpaceTokens
import org.jellyfin.design.token.TypographyTokens

object Tokens {
	/** Legacy raw palette. New components consume [SemanticColor]. */
	val Color = ColorTokens
	val Radius = RadiusTokens
	val Space = SpaceTokens
	val Typography = TypographyTokens

	val SemanticColor = SemanticColorTokens
	val SemanticComponent = SemanticComponentTokens
	val SemanticElevation = SemanticElevationTokens
	val SemanticMotion = SemanticMotionTokens
	val SemanticShape = SemanticShapeTokens
	val SemanticSpace = SemanticSpaceTokens
	val SemanticTypography = SemanticTypographyTokens
}
