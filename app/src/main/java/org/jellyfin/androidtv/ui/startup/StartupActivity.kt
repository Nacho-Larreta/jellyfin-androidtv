package org.jellyfin.androidtv.ui.startup

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.JellyfinApplication
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.ProfileSelectorRepository
import org.jellyfin.androidtv.auth.repository.ProfileSelectorStartupAction
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.SessionRepositoryState
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.databinding.ActivityStartupBinding
import org.jellyfin.androidtv.ui.background.AppBackground
import org.jellyfin.androidtv.ui.browsing.MainActivity
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.startup.fragment.SelectServerFragment
import org.jellyfin.androidtv.ui.startup.fragment.ProfileSelectorFragment
import org.jellyfin.androidtv.ui.startup.fragment.ServerFragment
import org.jellyfin.androidtv.ui.startup.fragment.SplashFragment
import org.jellyfin.androidtv.ui.startup.fragment.StartupToolbarFragment
import org.jellyfin.androidtv.util.applyTheme
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber
import java.util.UUID

class StartupActivity : FragmentActivity(), ProfileSelectorFragment.Host {
	companion object {
		const val EXTRA_ITEM_ID = "ItemId"
		const val EXTRA_ITEM_IS_USER_VIEW = "ItemIsUserView"
		const val EXTRA_HIDE_SPLASH = "HideSplash"
		const val EXTRA_OPEN_PROFILE_SELECTOR = "OpenProfileSelector"
	}

	private val startupViewModel: StartupViewModel by viewModel()
	private val noSessionStartupRouter: NoSessionStartupRouter by inject {
		parametersOf(::getLastServerId)
	}
	private val api: ApiClient by inject()
	private val profileSelectorRepository: ProfileSelectorRepository by inject()
	private val sessionRepository: SessionRepository by inject()
	private val userRepository: UserRepository by inject()
	private val navigationRepository: NavigationRepository by inject()
	private val itemLauncher: ItemLauncher by inject()

	private lateinit var binding: ActivityStartupBinding
	private var backgroundComposeView: ComposeView? = null
	private var firstFrameCoordinator: StartupFirstFrameCoordinator? = null
	private var openedForProfileSelector = false
	private var profileSelectorRequestConsumed = false
	private var profileSelectedFromSelector = false
	private var returningToMainActivity = false

	private val networkPermissionsRequester = registerForActivityResult(
		ActivityResultContracts.RequestMultiplePermissions()
	) { grants ->
		val anyRejected = grants.any { !it.value }

		if (anyRejected) {
			// Permission denied, exit the app.
			Toast.makeText(this, R.string.no_network_permissions, Toast.LENGTH_LONG).show()
			finish()
		} else {
			onPermissionsGranted()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		applyTheme()

		super.onCreate(savedInstanceState)
		window.setBackgroundDrawableResource(R.drawable.startup_window)

		binding = ActivityStartupBinding.inflate(layoutInflater)
		setContentView(binding.root)

		firstFrameCoordinator = StartupFirstFrameCoordinator(
			root = binding.root,
			startContent = ::startContentAfterFirstFrame,
			revealContent = ::removeStaticStartupSurface,
		).also(StartupFirstFrameCoordinator::install)
	}

	private fun startContentAfterFirstFrame() {
		attachBackgroundContent()
		binding.background.isVisible = true

		openedForProfileSelector = intent.getBooleanExtra(EXTRA_OPEN_PROFILE_SELECTOR, false)

		if (!intent.getBooleanExtra(EXTRA_HIDE_SPLASH, false)) showSplash()

		// Ensure basic permissions
		networkPermissionsRequester.launch(arrayOf(Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE))
	}

	private fun attachBackgroundContent() {
		if (backgroundComposeView != null) return

		val composeView = ComposeView(this).apply {
			layoutParams = FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT,
			)
			isFocusable = false
			descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
			setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
			setContent { AppBackground() }
		}

		backgroundComposeView = composeView
		binding.background.addView(composeView)
	}

	private fun removeStaticStartupSurface() {
		window.setBackgroundDrawableResource(R.color.not_quite_black)
		binding.root.removeView(binding.startupSurface)
		firstFrameCoordinator = null
	}

	override fun onDestroy() {
		firstFrameCoordinator?.cancel()
		firstFrameCoordinator = null
		super.onDestroy()
	}

	override fun onResume() {
		super.onResume()

		applyTheme()
	}

