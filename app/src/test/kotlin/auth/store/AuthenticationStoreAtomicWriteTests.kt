package org.jellyfin.androidtv.auth.store

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.auth.model.AuthenticationActiveProfileSession
import org.jellyfin.androidtv.auth.model.AuthenticationCommittedPendingCleanup
import org.jellyfin.androidtv.auth.model.AuthenticationOwnerRecoverySession
import org.jellyfin.androidtv.auth.model.AuthenticationPendingSwitch
import org.jellyfin.androidtv.auth.model.AuthenticationPendingSwitchPhase
import org.jellyfin.androidtv.auth.model.AuthenticationSessionEnvelope
import org.jellyfin.androidtv.auth.model.AuthenticationStoreServer
import org.jellyfin.androidtv.auth.model.AuthenticationStoreUser
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

class AuthenticationStoreAtomicWriteTests : FunSpec({
	test("failed replacement preserves both durable and in-memory committed envelope") {
		val file = FailpointAuthenticationStoreFile()
		val store = AuthenticationStore(file)
		val serverId = UUID.randomUUID()
		val oldEnvelope = envelope("old-token", epoch = 3)
		val targetEnvelope = envelope("target-token", epoch = 4)
		store.putServer(
			serverId,
			AuthenticationStoreServer(
				name = "Server",
				address = "https://jellyfin.example",
				sessionEnvelope = oldEnvelope,
			),
		) shouldBe true
		file.failNextReplace = true

		val expected = store.getAuthoritySnapshot(serverId)!!
		store.replaceSessionEnvelope(serverId, expected, targetEnvelope) shouldBe null

		store.getAuthoritySnapshot(serverId)?.envelope shouldBe oldEnvelope
		AuthenticationStore(file).getAuthoritySnapshot(serverId)?.envelope shouldBe oldEnvelope
	}

	test("one successful replacement persists identity token epoch and cleanup as one document") {
		val file = FailpointAuthenticationStoreFile()
		val store = AuthenticationStore(file)
		val serverId = UUID.randomUUID()
		store.putServer(serverId, AuthenticationStoreServer("Server", "https://jellyfin.example"))
		val committed = envelope("target-token", epoch = 19)

		val expected = store.getAuthoritySnapshot(serverId)!!
		store.replaceSessionEnvelope(serverId, expected, committed)?.envelope shouldBe committed

		AuthenticationStore(file).getAuthoritySnapshot(serverId)?.envelope shouldBe committed
		file.successfulReplacements shouldBe 2
	}

	test("same-generation compare-and-swap rejects a stale envelope and preserves its winner across restart") {
		val file = FailpointAuthenticationStoreFile()
		val store = AuthenticationStore(file)
		val serverId = UUID.randomUUID()
		val envelopeZero = envelope("zero-token", epoch = 0)
		store.putServer(
			serverId,
			AuthenticationStoreServer("Server", "https://jellyfin.example", sessionEnvelope = envelopeZero),
		)
		val snapshotZero = store.getAuthoritySnapshot(serverId)!!
		val envelopeOne = envelope("one-token", epoch = 1)
		val snapshotOne = store.replaceSessionEnvelope(serverId, snapshotZero, envelopeOne)!!

		store.replaceSessionEnvelope(serverId, snapshotZero, envelope("two-token", epoch = 2)) shouldBe null

		snapshotZero.generation shouldBe snapshotOne.generation
		store.getAuthoritySnapshot(serverId) shouldBe snapshotOne
		AuthenticationStore(file).getAuthoritySnapshot(serverId) shouldBe snapshotOne
	}

	test("stale preparing snapshot cannot replace a same-generation committed cleanup marker") {
		val fixture = StoredAuthorityFixture()
		val initial = fixture.store.getAuthoritySnapshot(fixture.serverId)!!
		val switchId = UUID.randomUUID()
		val preparingEnvelope = initial.envelope!!.copy(
			pendingSwitch = AuthenticationPendingSwitch(
				switchId = switchId,
				targetProfileUserId = UUID.randomUUID(),
				oldProfileUserId = fixture.profileUserId,
				oldSessionEpoch = initial.envelope.sessionEpoch,
				phase = AuthenticationPendingSwitchPhase.PREPARING,
				createdAtEpochMillis = 1,
			),
		)
		val preparing = fixture.store.replaceSessionEnvelope(fixture.serverId, initial, preparingEnvelope)!!
		val pending = preparingEnvelope.pendingSwitch!!
		val committedEnvelope = preparingEnvelope.copy(
			activeProfile = preparingEnvelope.activeProfile!!.copy(
				profileUserId = pending.targetProfileUserId,
				accessToken = "committed-token",
			),
			sessionEpoch = preparingEnvelope.sessionEpoch + 1,
			pendingSwitch = pending.copy(phase = AuthenticationPendingSwitchPhase.INSTALLING),
			cleanupMarker = AuthenticationCommittedPendingCleanup(switchId),
		)
		val committed = fixture.store.replaceSessionEnvelope(fixture.serverId, preparing, committedEnvelope)!!
		val staleOrdinaryEnvelope = preparingEnvelope.copy(cleanupMarker = null)

		fixture.store.replaceSessionEnvelope(
			fixture.serverId,
			preparing,
			staleOrdinaryEnvelope,
			requireActiveUserToken = true,
		) shouldBe null

		committed.generation shouldBe preparing.generation
		fixture.store.getAuthoritySnapshot(fixture.serverId) shouldBe committed
		AuthenticationStore(fixture.file).getAuthoritySnapshot(fixture.serverId) shouldBe committed
	}

	test("logout atomically invalidates the user token and related envelope across cold start") {
		val fixture = StoredAuthorityFixture()

		fixture.store.invalidateUser(fixture.serverId, fixture.profileUserId) shouldBe true

		val restarted = AuthenticationStore(fixture.file)
		restarted.getUser(fixture.serverId, fixture.profileUserId)?.accessToken shouldBe null
		restarted.getAuthoritySnapshot(fixture.serverId)?.envelope shouldBe null
	}

	test("failed logout invalidation preserves user and envelope in memory and on disk") {
		val fixture = StoredAuthorityFixture()
		val before = fixture.store.getAuthoritySnapshot(fixture.serverId)?.envelope
		fixture.file.failNextReplace = true

		fixture.store.invalidateUser(fixture.serverId, fixture.profileUserId) shouldBe false

		fixture.store.getUser(fixture.serverId, fixture.profileUserId)?.accessToken shouldBe "profile-token"
		fixture.store.getAuthoritySnapshot(fixture.serverId)?.envelope shouldBe before
		AuthenticationStore(fixture.file).getAuthoritySnapshot(fixture.serverId)?.envelope shouldBe before
	}

	test("user removal atomically removes the user and related envelope across cold start") {
		val fixture = StoredAuthorityFixture()

		fixture.store.removeUser(fixture.serverId, fixture.profileUserId) shouldBe true

		val restarted = AuthenticationStore(fixture.file)
		restarted.getUser(fixture.serverId, fixture.profileUserId) shouldBe null
		restarted.getAuthoritySnapshot(fixture.serverId)?.envelope shouldBe null
	}

	test("selector sign-out invalidates owner and profile authority in one replacement") {
		val fixture = StoredAuthorityFixture()

		fixture.store.invalidateProfileSelector(fixture.serverId, fixture.ownerUserId) shouldBe true

		val restarted = AuthenticationStore(fixture.file)
		restarted.getUsers(fixture.serverId)?.values?.all { it.accessToken == null } shouldBe true
		restarted.getAuthoritySnapshot(fixture.serverId)?.envelope shouldBe null
	}

	test("account authority change invalidates a related envelope across cold start") {
		val fixture = StoredAuthorityFixture()
		val changed = fixture.store.getUser(fixture.serverId, fixture.profileUserId)!!.copy(
			profileSelectorOwnerUserId = UUID.randomUUID(),
		)

		fixture.store.updateUser(fixture.serverId, fixture.profileUserId) { changed } shouldBe true

		AuthenticationStore(fixture.file).getAuthoritySnapshot(fixture.serverId)?.envelope shouldBe null
	}

	test("stale server metadata write cannot restore authority after logout") {
		val fixture = StoredAuthorityFixture()
		val staleWriteSucceeded = runAfterTerminalMutation(
			capture = { fixture.store.getServer(fixture.serverId)!! },
			terminalMutation = { fixture.store.invalidateUser(fixture.serverId, fixture.profileUserId) },
			staleWrite = { stale ->
				fixture.store.putServer(fixture.serverId, stale.copy(name = "Refreshed server"))
			},
		)

		staleWriteSucceeded shouldBe true
		val restarted = AuthenticationStore(fixture.file)
		restarted.getServer(fixture.serverId)?.name shouldBe "Refreshed server"
		restarted.getUser(fixture.serverId, fixture.profileUserId)?.accessToken shouldBe null
		restarted.getAuthoritySnapshot(fixture.serverId)?.envelope shouldBe null
	}

	test("stale user metadata write cannot restore authority after logout") {
		val fixture = StoredAuthorityFixture()
		val staleWriteSucceeded = runAfterTerminalMutation(
			capture = { fixture.store.getUser(fixture.serverId, fixture.profileUserId)!! },
			terminalMutation = { fixture.store.invalidateUser(fixture.serverId, fixture.profileUserId) },
			staleWrite = { stale ->
				fixture.store.putUser(fixture.serverId, fixture.profileUserId, stale.copy(name = "Refreshed profile"))
			},
		)

		staleWriteSucceeded shouldBe true
		val restarted = AuthenticationStore(fixture.file)
		restarted.getUser(fixture.serverId, fixture.profileUserId)?.name shouldBe "Refreshed profile"
		restarted.getUser(fixture.serverId, fixture.profileUserId)?.accessToken shouldBe null
		restarted.getAuthoritySnapshot(fixture.serverId)?.envelope shouldBe null
	}

	test("stale user metadata write cannot restore selector authority after sign out") {
		val fixture = StoredAuthorityFixture()
		val staleWriteSucceeded = runAfterTerminalMutation(
			capture = { fixture.store.getUser(fixture.serverId, fixture.profileUserId)!! },
			terminalMutation = { fixture.store.invalidateProfileSelector(fixture.serverId, fixture.ownerUserId) },
			staleWrite = { stale ->
				fixture.store.putUser(fixture.serverId, fixture.profileUserId, stale.copy(lastUsed = 99))
			},
		)

		staleWriteSucceeded shouldBe true
		val restarted = AuthenticationStore(fixture.file)
		restarted.getUser(fixture.serverId, fixture.profileUserId)?.lastUsed shouldBe 99
		restarted.getUser(fixture.serverId, fixture.profileUserId)?.accessToken shouldBe null
		restarted.getAuthoritySnapshot(fixture.serverId)?.envelope shouldBe null
	}

	test("stale user metadata write cannot recreate a removed user") {
		val fixture = StoredAuthorityFixture()
		val staleWriteSucceeded = runAfterTerminalMutation(
			capture = { fixture.store.getUser(fixture.serverId, fixture.profileUserId)!! },
			terminalMutation = { fixture.store.removeUser(fixture.serverId, fixture.profileUserId) },
			staleWrite = { stale ->
				fixture.store.putUser(fixture.serverId, fixture.profileUserId, stale.copy(lastUsed = 99))
			},
		)

		staleWriteSucceeded shouldBe false
		val restarted = AuthenticationStore(fixture.file)
		restarted.getUser(fixture.serverId, fixture.profileUserId) shouldBe null
		restarted.getAuthoritySnapshot(fixture.serverId)?.envelope shouldBe null
	}

	test("stale envelope write cannot restore authority after logout") {
		val fixture = StoredAuthorityFixture()
		val staleWriteSucceeded = runAfterTerminalMutation(
			capture = { fixture.store.getAuthoritySnapshot(fixture.serverId)!! },
			terminalMutation = { fixture.store.invalidateUser(fixture.serverId, fixture.profileUserId) },
			staleWrite = { stale ->
				fixture.store.replaceSessionEnvelope(fixture.serverId, stale, stale.envelope!!) != null
			},
		)

		staleWriteSucceeded shouldBe false
		val restarted = AuthenticationStore(fixture.file)
		restarted.getUser(fixture.serverId, fixture.profileUserId)?.accessToken shouldBe null
		restarted.getAuthoritySnapshot(fixture.serverId)?.envelope shouldBe null
	}

	test("stale envelope write cannot restore selector authority after sign out") {
		val fixture = StoredAuthorityFixture()
		val staleWriteSucceeded = runAfterTerminalMutation(
			capture = { fixture.store.getAuthoritySnapshot(fixture.serverId)!! },
			terminalMutation = { fixture.store.invalidateProfileSelector(fixture.serverId, fixture.ownerUserId) },
			staleWrite = { stale ->
				fixture.store.replaceSessionEnvelope(fixture.serverId, stale, stale.envelope!!) != null
			},
		)

		staleWriteSucceeded shouldBe false
		val restarted = AuthenticationStore(fixture.file)
		restarted.getUsers(fixture.serverId)?.values?.all { it.accessToken == null } shouldBe true
		restarted.getAuthoritySnapshot(fixture.serverId)?.envelope shouldBe null
	}

	test("stale envelope write cannot restore a removed user") {
		val fixture = StoredAuthorityFixture()
		val staleWriteSucceeded = runAfterTerminalMutation(
			capture = { fixture.store.getAuthoritySnapshot(fixture.serverId)!! },
			terminalMutation = { fixture.store.removeUser(fixture.serverId, fixture.profileUserId) },
			staleWrite = { stale ->
				fixture.store.replaceSessionEnvelope(fixture.serverId, stale, stale.envelope!!) != null
			},
		)

		staleWriteSucceeded shouldBe false
		val restarted = AuthenticationStore(fixture.file)
		restarted.getUser(fixture.serverId, fixture.profileUserId) shouldBe null
		restarted.getAuthoritySnapshot(fixture.serverId)?.envelope shouldBe null
	}

	test("ordinary envelope install requires the current user token to match") {
		val fixture = StoredAuthorityFixture()
		val staleEnvelope = fixture.store.getAuthoritySnapshot(fixture.serverId)!!.envelope!!
		fixture.store.invalidateUser(fixture.serverId, fixture.profileUserId) shouldBe true
		val currentAuthority = fixture.store.getAuthoritySnapshot(fixture.serverId)!!

		fixture.store.replaceSessionEnvelope(
			fixture.serverId,
			currentAuthority,
			staleEnvelope,
			requireActiveUserToken = true,
		) shouldBe null

		val restarted = AuthenticationStore(fixture.file)
		restarted.getUser(fixture.serverId, fixture.profileUserId)?.accessToken shouldBe null
		restarted.getAuthoritySnapshot(fixture.serverId)?.envelope shouldBe null
	}

	test("v2 decode rejects malformed documents and invalid envelope invariants without throwing") {
		val fixture = StoredAuthorityFixture()
		val valid = fixture.file.contents()
		val malformed = listOf(
			"{",
			"""{"version":2}""",
			"""{"version":2,"servers":[]}""",
			valid.replace("\"authority_generation\":0", "\"authority_generation\":-1"),
			valid.replace("\"session_epoch\":3", "\"session_epoch\":-1"),
			valid.replaceLast("\"device_id\":\"device\"", "\"device_id\":\"other-device\""),
			valid.replaceLast(
				"\"owner_user_id\":\"${fixture.ownerUserId}\"",
				"\"owner_user_id\":\"${UUID.randomUUID()}\"",
			),
			valid.replaceLast(
				"\"profile_selector_id\":\"${fixture.selectorId}\"",
				"\"profile_selector_id\":\"${UUID.randomUUID()}\"",
			),
		)

		for (document in malformed) {
			fixture.file.seed(document)
			AuthenticationStore(fixture.file).getServers() shouldBe emptyMap()
		}
	}

	test("rejected v2 storage logs only a sanitized code without throwable or token-bearing JSON") {
		val fixture = StoredAuthorityFixture()
		val captured = mutableListOf<String>()
		Timber.plant(CapturingTree(captured))
		try {
			fixture.file.seed(fixture.file.contents().replace("\"session_epoch\":3", "\"session_epoch\":-1"))

			AuthenticationStore(fixture.file).getServers() shouldBe emptyMap()

			captured.single() shouldBe "Authentication store rejected: authority-invariant"
			captured.single().contains("profile-token") shouldBe false
		} finally {
			Timber.uprootAll()
		}
	}
})

