package com.lantern.manosabamodsync.sync;

import com.lantern.manosabamodsync.manifest.SyncManifest;
import com.lantern.manosabamodsync.manifest.SyncModEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SyncPlanner {
	private SyncPlanner() {
	}

	public static SyncPlan plan(SyncManifest manifest, List<LocalModInfo> localMods) {
		Map<String, List<LocalModInfo>> localById = new HashMap<>();
		for (LocalModInfo localMod : localMods) {
			localById.computeIfAbsent(normalize(localMod.id()), ignored -> new ArrayList<>()).add(localMod);
		}

		List<SyncModEntry> missing = new ArrayList<>();
		List<SyncModEntry> mismatched = new ArrayList<>();
		List<SyncModEntry> valid = new ArrayList<>();
		Set<String> requiredIds = new HashSet<>();
		long totalDownloadSize = 0L;

		for (SyncModEntry entry : manifest.mods()) {
			if (!entry.shouldSyncToClient()) {
				continue;
			}

			String id = normalize(entry.id());
			requiredIds.add(id);
			List<LocalModInfo> candidates = localById.getOrDefault(id, List.of());
			if (candidates.isEmpty()) {
				missing.add(entry);
				totalDownloadSize += Math.max(0L, entry.size());
				continue;
			}

			boolean hashMatches = candidates.stream()
					.anyMatch(local -> local.sha256().equalsIgnoreCase(entry.sha256()));
			if (hashMatches) {
				valid.add(entry);
			} else {
				mismatched.add(entry);
				totalDownloadSize += Math.max(0L, entry.size());
			}
		}

		List<LocalModInfo> extra = localMods.stream()
				.filter(local -> !requiredIds.contains(normalize(local.id())))
				.toList();

		return new SyncPlan(missing, mismatched, valid, extra, totalDownloadSize, !missing.isEmpty() || !mismatched.isEmpty());
	}

	public static SyncPlan planByFileName(SyncManifest manifest, List<LocalModInfo> localMods) {
		Map<String, LocalModInfo> localByFileName = new HashMap<>();
		for (LocalModInfo localMod : localMods) {
			localByFileName.put(normalize(localMod.fileName()), localMod);
		}

		List<SyncModEntry> missing = new ArrayList<>();
		List<SyncModEntry> mismatched = new ArrayList<>();
		List<SyncModEntry> valid = new ArrayList<>();
		Set<String> requiredFileNames = new HashSet<>();
		long totalDownloadSize = 0L;

		for (SyncModEntry entry : manifest.mods()) {
			if (!entry.shouldSyncToClient()) {
				continue;
			}

			String fileName = normalize(entry.fileName());
			requiredFileNames.add(fileName);
			LocalModInfo local = localByFileName.get(fileName);
			if (local == null) {
				missing.add(entry);
				totalDownloadSize += Math.max(0L, entry.size());
				continue;
			}

			if (local.sha256().equalsIgnoreCase(entry.sha256())) {
				valid.add(entry);
			} else {
				mismatched.add(entry);
				totalDownloadSize += Math.max(0L, entry.size());
			}
		}

		List<LocalModInfo> extra = localMods.stream()
				.filter(local -> !requiredFileNames.contains(normalize(local.fileName())))
				.toList();

		return new SyncPlan(missing, mismatched, valid, extra, totalDownloadSize, !missing.isEmpty() || !mismatched.isEmpty());
	}

	private static String normalize(String id) {
		return id == null ? "" : id.toLowerCase(Locale.ROOT);
	}
}
