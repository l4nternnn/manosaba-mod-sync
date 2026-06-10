package com.lantern.manosabamodsync.client;

import com.lantern.manosabamodsync.ManosabaModSync;
import com.lantern.manosabamodsync.config.ClientSyncConfig;
import com.lantern.manosabamodsync.manifest.JarModMetadataReader;
import com.lantern.manosabamodsync.manifest.ModJarMetadata;
import com.lantern.manosabamodsync.sync.LocalModInfo;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class StartupApplyManager {
	private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private StartupApplyManager() {
	}

	public static void applyStagedOnStartup(ClientSyncConfig config) {
		StagingManager stagingManager = new StagingManager(config);
		Path stagedDirectory = stagingManager.stagedDirectory();
		if (Files.notExists(stagedDirectory)) {
			return;
		}

		List<Path> stagedJars;
		try (Stream<Path> paths = Files.list(stagedDirectory)) {
			stagedJars = paths.filter(path -> path.getFileName().toString().endsWith(".jar")).toList();
		} catch (IOException e) {
			ManosabaModSync.LOGGER.error("Unable to list staged mods {}", stagedDirectory, e);
			return;
		}

		if (stagedJars.isEmpty()) {
			return;
		}

		Path modsDirectory = FabricLoader.getInstance().getGameDir().resolve("mods");
		try {
			Files.createDirectories(modsDirectory);
			Files.createDirectories(stagingManager.backupDirectory());
		} catch (IOException e) {
			ManosabaModSync.LOGGER.error("Unable to prepare mods or backup directories", e);
			return;
		}

		List<LocalModInfo> localMods = ClientModScanner.scan(modsDirectory);
		Path backupBatch = stagingManager.backupDirectory().resolve(LocalDateTime.now().format(BACKUP_TIME));
		boolean allApplied = true;

		for (Path stagedJar : stagedJars) {
			try {
				applyOne(config, modsDirectory, backupBatch, localMods, stagedJar);
			} catch (Exception e) {
				allApplied = false;
				ManosabaModSync.LOGGER.error("Failed to apply staged mod {}; keeping staged file for retry", stagedJar, e);
			}
		}

		if (allApplied) {
			try {
				stagingManager.clearSyncState();
				ManosabaModSync.LOGGER.info("Applied {} staged mod(s) on startup", stagedJars.size());
			} catch (IOException e) {
				ManosabaModSync.LOGGER.warn("Applied staged mods but failed to clear sync state", e);
			}
		}
	}

	private static void applyOne(ClientSyncConfig config, Path modsDirectory, Path backupBatch,
			List<LocalModInfo> localMods, Path stagedJar) throws IOException {
		Optional<ModJarMetadata> stagedMetadata = JarModMetadataReader.read(stagedJar);
		String stagedFileName = stagedJar.getFileName().toString();

		if (config.backupOldMods) {
			for (LocalModInfo localMod : localMods) {
				boolean sameId = stagedMetadata.map(metadata -> metadata.id().equalsIgnoreCase(localMod.id())).orElse(false);
				boolean sameFileName = stagedFileName.equals(localMod.fileName());
				if (sameId || sameFileName) {
					Files.createDirectories(backupBatch);
					Path backupTarget = backupBatch.resolve(localMod.fileName());
					Files.move(localMod.path(), backupTarget, StandardCopyOption.REPLACE_EXISTING);
					ManosabaModSync.LOGGER.info("Backed up old mod {} to {}", localMod.path(), backupTarget);
				}
			}
		}

		Path target = modsDirectory.resolve(stagedFileName);
		Files.move(stagedJar, target, StandardCopyOption.REPLACE_EXISTING);
		ManosabaModSync.LOGGER.info("Applied staged mod {} to {}", stagedJar, target);
	}
}
