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

class SearchFragment : Fragment() {
	companion object {
		const val EXTRA_QUERY = "query"
	}

	private lateinit var backCallback: OnBackPressedCallback

	private var handleSearchBackPressed: () -> Boolean = { false }
	private var handleSearchKeyPressed: (Int) -> Boolean = { false }

	private inner class SearchKeyHost(context: Context) : FrameLayout(context) {
		override fun dispatchKeyEvent(event: AndroidKeyEvent): Boolean {
			if (event.action == AndroidKeyEvent.ACTION_DOWN) {
				val handled = handleSearchKeyPressed(event.keyCode)
				if (handled) return true
			}

			return super.dispatchKeyEvent(event)
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
	): View = SearchKeyHost(requireContext()).apply {
		isFocusable = true
		isFocusableInTouchMode = true
		addView(
			ComposeView(context).apply {
				setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
				setContent {
					SearchScreen(
						initialQuery = arguments?.getString(EXTRA_QUERY).orEmpty(),
						onBackPressedHandlerChange = { handleSearchBackPressed = it },
						onKeyPressedHandlerChange = { handleSearchKeyPressed = it },
					)
				}
			},
			ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT,
			),
		)
		post {
			requestFocusFromTouch()
			requestFocus()
		}
	}

	override fun onResume() {
		super.onResume()

		// The search screen owns TV remote navigation. Re-claim focus after
		// returning from IME, details, or player screens so DPAD events do not
		// leak to the previously focused Leanback view behind Compose.
		view?.post {
			view?.requestFocusFromTouch()
			view?.requestFocus()
		}
	}

	override fun onDestroyView() {
		handleSearchBackPressed = { false }
		handleSearchKeyPressed = { false }
		super.onDestroyView()
	}
}
