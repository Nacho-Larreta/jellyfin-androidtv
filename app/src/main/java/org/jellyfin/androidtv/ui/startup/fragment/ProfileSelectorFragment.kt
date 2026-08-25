package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.leanback.widget.BaseGridView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.PROFILE_PIN_MAX_LENGTH
import org.jellyfin.androidtv.auth.model.ProfileSelector
import org.jellyfin.androidtv.auth.model.ProfileSelectorUser
import org.jellyfin.androidtv.auth.model.isValidProfilePin
import org.jellyfin.androidtv.auth.repository.ProfileSelectorApiException
import org.jellyfin.androidtv.auth.repository.ProfileSelectorRepository
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.Session
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.databinding.FragmentProfileSelectorBinding
import org.jellyfin.androidtv.ui.card.UserCardView
import org.jellyfin.androidtv.ui.card.UserCardVisualStyle
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import org.jellyfin.androidtv.util.ListAdapter
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import timber.log.Timber
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class ProfileSelectorFragment : Fragment() {
	interface Host {
		fun onProfileSelectedFromSelector()
	}

	private val profileSelectorRepository: ProfileSelectorRepository by inject()
	private val serverRepository: ServerRepository by inject()
	private val sessionRepository: SessionRepository by inject()
	private val startupViewModel: StartupViewModel by activityViewModel()

	private var _binding: FragmentProfileSelectorBinding? = null
	private val binding get() = _binding!!
	private var activatingProfile = false

	private val profileAdapter by lazy {
		ProfileAdapter(
			startupViewModel = startupViewModel,
			serverRepository = serverRepository,
		).apply {
			onItemPressed = ::onProfilePressed
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		_binding = FragmentProfileSelectorBinding.inflate(inflater, container, false)

		binding.users.adapter = profileAdapter
		binding.users.setGravity(Gravity.CENTER)
		binding.users.setHorizontalSpacing(resources.getDimensionPixelSize(R.dimen.profile_selector_card_spacing))
		binding.users.windowAlignment = BaseGridView.WINDOW_ALIGN_BOTH_EDGE
		binding.cancelButton.isVisible = false
		binding.signOutButton.setOnClickListener {
			val session = sessionRepository.currentSession.value ?: return@setOnClickListener
			viewLifecycleOwner.lifecycleScope.launch {
				sessionRepository.destroyCurrentSession()
				profileSelectorRepository.signOut(session)
			}
		}

		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		loadSelector()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	private fun loadSelector() {
		val session = sessionRepository.currentSession.value ?: run {
			requireActivity().finishAfterTransition()
			return
		}

		binding.message.isVisible = true
		binding.message.setText(R.string.loading)

		lifecycleScope.launch {
			val selector = try {
				withContext(Dispatchers.IO) {
					profileSelectorRepository.getCurrentSelector(session)
				}
			} catch (error: ProfileSelectorApiException) {
				binding.message.text = error.message
				Toast.makeText(context, error.message, Toast.LENGTH_LONG).show()
				return@launch
			}

			if (!isAdded) {
				return@launch
			}

			if (selector == null) {
				requireActivity().finishAfterTransition()
				return@launch
			}

			renderSelector(selector)
		}
	}

	private fun renderSelector(selector: ProfileSelector) {
		val session = sessionRepository.currentSession.value
		val server = session?.let { serverRepository.currentServer.value ?: startupViewModel.getServer(it.serverId) }
		binding.subtitle.text = getString(R.string.profile_selector_subtitle_owner, server?.name ?: selector.ownerUserName ?: "Jellyfin")
		binding.message.isVisible = false
		binding.noUsersWarning.isVisible = selector.profiles.isEmpty()
		val sessionProfileUserId = session?.userId?.takeIf { userId ->
			selector.profiles.any { profile -> profile.id == userId }
		}
		profileAdapter.activeProfileUserId = sessionProfileUserId ?: selector.currentDeviceProfileUserId
		profileAdapter.items = selector.profiles
		binding.users.isFocusable = selector.profiles.any()
		if (selector.profiles.any()) {
			val selectedPosition = selector.profiles
				.indexOfFirst { profile -> profile.id == profileAdapter.activeProfileUserId }
				.takeIf { index -> index >= 0 }
				?: 0

			binding.users.post {
				centerProfileList(selector.profiles.size)
				binding.users.setSelectedPosition(selectedPosition)
				binding.users.requestFocus()
			}
		}
	}

	private fun centerProfileList(profileCount: Int) {
		val itemCount = profileCount.coerceAtLeast(0)
		val spacing = resources.getDimensionPixelSize(R.dimen.profile_selector_card_spacing)
		val cardWidth = resources.getDimensionPixelSize(R.dimen.profile_selector_card_width)
		val contentWidth = (itemCount * cardWidth) + ((itemCount - 1).coerceAtLeast(0) * spacing)
		val horizontalPadding = ((binding.users.width - contentWidth) / 2).coerceAtLeast(0)

		binding.users.setPadding(horizontalPadding, 0, horizontalPadding, 0)
	}

	private fun onProfilePressed(profile: ProfileSelectorUser) {
		if (activatingProfile) {
			return
		}

		val session = sessionRepository.currentSession.value ?: return

		if (profile.isDisabled) {
			Toast.makeText(context, R.string.profile_selector_profile_disabled, Toast.LENGTH_LONG).show()
			return
		}

		if (profile.id == session.userId && session.isActiveProfileSession()) {
			requireActivity().finishAfterTransition()
			return
		}

		if (profile.requiresPin) {
			showPinPrompt(profile)
			return
		}

		activateProfile(profile, null)
	}

	private fun showPinPrompt(profile: ProfileSelectorUser, errorMessage: String? = null) {
		val pinInput = EditText(requireContext()).apply {
			filters = arrayOf(InputFilter.LengthFilter(PROFILE_PIN_MAX_LENGTH))
			hint = getString(R.string.profile_selector_pin_hint)
			contentDescription = getString(R.string.profile_selector_pin_description)
			imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
			inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
		}

		val dialog = AlertDialog.Builder(requireContext())
			.setTitle(getString(R.string.profile_selector_pin_title, profile.name))
			.setMessage(errorMessage ?: getString(R.string.profile_selector_pin_message))
			.setView(pinInput)
			.setPositiveButton(R.string.lbl_ok, null)
			.setNegativeButton(R.string.btn_cancel) { _, _ -> restoreProfileSelectorFocus() }
			.create()

		dialog.setOnShowListener {
			dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
				val pin = pinInput.text?.toString()
				if (!isValidProfilePin(pin)) {
					pinInput.error = getString(R.string.profile_selector_pin_format_invalid)
					pinInput.requestFocus()
					return@setOnClickListener
				}

				dialog.dismiss()
				activateProfile(profile, pin)
			}
			pinInput.requestFocus()
			dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
		}
		dialog.setOnCancelListener { restoreProfileSelectorFocus() }
		dialog.show()
	}

	private fun restoreProfileSelectorFocus() {
		binding.users.requestFocus()
	}

	private fun activateProfile(profile: ProfileSelectorUser, pin: String?) {
		val session = sessionRepository.currentSession.value ?: return
		activatingProfile = true
		binding.message.isVisible = true
		binding.message.setText(R.string.login_authenticating)

		lifecycleScope.launch {
			try {
				val activeSession = withContext(Dispatchers.IO) {
					profileSelectorRepository.activateProfile(session, profile.id, pin)
				}
				val switched = sessionRepository.switchCurrentSession(activeSession)
				if (!switched) {
					showActivationFailure()
					return@launch
				}

				(requireActivity() as? Host)?.onProfileSelectedFromSelector()
			} catch (error: CancellationException) {
				throw error
			} catch (error: ProfileSelectorApiException) {
				activatingProfile = false
				handleProfileSelectorError(profile, error)
			} catch (error: Exception) {
				Timber.e(error, "Unexpected error while activating profile ${profile.id}")
				showActivationFailure()
			}
		}
	}

	private fun showActivationFailure() {
		activatingProfile = false
		binding.message.setText(R.string.login_server_unavailable)
		Toast.makeText(context, R.string.login_server_unavailable, Toast.LENGTH_LONG).show()
		binding.users.requestFocus()
	}

	private fun handleProfileSelectorError(profile: ProfileSelectorUser, error: ProfileSelectorApiException) {
		when (error.code) {
			"PROFILE_PIN_REQUIRED" -> {
				binding.message.isVisible = false
				showPinPrompt(profile)
			}

			"PROFILE_PIN_INVALID" -> {
				binding.message.isVisible = false
				showPinPrompt(profile, getString(R.string.profile_selector_pin_invalid))
			}

			"PROFILE_PIN_LOCKED" -> {
				binding.message.setText(R.string.profile_selector_pin_locked)
				Toast.makeText(context, R.string.profile_selector_pin_locked, Toast.LENGTH_LONG).show()
			}

			else -> {
				binding.message.text = error.message
				Toast.makeText(context, error.message, Toast.LENGTH_LONG).show()
			}
		}
	}

	private fun Session?.isActiveProfileSession(): Boolean {
		val currentSession = this ?: return false
		return currentSession.ownerUserId != null && currentSession.ownerUserId != currentSession.userId
	}

	private class ProfileAdapter(
		private val startupViewModel: StartupViewModel,
		private val serverRepository: ServerRepository,
	) : ListAdapter<ProfileSelectorUser, ProfileAdapter.ViewHolder>() {
		var onItemPressed: (ProfileSelectorUser) -> Unit = {}
		var activeProfileUserId: UUID? = null

		override fun areItemsTheSame(old: ProfileSelectorUser, new: ProfileSelectorUser): Boolean = old.id == new.id

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
			ViewHolder(UserCardView(parent.context))

		override fun onBindViewHolder(holder: ViewHolder, item: ProfileSelectorUser) {
			val server = serverRepository.currentServer.value ?: startupViewModel.getServer(item.serverId)

			holder.cardView.name = item.name
			holder.cardView.image = server?.let { startupViewModel.getUserImage(it, item) }
			holder.cardView.colorSeed = item.id.toString()
			holder.cardView.colorIndex = items.indexOfFirst { profile -> profile.id == item.id }.takeIf { it >= 0 }
			holder.cardView.visualStyle = UserCardVisualStyle.ProfileSelector
			holder.cardView.metaText = when {
				item.isOwner -> holder.cardView.context.getString(R.string.profile_selector_owner_meta)
				item.hasParentalRestrictions -> holder.cardView.context.getString(R.string.profile_selector_kids_meta)
				item.requiresPin -> holder.cardView.context.getString(R.string.profile_selector_pin_meta)
				else -> null
			}
			holder.cardView.badgeText = when {
				item.requiresPin -> holder.cardView.context.getString(R.string.profile_selector_pin_badge)
				item.hasParentalRestrictions -> holder.cardView.context.getString(R.string.lbl_kids).uppercase()
				else -> null
			}
			holder.cardView.activeIndicator = item.id == activeProfileUserId
			holder.cardView.alpha = if (item.isDisabled) 0.5f else 1f
			holder.cardView.setOnClickListener {
				onItemPressed(item)
			}
		}

		private class ViewHolder(
			val cardView: UserCardView,
		) : RecyclerView.ViewHolder(cardView)
	}
}
