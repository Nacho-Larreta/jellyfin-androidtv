package org.jellyfin.androidtv.ui.base.designsystem

enum class TvAvailability {
	Ready,
	Loading,
	Disabled,
	Restricted,
	Locked,
	Error,
}

enum class TvSelection {
	Unselected,
	Selected,
}

enum class TvActivity {
	Inactive,
	Active,
	Buffering,
	Completed,
}

enum class TvInputMode {
	Pointer,
	Touch,
	Keyboard,
	Dpad,
	Assistive,
}

enum class TvMotionPreference {
	Default,
	Reduced,
}

data class TvComponentState(
	val availability: TvAvailability = TvAvailability.Ready,
	val selection: TvSelection = TvSelection.Unselected,
	val activity: TvActivity = TvActivity.Inactive,
	val inputMode: TvInputMode = TvInputMode.Dpad,
	val motion: TvMotionPreference = TvMotionPreference.Default,
) {
	val enabled: Boolean
		get() = availability == TvAvailability.Ready
}

enum class TvActionVariant {
	Primary,
	Secondary,
	Tertiary,
	Destructive,
}

enum class TvComponentSize {
	Compact,
	Standard,
	Comfortable,
}

enum class TvTone {
	Neutral,
	Informative,
	Success,
	Warning,
	Error,
}

data class TvSemantics(
	val id: String,
	val accessibleName: String,
	val accessibleStateDescription: String? = null,
)

data class TvActionSpec(
	val semantics: TvSemantics,
	val variant: TvActionVariant = TvActionVariant.Primary,
	val size: TvComponentSize = TvComponentSize.Standard,
	val state: TvComponentState = TvComponentState(),
)

data class TvChipSpec(
	val semantics: TvSemantics,
	val state: TvComponentState = TvComponentState(),
)

data class TvAvatarSpec(
	val semantics: TvSemantics,
	val size: TvComponentSize = TvComponentSize.Standard,
	val state: TvComponentState = TvComponentState(),
)

data class TvMediaCardSpec(
	val semantics: TvSemantics,
	val size: TvComponentSize = TvComponentSize.Standard,
	val tone: TvTone = TvTone.Neutral,
	val state: TvComponentState = TvComponentState(),
)

data class TvOverlaySpec(
	val semantics: TvSemantics,
	val restoreFocus: TvFocusRestoreRequest,
	val initialFocusId: String,
	val layer: TvLayer = TvLayer.Modal,
)

data class TvOverlayEnvironment(
	val layers: TvLayerCoordinator,
	val focusOwners: TvFocusOwnerRegistry,
)
