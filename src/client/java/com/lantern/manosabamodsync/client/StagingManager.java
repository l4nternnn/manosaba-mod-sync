package com.lantern.manosabamodsync.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lantern.manosabamodsync.config.ClientSyncConfig;
import com.lantern.manosabamodsync.manifest.SyncModEntry;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class StagingManager {
	private static final Gson GSON = new GsonBuilder()
			.disableHtmlEscaping()
			.setPrettyPrinting()
			.create();

	private final Path gameDirectory;
	private final ClientSyncConfig config;

	public StagingManager(ClientSyncConfig config) {
		this(FabricLoader.getInstance().getGameDir(), config);
	}

	public StagingManager(Path gameDirectory, ClientSyncConfig config) {
		this.gameDirectory = gameDirectory;
		this.config = config;
	}

	public Path syncDirectory() {
		return gameDirectory.resolve(config.syncDirectory);
	}

	public Path stagedDirectory() {
		return syncDirectory().resolve("staged");
	}

	public Path backupDirectory() {
		return syncDirectory().resolve("backup");
	}

	public Path stateFile() {
		return syncDirectory().resolve("sync-state.json");
	}

	public void ensureDirectories() throws IOException {
		Files.createDirectories(stagedDirectory());
		Files.createDirectories(backupDirectory());
	}

	public void clearFailedDownloads() throws IOException {
		Path staged = stagedDirectory();
		if (Files.notExists(staged)) {
			return;
		}
		try (var paths = Files.list(staged)) {
			for (Path path : paths.filter(candidate -> candidate.getFileName().toString().endsWith(".download")).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	public void writeSyncState(List<SyncModEntry> entries) throws IOException {
		Files.createDirectories(syncDirectory());
		try (Writer writer = Files.newBufferedWriter(stateFile(), StandardCharsets.UTF_8)) {
			GSON.toJson(new SyncState(entries), writer);
		}
	}

	public void clearSyncState() throws IOException {
		Files.deleteIfExists(stateFile());
	}

	private record SyncState(List<SyncModEntry> stagedMods) {
	}
}
