package com.lantern.manosabamodsync;

import com.lantern.manosabamodsync.hash.Sha256Util;
import com.lantern.manosabamodsync.manifest.ManifestCodec;
import com.lantern.manosabamodsync.manifest.SyncManifest;
import com.lantern.manosabamodsync.manifest.SyncModEntry;
import com.lantern.manosabamodsync.sync.LocalModInfo;
import com.lantern.manosabamodsync.sync.SyncPlan;
import com.lantern.manosabamodsync.sync.SyncPlanner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CoreLogicTest {
	public static void main(String[] args) throws Exception {
		sha256UsesLowercaseHex();
		manifestRoundTripsJson();
		plannerClassifiesMissingMismatchedValidAndExtra();
		plannerCanMatchManualReleaseAssetsByFileName();
	}

	private static void sha256UsesLowercaseHex() throws Exception {
		Path file = Files.createTempFile("manosaba-sha", ".txt");
		Files.writeString(file, "abc", StandardCharsets.UTF_8);

		String hash = Sha256Util.sha256(file);

		assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash);
		Files.deleteIfExists(file);
	}

	private static void manifestRoundTripsJson() {
		SyncManifest manifest = new SyncManifest(
				"pack",
				"Pack Name",
				"2026.06.08",
				"1.21.8",
				"fabric",
				"1.0.0",
				List.of(new SyncModEntry(
						"fabric-api",
						"fabric-api.jar",
						"0.136.1",
						"both",
						"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
						1234L,
						"https://example.com/mods/fabric-api.jar",
						true
				))
		);

		String json = ManifestCodec.toJson(manifest);
		SyncManifest parsed = ManifestCodec.fromJson(json);

		assertEquals("pack", parsed.packId());
		assertEquals(1, parsed.mods().size());
		assertEquals("fabric-api", parsed.mods().getFirst().id());
		assertEquals(1234L, parsed.mods().getFirst().size());
	}

	private static void plannerClassifiesMissingMismatchedValidAndExtra() {
		SyncManifest manifest = new SyncManifest(
				"pack",
				"Pack Name",
				"2026.06.08",
				"1.21.8",
				"fabric",
				"1.0.0",
				List.of(
						new SyncModEntry("missing", "missing.jar", "1", "both", "aaa", 10L, "https://example.com/missing.jar", true),
						new SyncModEntry("changed", "changed.jar", "1", "both", "bbb", 20L, "https://example.com/changed.jar", true),
						new SyncModEntry("ok", "ok.jar", "1", "both", "ccc", 30L, "https://example.com/ok.jar", true)
				)
		);

		List<LocalModInfo> localMods = List.of(
				new LocalModInfo("changed", "Changed", "old", Path.of("changed-old.jar"), "changed-old.jar", "old-hash", 9L),
				new LocalModInfo("ok", "Ok", "1", Path.of("ok.jar"), "ok.jar", "ccc", 30L),
				new LocalModInfo("extra", "Extra", "1", Path.of("extra.jar"), "extra.jar", "ddd", 40L)
		);

		SyncPlan plan = SyncPlanner.plan(manifest, localMods);

		assertEquals(1, plan.missingMods().size());
		assertEquals("missing", plan.missingMods().getFirst().id());
		assertEquals(1, plan.mismatchedMods().size());
		assertEquals("changed", plan.mismatchedMods().getFirst().id());
		assertEquals(1, plan.validMods().size());
		assertEquals("ok", plan.validMods().getFirst().id());
		assertEquals(1, plan.extraMods().size());
		assertEquals("extra", plan.extraMods().getFirst().id());
		assertEquals(30L, plan.totalDownloadSize());
		assertTrue(plan.requiresRestart());
	}

	private static void plannerCanMatchManualReleaseAssetsByFileName() {
		SyncManifest manifest = new SyncManifest(
				"static",
				"Static Pack",
				"pack",
				"1.21.8",
				"fabric",
				"1.0.1",
				List.of(
						new SyncModEntry("ice-phone-1.3.0", "ice-phone-1.3.0.jar", "pack", "both", "aaa", 10L, "https://example.com/ice-phone.jar", true),
						new SyncModEntry("modernui", "ModernUI.jar", "pack", "both", "bbb", 20L, "https://example.com/modernui.jar", true)
				)
		);

		List<LocalModInfo> localMods = List.of(
				new LocalModInfo("not-the-release-id", "ModernUI", "1", Path.of("ModernUI.jar"), "ModernUI.jar", "old-hash", 20L),
				new LocalModInfo("extra", "Extra", "1", Path.of("extra.jar"), "extra.jar", "ccc", 30L)
		);

		SyncPlan plan = SyncPlanner.planByFileName(manifest, localMods);

		assertEquals(1, plan.missingMods().size());
		assertEquals("ice-phone-1.3.0.jar", plan.missingMods().getFirst().fileName());
		assertEquals(1, plan.mismatchedMods().size());
		assertEquals("ModernUI.jar", plan.mismatchedMods().getFirst().fileName());
		assertEquals(1, plan.extraMods().size());
		assertEquals("extra.jar", plan.extraMods().getFirst().fileName());
		assertEquals(30L, plan.totalDownloadSize());
	}

	private static void assertTrue(boolean value) {
		if (!value) {
			throw new AssertionError("Expected true");
		}
	}

	private static void assertEquals(Object expected, Object actual) {
		if (!expected.equals(actual)) {
			throw new AssertionError("Expected " + expected + " but got " + actual);
		}
	}
}
