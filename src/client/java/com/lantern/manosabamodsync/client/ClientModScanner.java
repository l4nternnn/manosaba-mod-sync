package com.lantern.manosabamodsync.client;

import com.lantern.manosabamodsync.ManosabaModSync;
import com.lantern.manosabamodsync.hash.Sha256Util;
import com.lantern.manosabamodsync.manifest.JarModMetadataReader;
import com.lantern.manosabamodsync.manifest.ModJarMetadata;
import com.lantern.manosabamodsync.sync.LocalModInfo;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class ClientModScanner {
	private ClientModScanner() {
	}

	public static List<LocalModInfo> scanDefaultModsDirectory() {
		return scan(FabricLoader.getInstance().getGameDir().resolve("mods"));
	}

	public static List<LocalModInfo> scan(Path modsDirectory) {
		List<LocalModInfo> mods = new ArrayList<>();
		if (Files.notExists(modsDirectory)) {
			ManosabaModSync.LOGGER.warn("Client mods directory {} does not exist", modsDirectory);
			return mods;
		}

		try (Stream<Path> paths = Files.list(modsDirectory)) {
			paths.filter(path -> path.getFileName().toString().endsWith(".jar"))
					.forEach(path -> inspect(path, mods));
		} catch (IOException e) {
			ManosabaModSync.LOGGER.error("Failed to scan client mods directory {}", modsDirectory, e);
		}

		ManosabaModSync.LOGGER.info("Client scanned {} local Fabric mod(s)", mods.size());
		return mods;
	}

	private static void inspect(Path path, List<LocalModInfo> mods) {
		try {
			ModJarMetadata metadata = JarModMetadataReader.read(path).orElse(null);
			if (metadata == null) {
				ManosabaModSync.LOGGER.debug("Skipping non-Fabric jar {}", path);
				return;
			}
			mods.add(new LocalModInfo(
					metadata.id(),
					metadata.name(),
					metadata.version(),
					path,
					path.getFileName().toString(),
					Sha256Util.sha256(path),
					Files.size(path)
			));
		} catch (Exception e) {
			ManosabaModSync.LOGGER.warn("Unable to inspect local mod jar {}", path, e);
		}
	}
}
