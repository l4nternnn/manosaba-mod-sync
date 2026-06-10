package com.lantern.manosabamodsync.manifest;

import com.lantern.manosabamodsync.ManosabaModSync;
import com.lantern.manosabamodsync.config.ServerSyncConfig;
import com.lantern.manosabamodsync.hash.Sha256Util;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class ManifestGenerator {
	private static final String MINECRAFT_VERSION = "1.21.8";
	private static final String LOADER = "fabric";

	private ManifestGenerator() {
	}

	public static SyncManifest generate(ServerSyncConfig config) {
		Path modsDirectory = FabricLoader.getInstance().getGameDir().resolve("mods");
		return generate(config, modsDirectory);
	}

	public static SyncManifest generate(ServerSyncConfig config, Path modsDirectory) {
		List<SyncModEntry> entries = new ArrayList<>();
		Set<String> whitelist = normalizeSet(config.syncMods);
		Set<String> ignored = normalizeSet(config.ignoreMods);

		if (whitelist.isEmpty()) {
			ManosabaModSync.LOGGER.warn("Server syncMods is empty; manifest will not include all server mods automatically");
			return manifest(config, entries);
		}

		Map<String, List<Path>> jarsByModId = scanFabricMods(modsDirectory);
		for (String id : whitelist) {
			if (ignored.contains(id)) {
				ManosabaModSync.LOGGER.info("Skipping ignored sync mod {}", id);
				continue;
			}

			List<Path> candidates = jarsByModId.getOrDefault(id, List.of());
			if (candidates.isEmpty()) {
				ManosabaModSync.LOGGER.warn("Configured sync mod {} was not found in {}", id, modsDirectory);
				continue;
			}
			if (candidates.size() > 1) {
				ManosabaModSync.LOGGER.error("Found multiple jars for mod id {}; skipping to avoid an ambiguous manifest: {}", id, candidates);
				continue;
			}

			Path jar = candidates.getFirst();
			try {
				ModJarMetadata metadata = JarModMetadataReader.read(jar).orElseThrow();
				String fileName = jar.getFileName().toString();
				entries.add(new SyncModEntry(
						metadata.id(),
						fileName,
						metadata.version(),
						"both",
						Sha256Util.sha256(jar),
						Files.size(jar),
						downloadUrl(config.baseDownloadUrl, fileName),
						true
				));
			} catch (Exception e) {
				ManosabaModSync.LOGGER.error("Failed to add {} to sync manifest", jar, e);
			}
		}

		ManosabaModSync.LOGGER.info("Generated sync manifest with {} mod(s)", entries.size());
		return manifest(config, entries);
	}

	private static Map<String, List<Path>> scanFabricMods(Path modsDirectory) {
		Map<String, List<Path>> jarsByModId = new HashMap<>();
		if (Files.notExists(modsDirectory)) {
			ManosabaModSync.LOGGER.warn("Mods directory {} does not exist", modsDirectory);
			return jarsByModId;
		}

		try (Stream<Path> paths = Files.list(modsDirectory)) {
			paths.filter(path -> path.getFileName().toString().endsWith(".jar"))
					.forEach(path -> {
						try {
							JarModMetadataReader.read(path).ifPresent(metadata ->
									jarsByModId.computeIfAbsent(normalize(metadata.id()), ignored -> new ArrayList<>()).add(path));
						} catch (IOException e) {
							ManosabaModSync.LOGGER.warn("Unable to inspect mod jar {}", path, e);
						}
					});
		} catch (IOException e) {
			ManosabaModSync.LOGGER.error("Unable to scan mods directory {}", modsDirectory, e);
		}
		return jarsByModId;
	}

	private static SyncManifest manifest(ServerSyncConfig config, List<SyncModEntry> entries) {
		return new SyncManifest(
				config.packId,
				config.packName,
				config.packVersion,
				MINECRAFT_VERSION,
				LOADER,
				ManosabaModSync.MOD_VERSION,
				entries
		);
	}

	private static String downloadUrl(String baseDownloadUrl, String fileName) {
		String base = baseDownloadUrl.endsWith("/") ? baseDownloadUrl : baseDownloadUrl + "/";
		return URI.create(base).resolve(fileName).toString();
	}

	private static Set<String> normalizeSet(List<String> values) {
		Set<String> normalized = new HashSet<>();
		if (values == null) {
			return normalized;
		}
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				normalized.add(normalize(value));
			}
		}
		return normalized;
	}

	private static String normalize(String value) {
		return value.toLowerCase(Locale.ROOT);
	}
}
