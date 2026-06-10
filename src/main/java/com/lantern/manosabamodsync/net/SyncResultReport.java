package com.lantern.manosabamodsync.net;

public record SyncResultReport(String status, String message) {
	public static SyncResultReport syncing() {
		return new SyncResultReport("SYNCING", "Client started syncing");
	}

	public static SyncResultReport restartRequired() {
		return new SyncResultReport("RESTART_REQUIRED", "Client staged mods and must restart");
	}

	public static SyncResultReport failed(String message) {
		return new SyncResultReport("SYNC_FAILED", message);
	}
}