	private fun onPermissionsGranted() = sessionRepository.state
		.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
		.filter { it == SessionRepositoryState.READY }
		.map { sessionRepository.currentSession.value }
		.distinctUntilChanged()
		.onEach { session ->
			if (session != null) {
				if (returningToMainActivity) {
					return@onEach
				}

				if (openedForProfileSelector && profileSelectedFromSelector) {
					returnToMainActivityAfterProfileSwitch()
					return@onEach
				}

				val forceProfileSelector = consumeProfileSelectorRequest()
				val action = withContext(Dispatchers.IO) {
					profileSelectorRepository.resolveStartupAction(
						session = session,
						forceSelector = forceProfileSelector,
					)
				}

				if (returningToMainActivity || openedForProfileSelector && profileSelectedFromSelector) {
					return@onEach
				}

				when (action) {
					ProfileSelectorStartupAction.ContinueToApp -> {
						if (openedForProfileSelector) {
							returnToMainActivityAfterProfileSwitch()
							return@onEach
						}

						Timber.i("Found a ready runtime session, waiting for currentUser before opening the app.")
						showSplash()

						val currentUser = userRepository.currentUser.first { it != null }
						Timber.i("CurrentUser changed to ${currentUser?.id} while waiting for startup.")

						lifecycleScope.launch {
							openNextActivity()
						}
					}

					is ProfileSelectorStartupAction.ShowSelector -> {
						Timber.i("Opening profile selector for owner %s", action.selector.ownerUserId)
						showProfileSelector()
					}

					is ProfileSelectorStartupAction.SwitchSession -> {
						Timber.i("Auto-activating remembered profile %s", action.session.userId)
						sessionRepository.switchCurrentSession(action.session)
					}
				}
			} else {
				when (val destination = noSessionStartupRouter.route()) {
					is NoSessionStartupDestination.Server -> showServer(destination.id)
					NoSessionStartupDestination.ServerSelection -> showServerSelection()
				}
			}
		}.launchIn(lifecycleScope)

	private suspend fun getLastServerId() = startupViewModel.getLastServer()?.id

	private suspend fun openNextActivity() {
		val itemId = when {
			intent.action == Intent.ACTION_VIEW && intent.data != null -> intent.data.toString()
			else -> intent.getStringExtra(EXTRA_ITEM_ID)
		}?.toUUIDOrNull()
		val itemIsUserView = intent.getBooleanExtra(EXTRA_ITEM_IS_USER_VIEW, false)

		Timber.i("Determining next activity (action=${intent.action}, itemId=$itemId, itemIsUserView=$itemIsUserView)")

		// Start session
		(application as? JellyfinApplication)?.onSessionStart()

		// Create destination
		val destination = when {
			// Search is requested
			intent.action === Intent.ACTION_SEARCH -> Destinations.search(
				query = intent.getStringExtra(SearchManager.QUERY)
			)
			// User view item is requested
			itemId != null && itemIsUserView -> runCatching {
				val item = withContext(Dispatchers.IO) {
					api.userLibraryApi.getItem(itemId = itemId).content
				}
				itemLauncher.getUserViewDestination(item)
			}.onFailure { throwable ->
				Timber.w(throwable, "Failed to retrieve item $itemId from server.")
			}.getOrNull()
			// Other item is requested
			itemId != null -> Destinations.itemDetails(itemId)
			// No destination requested, use default
			else -> null
		}

		navigationRepository.reset(destination, true)

		val intent = Intent(this, MainActivity::class.java)
		// Clear navigation history
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
		Timber.i("Opening next activity $intent")
		startActivity(intent)
		finishAfterTransition()
	}

	// Fragment switching
	private fun showSplash() {
		// Prevent progress bar flashing
		if (supportFragmentManager.findFragmentById(R.id.content_view) is SplashFragment) return

		supportFragmentManager.commit {
			replace<SplashFragment>(R.id.content_view)
		}
	}

	private fun showServer(id: UUID) = supportFragmentManager.commit {
		replace<StartupToolbarFragment>(R.id.content_view)
		add<ServerFragment>(
			R.id.content_view,
			null,
			Bundle().apply {
				putString(ServerFragment.ARG_SERVER_ID, id.toString())
			},
		)
	}

	private fun showServerSelection() = supportFragmentManager.commit {
		replace<StartupToolbarFragment>(R.id.content_view)
		add<SelectServerFragment>(R.id.content_view)
	}

	private fun showProfileSelector() = supportFragmentManager.commit {
		replace<ProfileSelectorFragment>(R.id.content_view)
	}

	private fun consumeProfileSelectorRequest(): Boolean {
		if (!openedForProfileSelector || profileSelectorRequestConsumed) {
			return false
		}

		profileSelectorRequestConsumed = true
		intent.removeExtra(EXTRA_OPEN_PROFILE_SELECTOR)
		return true
	}

	override fun onProfileSelectedFromSelector() {
		profileSelectedFromSelector = true
		if (openedForProfileSelector &&
			sessionRepository.state.value == SessionRepositoryState.READY &&
			sessionRepository.currentSession.value != null
		) {
			returnToMainActivityAfterProfileSwitch()
		}
	}

	private fun returnToMainActivityAfterProfileSwitch() {
		if (returningToMainActivity) {
			return
		}

		returningToMainActivity = true
		Timber.i("Profile switch finished, returning to MainActivity with the new session.")
		navigationRepository.reset(clearHistory = true)
		finishAfterTransition()
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		openedForProfileSelector = intent.getBooleanExtra(EXTRA_OPEN_PROFILE_SELECTOR, false)
		profileSelectorRequestConsumed = false
		profileSelectedFromSelector = false
		returningToMainActivity = false
	}
}
