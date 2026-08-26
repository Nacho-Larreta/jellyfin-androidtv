package org.jellyfin.design.token

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object SemanticSpaceTokens {
	val space0 = 0.dp
	val space1 = 4.dp
	val space2 = 8.dp
	val space3 = 12.dp
	val space4 = 16.dp
	val space5 = 24.dp
	val space6 = 32.dp
	val space7 = 40.dp
	val space8 = 56.dp
	val space9 = 80.dp
}

object SemanticComponentTokens {
	val tvMinimumTarget = 48.dp
	val tvComfortableTarget = 56.dp
	val focusIndicatorWidth = 3.dp
	val focusSeparatorWidth = 2.dp
	val controlHorizontalPadding = 16.dp
	val controlVerticalPadding = 10.dp
	val chipHorizontalPadding = 14.dp
	val chipVerticalPadding = 8.dp
	val progressHeight = 6.dp

	val avatarCompact = 48.dp
	val avatarStandard = 64.dp
	val avatarComfortable = 80.dp
	val mediaCardCompactWidth = 160.dp
	val mediaCardStandardWidth = 240.dp
	val mediaCardComfortableWidth = 320.dp
	const val MediaCardAspectRatio = 16f / 9f
}

object SemanticShapeTokens {
	val none = 0.dp
	val extraSmall = 4.dp
	val small = 8.dp
	val medium = 12.dp
	val large = 16.dp
	val extraLarge = 28.dp
	const val FullPercent = 50
}

object SemanticElevationTokens {
	val level0 = 0.dp
	val level1 = 4.dp
	val level2 = 8.dp
	val level3 = 16.dp
}

object SemanticMotionTokens {
	const val FastMillis = 120
	const val BaseMillis = 200
	const val SlowMillis = 400
	const val HeroMillis = 800
	const val FocusScale = 1.04f

	val standardEasing: Easing = CubicBezierEasing(StandardEasingControl, 0f, 0f, 1f)
	val enterEasing: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
	val exitEasing: Easing = CubicBezierEasing(ExitEasingControl, 0f, 1f, 1f)

	private const val StandardEasingControl = 0.2f
	private const val ExitEasingControl = 0.3f
}

object SemanticTypographyTokens {
	val displayLarge = tvTextStyle(DisplayLargeSize, DisplayLargeLineHeight, FontWeight.Bold)
	val displayMedium = tvTextStyle(DisplayMediumSize, DisplayMediumLineHeight, FontWeight.SemiBold)
	val titleLarge = tvTextStyle(TitleLargeSize, TitleLargeLineHeight, FontWeight.SemiBold)
	val titleMedium = tvTextStyle(TitleMediumSize, TitleMediumLineHeight, FontWeight.Medium)
	val bodyLarge = tvTextStyle(BodyLargeSize, BodyLargeLineHeight, FontWeight.Normal)
	val bodyMedium = tvTextStyle(BodyMediumSize, BodyMediumLineHeight, FontWeight.Normal)
	val labelLarge = tvTextStyle(LabelLargeSize, LabelLargeLineHeight, FontWeight.SemiBold, tracking = LabelTracking)
	val labelMedium = tvTextStyle(LabelMediumSize, LabelMediumLineHeight, FontWeight.Medium, tracking = LabelTracking)
	val metadata = tvTextStyle(MetadataSize, MetadataLineHeight, FontWeight.Normal, tracking = MetadataTracking)
	val numericTimeline = tvTextStyle(
		fontSize = NumericTimelineSize,
		lineHeight = NumericTimelineLineHeight,
		fontWeight = FontWeight.Medium,
		fontFamily = FontFamily.Monospace,
	)

	private const val DisplayLargeSize = 32
	private const val DisplayLargeLineHeight = 40
	private const val DisplayMediumSize = 24
	private const val DisplayMediumLineHeight = 32
	private const val TitleLargeSize = 20
	private const val TitleLargeLineHeight = 28
	private const val TitleMediumSize = 18
	private const val TitleMediumLineHeight = 24
	private const val BodyLargeSize = 16
	private const val BodyLargeLineHeight = 24
	private const val BodyMediumSize = 14
	private const val BodyMediumLineHeight = 20
	private const val LabelLargeSize = 14
	private const val LabelLargeLineHeight = 20
	private const val LabelMediumSize = 12
	private const val LabelMediumLineHeight = 16
	private const val MetadataSize = 12
	private const val MetadataLineHeight = 16
	private const val NumericTimelineSize = 14
	private const val NumericTimelineLineHeight = 20
	private const val LabelTracking = 0.1f
	private const val MetadataTracking = 0.2f
}

private fun tvTextStyle(
	fontSize: Int,
	lineHeight: Int,
	fontWeight: FontWeight,
	tracking: Float = 0f,
	fontFamily: FontFamily = FontFamily.SansSerif,
): TextStyle = TextStyle(
	fontFamily = fontFamily,
	fontSize = fontSize.sp,
	lineHeight = lineHeight.sp,
	fontWeight = fontWeight,
	letterSpacing = tracking.sp,
)
