package com.lantern.manosabamodsync.manifest;

import java.util.List;

public record SyncManifest(
		String packId,
		String packName,
		String packVersion,
		String minecraftVersion,
		String loader,
		String requiredSyncModVersion,
		List<SyncModEntry> mods
) {
	public SyncManifest {
		mods = mods == null ? List.of() : List.copyOf(mods);
	}
}
