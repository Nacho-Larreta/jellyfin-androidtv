package org.jellyfin.androidtv.screensaver

import org.jellyfin.androidtv.util.apiclient.albumPrimaryImage
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

internal fun selectEligibleLibraryItems(
	items: List<BaseItemDto>?,
	policy: ScreensaverContentPolicy,
): List<BaseItemDto> = items.orEmpty().filter { item ->
	policy.isEligible(item) && item.itemBackdropImages.isNotEmpty()
}

internal fun selectEligibleNowPlayingItem(
	item: BaseItemDto?,
	policy: ScreensaverContentPolicy?,
): BaseItemDto? = item?.takeIf { candidate ->
	policy?.isEligible(candidate) == true && candidate.hasNowPlayingArtwork()
}

private fun BaseItemDto.hasNowPlayingArtwork(): Boolean =
	itemImages[ImageType.PRIMARY] != null || albumPrimaryImage != null || parentImages[ImageType.PRIMARY] != null
