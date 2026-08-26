package org.jellyfin.androidtv.ui.base.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jellyfin.design.Tokens
import org.jellyfin.androidtv.ui.base.ProvideTextStyle

@Composable
fun TvOverlay(
	spec: TvOverlaySpec,
	onDismissRequest: () -> Unit,
	environment: TvOverlayEnvironment,
	modifier: Modifier = Modifier,
	content: @Composable BoxScope.() -> Unit,
) {
	val latestDismissRequest by rememberUpdatedState(onDismissRequest)
	val latestRestoreFocus by rememberUpdatedState(spec.restoreFocus)
	DisposableEffect(spec.semantics.id, spec.layer, environment.layers) {
		val registration = environment.layers.activate(spec.semantics.id, spec.layer) {
			latestDismissRequest()
		}
		onDispose(registration::close)
	}
	DisposableEffect(spec.semantics.id, environment.focusOwners) {
		onDispose { environment.focusOwners.restore(latestRestoreFocus) }
	}
	LaunchedEffect(spec.initialFocusId, environment) {
		environment.focusOwners.restore(TvFocusRestoreRequest(triggerId = spec.initialFocusId))
	}
	BackHandler { environment.layers.handleBack() }
	TvInputCancellationEffect(environment.layers::cancelPendingInput)

	Dialog(
		onDismissRequest = { environment.layers.handleBack() },
		properties = DialogProperties(
			dismissOnBackPress = false,
			dismissOnClickOutside = false,
			usePlatformDefaultWidth = false,
			decorFitsSystemWindows = false,
		),
	) {
		ProvideTextStyle(
			Tokens.SemanticTypography.bodyLarge.copy(color = Tokens.SemanticColor.contentPrimary)
		) {
			Box(
				modifier = modifier
					.fillMaxSize()
					.onPreviewKeyEvent(environment.layers::routeEscape)
					.background(Tokens.SemanticColor.overlayScrim)
					.focusGroup()
					.semantics {
						paneTitle = spec.semantics.accessibleName
						isTraversalGroup = true
					},
				contentAlignment = Alignment.Center,
				content = content,
			)
		}
	}
}
