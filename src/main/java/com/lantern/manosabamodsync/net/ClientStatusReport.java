package com.lantern.manosabamodsync.net;

public record ClientStatusReport(
		String status,
		int missingCount,
		int mismatchedCount,
		String message
) {
	public static ClientStatusReport ok() {
		return new ClientStatusReport("OK", 0, 0, "Client mods are complete");
	}

	public static ClientStatusReport syncRequired(int missingCount, int mismatchedCount) {
		String status = missingCount > 0 && mismatchedCount > 0
				? "SYNC_REQUIRED"
				: missingCount > 0 ? "MISSING_MODS" : "MISMATCHED_MODS";
		return new ClientStatusReport(status, missingCount, mismatchedCount, "Client needs to sync mods");
	}
}
