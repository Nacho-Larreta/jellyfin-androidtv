@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.jellyfin.androidtv.ui.base.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import org.jellyfin.design.Tokens
import org.jellyfin.design.token.SemanticColorPair
import org.jellyfin.androidtv.ui.base.ProvideTextStyle

val LocalTvContentColor = staticCompositionLocalOf { Color.Unspecified }
internal val LocalTvSurfaceColors = staticCompositionLocalOf {
	SemanticColorPair(container = Color.Unspecified, content = Color.Unspecified)
}

internal data class TvFocusableSurfaceSpec(
	val semantics: TvSemantics,
	val role: Role,
	val colors: SemanticColorPair,
	val shape: Shape,
	val minTarget: Dp,
	val textStyle: TextStyle,
	val state: TvComponentState,
)

private class TvFocusableSurfaceRuntime(onActivate: () -> Unit) {
	private val pressLedger = TvPressLedger(onActivate)

	var focused by mutableStateOf(false)
		private set
	var pressed by mutableStateOf(false)
		private set

	fun onFocusChanged(hasFocus: Boolean) {
		focused = hasFocus
		if (!hasFocus) cancelPress()
	}

	fun routePress(event: TvPressEvent): Boolean {
		pressed = event.phase == TvPressPhase.Down
		return pressLedger.route(event)
	}

	fun cancelPress() {
		pressed = false
		pressLedger.cancel()
	}
}

private data class TvFocusableSurfaceBinding(
	val focusRequester: FocusRequester,
	val runtime: TvFocusableSurfaceRuntime,
	val focusOwners: TvFocusOwnerRegistry,
	val focusScale: Float,
	val onActivate: () -> Unit,
)

@Composable
internal fun TvFocusableSurface(
	spec: TvFocusableSurfaceSpec,
	onActivate: () -> Unit,
	focusOwners: TvFocusOwnerRegistry,
	modifier: Modifier = Modifier,
	content: @Composable BoxScope.() -> Unit,
) {
	validateSurfaceSpec(spec)
	val focusRequester = remember { FocusRequester() }
	val latestOnActivate by rememberUpdatedState(onActivate)
	val enabled by rememberUpdatedState(spec.state.enabled)
	val runtime = remember { TvFocusableSurfaceRuntime { latestOnActivate() } }
	val systemMotion = rememberSystemMotionPreference()
	val reducedMotion = spec.state.motion == TvMotionPreference.Reduced ||
		systemMotion == TvMotionPreference.Reduced
	val focusScale by animateFloatAsState(
		targetValue = if (runtime.focused && !reducedMotion) Tokens.SemanticMotion.FocusScale else 1f,
		animationSpec = focusAnimationSpec(reducedMotion),
		label = "tv-focus-scale",
	)

	DisposableEffect(spec.semantics.id, focusOwners, focusRequester) {
		val registration = focusOwners.register(
			id = spec.semantics.id,
			canFocus = { enabled },
			requestFocus = { focusRequester.requestFocus() },
		)
		onDispose {
			runtime.cancelPress()
			registration.close()
		}
	}
	LaunchedEffect(enabled, focusOwners) {
		if (enabled) focusOwners.retryPendingRestore()
	}
	TvInputCancellationEffect(runtime::cancelPress)

	Box(
		modifier = modifier.tvFocusableSurfaceModifier(
			spec = spec,
			binding = TvFocusableSurfaceBinding(
				focusRequester = focusRequester,
				runtime = runtime,
				focusOwners = focusOwners,
				focusScale = focusScale,
				onActivate = { latestOnActivate() },
			),
		),
		contentAlignment = Alignment.Center,
	) {
		CompositionLocalProvider(
			LocalTvContentColor provides spec.colors.content,
			LocalTvSurfaceColors provides spec.colors,
		) {
			ProvideTextStyle(spec.textStyle.copy(color = spec.colors.content)) {
				content()
			}
		}
	}
}

private fun Modifier.tvFocusableSurfaceModifier(
	spec: TvFocusableSurfaceSpec,
	binding: TvFocusableSurfaceBinding,
): Modifier = scale(binding.focusScale)
	.tvFocusBoundary(
		indicatorWidth = Tokens.SemanticComponent.focusIndicatorWidth,
		separatorWidth = Tokens.SemanticComponent.focusSeparatorWidth,
		indicatorColor = binding.runtime.focusBoundaryColor(),
		separatorColor = binding.runtime.focusSeparatorColor(),
		shape = spec.shape,
	)
	.clip(spec.shape)
	.background(spec.colors.container, spec.shape)
	.background(binding.runtime.stateLayerColor(), spec.shape)
	.defaultMinSize(minWidth = spec.minTarget, minHeight = spec.minTarget)
	.focusRequester(binding.focusRequester)
	.tvFocusOwner(spec.semantics.id, binding.focusOwners)
	.onFocusChanged { binding.runtime.onFocusChanged(it.hasFocus) }
	.onPreviewKeyEvent { event ->
		val pressEvent = event.toTvPressEvent() ?: return@onPreviewKeyEvent false
		if (!spec.state.enabled) return@onPreviewKeyEvent false
		binding.runtime.routePress(pressEvent)
	}
	.combinedClickable(
		enabled = spec.state.enabled,
		role = spec.role,
		onClick = binding.onActivate,
	)
	.semantics(mergeDescendants = true) {
		contentDescription = spec.semantics.accessibleName
		role = spec.role
		selected = spec.state.selection == TvSelection.Selected
		spec.semantics.accessibleStateDescription?.let { stateDescription = it }
		if (!spec.state.enabled) disabled()
	}

private fun validateSurfaceSpec(spec: TvFocusableSurfaceSpec) {
	require(spec.semantics.id.isNotBlank()) { "Focusable primitives require a stable ID." }
	require(spec.semantics.accessibleName.isNotBlank()) { "Focusable primitives require an accessible name." }
	require(!spec.state.availability.requiresStateDescription() || !spec.semantics.accessibleStateDescription.isNullOrBlank()) {
		"Non-ready states require a localized accessible state description."
	}
}

private fun TvAvailability.requiresStateDescription(): Boolean = when (this) {
	TvAvailability.Loading,
	TvAvailability.Restricted,
	TvAvailability.Locked,
	TvAvailability.Error -> true
	TvAvailability.Ready,
	TvAvailability.Disabled -> false
}

private fun TvFocusableSurfaceRuntime.focusBoundaryColor(): Color =
	if (focused) Tokens.SemanticColor.focusIndicator else Color.Transparent

private fun TvFocusableSurfaceRuntime.focusSeparatorColor(): Color =
	if (focused) Tokens.SemanticColor.focusSeparator else Color.Transparent

private fun TvFocusableSurfaceRuntime.stateLayerColor(): Color = when {
	pressed -> Tokens.SemanticColor.interactionPressedLayer
	focused -> Tokens.SemanticColor.interactionFocusLayer
	else -> Color.Transparent
}

internal fun focusAnimationSpec(reducedMotion: Boolean): DurationBasedAnimationSpec<Float> =
	if (reducedMotion) {
		snap()
	} else {
		tween(
			durationMillis = Tokens.SemanticMotion.FastMillis,
			easing = Tokens.SemanticMotion.standardEasing,
		)
	}
