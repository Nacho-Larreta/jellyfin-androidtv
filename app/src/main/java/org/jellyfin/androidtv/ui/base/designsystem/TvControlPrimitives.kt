package org.jellyfin.androidtv.ui.base.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import org.jellyfin.design.Tokens
import org.jellyfin.design.token.SemanticColorPair

@Composable
fun TvAction(
	spec: TvActionSpec,
	onActivate: () -> Unit,
	focusOwners: TvFocusOwnerRegistry,
	modifier: Modifier = Modifier,
	content: @Composable BoxScope.() -> Unit,
) {
	TvFocusableSurface(
		spec = TvFocusableSurfaceSpec(
			semantics = spec.semantics,
			role = Role.Button,
			colors = actionColors(spec.variant, spec.state),
			shape = RoundedCornerShape(percent = Tokens.SemanticShape.FullPercent),
			minTarget = targetSize(spec.size),
			textStyle = Tokens.SemanticTypography.labelLarge,
			state = spec.state,
		),
		onActivate = onActivate,
		focusOwners = focusOwners,
		modifier = modifier,
	) {
		Box(
			modifier = Modifier.padding(
				horizontal = Tokens.SemanticComponent.controlHorizontalPadding,
				vertical = Tokens.SemanticComponent.controlVerticalPadding,
			),
			contentAlignment = Alignment.Center,
			content = content,
		)
	}
}

@Composable
fun TvChip(
	spec: TvChipSpec,
	onActivate: () -> Unit,
	focusOwners: TvFocusOwnerRegistry,
	modifier: Modifier = Modifier,
	content: @Composable BoxScope.() -> Unit,
) {
	TvFocusableSurface(
		spec = TvFocusableSurfaceSpec(
			semantics = spec.semantics,
			role = Role.Checkbox,
			colors = selectionColors(spec.state),
			shape = RoundedCornerShape(percent = Tokens.SemanticShape.FullPercent),
			minTarget = Tokens.SemanticComponent.tvMinimumTarget,
			textStyle = Tokens.SemanticTypography.labelLarge,
			state = spec.state,
		),
		onActivate = onActivate,
		focusOwners = focusOwners,
		modifier = modifier,
	) {
		Box(
			modifier = Modifier.padding(
				horizontal = Tokens.SemanticComponent.chipHorizontalPadding,
				vertical = Tokens.SemanticComponent.chipVerticalPadding,
			),
			contentAlignment = Alignment.Center,
			content = content,
		)
	}
}

@Composable
fun TvAvatar(
	spec: TvAvatarSpec,
	onActivate: () -> Unit,
	focusOwners: TvFocusOwnerRegistry,
	modifier: Modifier = Modifier,
	content: @Composable BoxScope.() -> Unit,
) {
	TvFocusableSurface(
		spec = TvFocusableSurfaceSpec(
			semantics = spec.semantics,
			role = Role.Button,
			colors = selectionColors(spec.state),
			shape = CircleShape,
			minTarget = Tokens.SemanticComponent.tvMinimumTarget,
			textStyle = Tokens.SemanticTypography.labelMedium,
			state = spec.state,
		),
		onActivate = onActivate,
		focusOwners = focusOwners,
		modifier = modifier.size(avatarSize(spec.size)),
		content = content,
	)
}

@Composable
fun TvMediaCard(
	spec: TvMediaCardSpec,
	onActivate: () -> Unit,
	focusOwners: TvFocusOwnerRegistry,
	modifier: Modifier = Modifier,
	content: @Composable BoxScope.() -> Unit,
) {
	TvFocusableSurface(
		spec = TvFocusableSurfaceSpec(
			semantics = spec.semantics,
			role = Role.Button,
			colors = toneColors(spec.tone, spec.state),
			shape = RoundedCornerShape(Tokens.SemanticShape.medium),
			minTarget = Tokens.SemanticComponent.tvMinimumTarget,
			textStyle = Tokens.SemanticTypography.titleMedium,
			state = spec.state,
		),
		onActivate = onActivate,
		focusOwners = focusOwners,
		modifier = modifier
			.width(mediaCardWidth(spec.size))
			.aspectRatio(Tokens.SemanticComponent.MediaCardAspectRatio),
		content = content,
	)
}

private fun actionColors(variant: TvActionVariant, state: TvComponentState): SemanticColorPair {
	if (!state.enabled) return Tokens.SemanticColor.actionDisabled
	if (state.selection == TvSelection.Selected) return Tokens.SemanticColor.selected
	return when (variant) {
		TvActionVariant.Primary -> Tokens.SemanticColor.actionPrimary
		TvActionVariant.Secondary -> Tokens.SemanticColor.actionSecondary
		TvActionVariant.Tertiary -> Tokens.SemanticColor.actionTertiary
		TvActionVariant.Destructive -> Tokens.SemanticColor.actionDestructive
	}
}

private fun selectionColors(state: TvComponentState): SemanticColorPair = when {
	!state.enabled -> Tokens.SemanticColor.actionDisabled
	state.selection == TvSelection.Selected -> Tokens.SemanticColor.selected
	state.availability == TvAvailability.Error -> Tokens.SemanticColor.error
	else -> Tokens.SemanticColor.actionSecondary
}

private fun toneColors(tone: TvTone, state: TvComponentState): SemanticColorPair {
	if (state.selection == TvSelection.Selected) return Tokens.SemanticColor.selected
	return when (tone) {
		TvTone.Neutral -> selectionColors(state)
		TvTone.Informative -> Tokens.SemanticColor.informative
		TvTone.Success -> Tokens.SemanticColor.success
		TvTone.Warning -> Tokens.SemanticColor.warning
		TvTone.Error -> Tokens.SemanticColor.error
	}
}

private fun targetSize(size: TvComponentSize): Dp = when (size) {
	TvComponentSize.Compact -> Tokens.SemanticComponent.tvMinimumTarget
	TvComponentSize.Standard,
	TvComponentSize.Comfortable -> Tokens.SemanticComponent.tvComfortableTarget
}

private fun avatarSize(size: TvComponentSize): Dp = when (size) {
	TvComponentSize.Compact -> Tokens.SemanticComponent.avatarCompact
	TvComponentSize.Standard -> Tokens.SemanticComponent.avatarStandard
	TvComponentSize.Comfortable -> Tokens.SemanticComponent.avatarComfortable
}

private fun mediaCardWidth(size: TvComponentSize): Dp = when (size) {
	TvComponentSize.Compact -> Tokens.SemanticComponent.mediaCardCompactWidth
	TvComponentSize.Standard -> Tokens.SemanticComponent.mediaCardStandardWidth
	TvComponentSize.Comfortable -> Tokens.SemanticComponent.mediaCardComfortableWidth
}
