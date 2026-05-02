package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.KeyEvent as AndroidKeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class HomeFragment : Fragment(), View.OnKeyListener {
	private val sessionRepository by inject<SessionRepository>()
	private val serverRepository by inject<ServerRepository>()
	private val notificationRepository by inject<NotificationsRepository>()

	private lateinit var backCallback: OnBackPressedCallback

	private var handleHomeBackPressed: () -> Boolean = { false }
	private var handleHomeKeyPressed: (Int) -> Boolean = { false }
	private var refreshHomeContent: () -> Unit = {}
	private var restoreHomeFocus: () -> Unit = {}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		backCallback = object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				if (!handleHomeBackPressed()) {
					isEnabled = false
					requireActivity().onBackPressedDispatcher.onBackPressed()
					isEnabled = true
				}
			}
		}
		requireActivity().onBackPressedDispatcher.addCallback(this, backCallback)
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = content {
		JellyfinTheme {
			val heroViewModel = koinViewModel<HomeHeroViewModel>()
			val rowsViewModel = koinViewModel<HomeRowsViewModel>()
			val heroState by heroViewModel.state.collectAsState()
			val rowsState by rowsViewModel.state

			SideEffect {
				refreshHomeContent = {
					heroViewModel.refresh()
					rowsViewModel.refresh()
				}
			}

			HomeScreen(
				heroState = heroState,
				rowsState = rowsState,
				onBackPressedHandlerChange = { handleHomeBackPressed = it },
				onKeyPressedHandlerChange = { handleHomeKeyPressed = it },
				onRestoreFocusChange = { restoreHomeFocus = it },
			)
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		sessionRepository.currentSession
			.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
			.map { session ->
				if (session == null) null
				else serverRepository.getServer(session.serverId)
			}
			.onEach { server ->
				notificationRepository.updateServerNotifications(server)
			}
			.launchIn(viewLifecycleOwner.lifecycleScope)
	}

	override fun onResume() {
		super.onResume()
		viewLifecycleOwner.lifecycleScope.launch {
			refreshHomeContent()
			delay(120)
			restoreHomeFocus()
			delay(300)
			restoreHomeFocus()
		}
	}

	override fun onDestroyView() {
		handleHomeBackPressed = { false }
		handleHomeKeyPressed = { false }
		refreshHomeContent = {}
		restoreHomeFocus = {}
		super.onDestroyView()
	}

	override fun onKey(view: View?, keyCode: Int, event: AndroidKeyEvent?): Boolean {
		if (event?.action != AndroidKeyEvent.ACTION_DOWN) return false

		return handleHomeKeyPressed(keyCode)
	}

	fun onKeyUp(keyCode: Int): Boolean = handleHomeKeyPressed(keyCode)
}