private fun <T> runAfterTerminalMutation(
	capture: () -> T,
	terminalMutation: () -> Boolean,
	staleWrite: (T) -> Boolean,
): Boolean {
	val captured = CountDownLatch(1)
	val terminalCommitted = CountDownLatch(1)
	val writer = FutureTask {
		val stale = capture()
		captured.countDown()
		check(terminalCommitted.await(2, TimeUnit.SECONDS))
		staleWrite(stale)
	}
	Thread(writer, "stale-auth-store-writer").start()
	check(captured.await(2, TimeUnit.SECONDS))
	check(terminalMutation())
	terminalCommitted.countDown()
	return writer.get(2, TimeUnit.SECONDS)
}

private fun envelope(token: String, epoch: Long) = AuthenticationSessionEnvelope(
	activeProfile = AuthenticationActiveProfileSession(
		profileUserId = UUID.nameUUIDFromBytes(token.toByteArray()),
		accessToken = token,
		deviceId = "device",
	),
	sessionEpoch = epoch,
)

private class FailpointAuthenticationStoreFile : AuthenticationStoreFile {
	private var contents: String? = null
	var failNextReplace = false
	var successfulReplacements = 0

	fun contents(): String = checkNotNull(contents)

	fun seed(contents: String) {
		this.contents = contents
	}

