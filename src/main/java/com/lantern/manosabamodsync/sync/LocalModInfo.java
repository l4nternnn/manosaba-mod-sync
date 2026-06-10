package com.lantern.manosabamodsync.sync;

import java.nio.file.Path;

public record LocalModInfo(
		String id,
		String name,
		String version,
		Path path,
		String fileName,
		String sha256,
		long size
) {
}
