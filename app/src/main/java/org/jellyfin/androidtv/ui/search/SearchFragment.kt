package org.jellyfin.androidtv.ui.search

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent as AndroidKeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import org.jellyfin.androidtv.ui.browsing.PreDispatchKeyEventHandler
import org.jellyfin.androidtv.ui.input.toRemoteKeyStrokeOrNull

internal class SearchFocusReclaimHandshake {
	private var handler: (() -> Boolean)? = null
	private var pending = false

	fun install(handler: () -> Boolean): Boolean {
		this.handler = handler
		return pending
	}

	fun reclaim(): Boolean {
		val claimed = handler?.invoke() == true
		pending = !claimed
		return claimed
	}

	fun clear() {
		handler = null
		pending = false
	}
}

class SearchFragment : Fragment(), PreDispatchKeyEventHandler {
	companion object {
		const val EXTRA_QUERY = "query"
		private const val REMOTE_FOCUS_RECLAIM_DELAY_MILLIS = 250L
	}

	private lateinit var backCallback: OnBackPressedCallback

	private var handleSearchBackPressed: () -> Boolean = { false }
	private var handleSearchKeyPressed: (Int) -> Boolean = { false }
	private val focusReclaimHandshake = SearchFocusReclaimHandshake()

	private inner class SearchKeyHost(context: Context) : FrameLayout(context) {
		private val preImeKeyRouter = SearchPreImeKeyRouter(
			backKeyCode = AndroidKeyEvent.KEYCODE_BACK,
			handleBack = { keyCode -> handleSearchKeyPressed(keyCode) },
		)

		fun reclaimRemoteFocus() {
			if (focusReclaimHandshake.reclaim()) return
			requestFocus()
		}

		override fun dispatchKeyEventPreIme(event: AndroidKeyEvent): Boolean {
			val keyStroke = event.toRemoteKeyStrokeOrNull()
			if (keyStroke != null && preImeKeyRouter.route(keyStroke)) return true

			return super.dispatchKeyEventPreIme(event)
		}

		override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
			super.onWindowFocusChanged(hasWindowFocus)
			if (!hasWindowFocus) preImeKeyRouter.reset()
		}

		override fun onDetachedFromWindow() {
			preImeKeyRouter.reset()
			super.onDetachedFromWindow()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		backCallback = object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				if (!handleSearchBackPressed()) {
					isEnabled = false
					requireActivity().onBackPressedDispatcher.onBackPressed()
					isEnabled = true
				}
			}
		}
		requireActivity().onBackPressedDispatcher.addCallback(this, backCallback)
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		val host = SearchKeyHost(requireContext()).apply {
			isFocusable = true
			isFocusableInTouchMode = true
		}
		val composeView = ComposeView(host.context).apply {
			setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
			setContent {
				SearchScreen(
					initialQuery = arguments?.getString(EXTRA_QUERY).orEmpty(),
					onBackPressedHandlerChange = { handleSearchBackPressed = it },
					onKeyPressedHandlerChange = { handleSearchKeyPressed = it },
					onFocusReclaimHandlerChange = { handler ->
						if (focusReclaimHandshake.install(handler)) {
							host.post(host::reclaimRemoteFocus)
						}
					},
				)
			}
		}
		host.addView(
			composeView,
			ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT,
			),
		)
		host.post {
			host.reclaimRemoteFocus()
			host.postDelayed(host::reclaimRemoteFocus, REMOTE_FOCUS_RECLAIM_DELAY_MILLIS)
		}
		return host
	}

	override fun onResume() {
		super.onResume()

		// The search screen owns TV remote navigation. Re-claim focus after
		// returning from IME, details, or player screens so DPAD events do not
		// leak to the previously focused Leanback view behind Compose.
		view?.post {
			(view as? SearchKeyHost)?.reclaimRemoteFocus()
			view?.postDelayed(
				{ (view as? SearchKeyHost)?.reclaimRemoteFocus() },
				REMOTE_FOCUS_RECLAIM_DELAY_MILLIS,
			)
		}
	}

	override fun onPreDispatchKeyDown(keyCode: Int): Boolean = handleSearchKeyPressed(keyCode)

	override fun onDestroyView() {
		handleSearchBackPressed = { false }
		handleSearchKeyPressed = { false }
		focusReclaimHandshake.clear()
		super.onDestroyView()
	}
}