	override fun exists(): Boolean = contents != null

	override fun readText(): String = checkNotNull(contents)

	override fun replace(contents: String) {
		if (failNextReplace) {
			failNextReplace = false
			throw IOException("simulated atomic replacement failure")
		}
		this.contents = contents
		successfulReplacements++
	}
}

private class StoredAuthorityFixture {
	val file = FailpointAuthenticationStoreFile()
	val store = AuthenticationStore(file)
	val serverId: UUID = UUID.randomUUID()
	val ownerUserId: UUID = UUID.randomUUID()
	val profileUserId: UUID = UUID.randomUUID()
	val selectorId: UUID = UUID.randomUUID()

	init {
		store.putServer(
			serverId,
			AuthenticationStoreServer(
				name = "Server",
				address = "https://jellyfin.example",
				users = mapOf(
					ownerUserId to AuthenticationStoreUser(
						name = "Owner",
						accessToken = "owner-token",
						profileSelectorId = selectorId,
					),
					profileUserId to AuthenticationStoreUser(
						name = "Profile",
						accessToken = "profile-token",
						profileSelectorId = selectorId,
						profileSelectorOwnerUserId = ownerUserId,
					),
				),
				sessionEnvelope = AuthenticationSessionEnvelope(
					activeProfile = AuthenticationActiveProfileSession(
						profileUserId = profileUserId,
						accessToken = "profile-token",
						deviceId = "device",
						profileSelectorId = selectorId,
						ownerUserId = ownerUserId,
					),
					ownerRecovery = AuthenticationOwnerRecoverySession(
						ownerUserId = ownerUserId,
						accessToken = "owner-token",
						profileSelectorId = selectorId,
						deviceId = "device",
					),
					sessionEpoch = 3,
				),
			),
		)
	}
}

private fun String.replaceLast(oldValue: String, newValue: String): String {
	val index = lastIndexOf(oldValue)
	check(index >= 0)
	return replaceRange(index, index + oldValue.length, newValue)
}

private class CapturingTree(
	private val messages: MutableList<String>,
) : Timber.Tree() {
	override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
		messages += message
	}
}
