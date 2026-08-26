package org.jellyfin.androidtv.di

import android.content.Context

import org.jellyfin.androidtv.auth.repository.AuthenticationRepository
import org.jellyfin.androidtv.auth.repository.AuthenticationRepositoryImpl
import org.jellyfin.androidtv.auth.repository.ProfileSelectorRepository
import org.jellyfin.androidtv.auth.repository.ProfileSelectorRepositoryImpl
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerRepositoryImpl
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepositoryImpl
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.SessionRepositoryImpl
import org.jellyfin.androidtv.auth.session.AuthenticationSessionSwitchStore
import org.jellyfin.androidtv.auth.session.CompositeSessionSwitchQuiescePort
import org.jellyfin.androidtv.auth.session.OkHttpSessionSwitchApi
import org.jellyfin.androidtv.auth.session.SessionAdmissionBarrier
import org.jellyfin.androidtv.auth.session.SessionRepositoryRuntimePort
import org.jellyfin.androidtv.auth.session.SessionSwitchApi
import org.jellyfin.androidtv.auth.session.SessionSwitchCoordinator
import org.jellyfin.androidtv.auth.session.SessionSwitchEnvironment
import org.jellyfin.androidtv.auth.session.SessionSwitchQuiescePort
import org.jellyfin.androidtv.auth.session.SessionSwitchRuntimePort
import org.jellyfin.androidtv.auth.session.SessionSwitchStore
import org.jellyfin.androidtv.auth.store.AuthenticationPreferences
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.koin.dsl.module

val authModule = module {
	single { AuthenticationStore(get<Context>()) }
	single { AuthenticationPreferences(get()) }
	single { SessionAdmissionBarrier() }
	single<SessionSwitchStore> { AuthenticationSessionSwitchStore(get()) }
	single<SessionSwitchApi> { OkHttpSessionSwitchApi(get(), get(), get(), get()) }
	single<SessionSwitchRuntimePort> { SessionRepositoryRuntimePort(get(), get()) }
	single<SessionSwitchQuiescePort> { CompositeSessionSwitchQuiescePort(getAll()) }
	single {
		SessionSwitchCoordinator(
			environment = SessionSwitchEnvironment(
				api = get(),
				store = get(),
				barrier = get(),
				quiescePort = get(),
				runtimePort = get(),
			),
		)
	}

	single<AuthenticationRepository> {
		AuthenticationRepositoryImpl(get(), get(), get(), get(), get(), get(defaultDeviceInfo))
	}
	single<ProfileSelectorRepository> { ProfileSelectorRepositoryImpl(get(), get(), get(), get()) }
	single<ServerRepository> { ServerRepositoryImpl(get(), get()) }
	single<ServerUserRepository> { ServerUserRepositoryImpl(get(), get()) }
	single<SessionRepository> {
		SessionRepositoryImpl(
			authenticationPreferences = get(),
			authenticationStore = get(),
			userApiClient = get(),
			preferencesRepository = get(),
			defaultDeviceInfo = get(defaultDeviceInfo),
			userRepository = get(),
			serverRepository = get(),
			telemetryPreferences = get(),
			playbackQuiescePort = get(),
		)
	}

	factory {
		val serverRepository = get<ServerRepository>()
		serverRepository.currentServer.value?.serverVersion ?: ServerRepository.minimumServerVersion
	}
}
