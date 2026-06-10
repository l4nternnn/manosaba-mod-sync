package com.lantern.manosabamodsync.sync;

import com.lantern.manosabamodsync.manifest.SyncModEntry;

import java.util.ArrayList;
import java.util.List;

public record SyncPlan(
		List<SyncModEntry> missingMods,
		List<SyncModEntry> mismatchedMods,
		List<SyncModEntry> validMods,
		List<LocalModInfo> extraMods,
		long totalDownloadSize,
		boolean requiresRestart
) {
	public SyncPlan {
		missingMods = List.copyOf(missingMods);
		mismatchedMods = List.copyOf(mismatchedMods);
		validMods = List.copyOf(validMods);
		extraMods = List.copyOf(extraMods);
	}

	public List<SyncModEntry> entriesToDownload() {
		List<SyncModEntry> entries = new ArrayList<>(missingMods.size() + mismatchedMods.size());
		entries.addAll(missingMods);
		entries.addAll(mismatchedMods);
		return entries;
	}

	public boolean isComplete() {
		return missingMods.isEmpty() && mismatchedMods.isEmpty();
	}
}
