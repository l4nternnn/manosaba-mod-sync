package com.lantern.manosabamodsync.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lantern.manosabamodsync.ManosabaModSync;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public final class ConfigManager {
	public static final String SERVER_CONFIG_NAME = "manosaba-mod-sync-server.json";
	public static final String CLIENT_CONFIG_NAME = "manosaba-mod-sync-client.json";

	private static final Gson GSON = new GsonBuilder()
			.disableHtmlEscaping()
			.setPrettyPrinting()
			.create();

	private ConfigManager() {
	}

	public static ServerSyncConfig loadServerConfig() {
		return load(SERVER_CONFIG_NAME, ServerSyncConfig.class, ServerSyncConfig::new);
	}

	public static ClientSyncConfig loadClientConfig() {
		return load(CLIENT_CONFIG_NAME, ClientSyncConfig.class, ClientSyncConfig::new);
	}

	private static <T> T load(String fileName, Class<T> type, Supplier<T> defaults) {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(fileName);
		try {
			Files.createDirectories(path.getParent());
			if (Files.notExists(path)) {
				T config = defaults.get();
				save(path, config);
				ManosabaModSync.LOGGER.info("Created default config {}", path);
				return config;
			}

			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				T config = GSON.fromJson(reader, type);
				if (config == null) {
					throw new IOException("Config file is empty");
				}
				ManosabaModSync.LOGGER.info("Loaded config {}", path);
				return config;
			}
		} catch (Exception e) {
			ManosabaModSync.LOGGER.error("Failed to load config {}, using defaults", path, e);
			return defaults.get();
		}
	}

	public static void saveServerConfig(ServerSyncConfig config) throws IOException {
		save(FabricLoader.getInstance().getConfigDir().resolve(SERVER_CONFIG_NAME), config);
	}

	public static void saveClientConfig(ClientSyncConfig config) throws IOException {
		save(FabricLoader.getInstance().getConfigDir().resolve(CLIENT_CONFIG_NAME), config);
	}

	private static void save(Path path, Object config) throws IOException {
		Files.createDirectories(path.getParent());
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			GSON.toJson(config, writer);
		}
	}
}
