package org.jellyfin.androidtv.auth.store

import org.jellyfin.androidtv.auth.model.AuthenticationSessionEnvelope
import org.jellyfin.androidtv.auth.model.AuthenticationStoreServer

internal fun AuthenticationStoreServer.nextAuthorityGeneration(): Long? =
	authorityGeneration.takeUnless { it == Long.MAX_VALUE }?.plus(1)

internal fun AuthenticationStoreServer.hasMatchingActiveUser(envelope: AuthenticationSessionEnvelope): Boolean {
	val active = envelope.activeProfile ?: return false
	return users[active.profileUserId]?.accessToken == active.accessToken
}
