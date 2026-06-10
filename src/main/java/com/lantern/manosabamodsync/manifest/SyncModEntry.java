package com.lantern.manosabamodsync.manifest;

public record SyncModEntry(
		String id,
		String fileName,
		String version,
		String side,
		String sha256,
		long size,
		String downloadUrl,
		boolean required
) {
	public boolean shouldSyncToClient() {
		return !"server".equalsIgnoreCase(side);
	}
}
