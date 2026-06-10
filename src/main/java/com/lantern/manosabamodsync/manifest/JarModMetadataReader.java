package com.lantern.manosabamodsync.manifest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class JarModMetadataReader {
	private static final String FABRIC_MOD_JSON = "fabric.mod.json";

	private JarModMetadataReader() {
	}

	public static Optional<ModJarMetadata> read(Path jarPath) throws IOException {
		try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
			ZipEntry entry = zipFile.getEntry(FABRIC_MOD_JSON);
			if (entry == null) {
				return Optional.empty();
			}

			try (InputStreamReader reader = new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8)) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				String id = stringValue(json, "id", "");
				if (id.isBlank()) {
					return Optional.empty();
				}
				String name = stringValue(json, "name", id);
				String version = stringValue(json, "version", "unknown");
				return Optional.of(new ModJarMetadata(id, name, version));
			}
		}
	}

	private static String stringValue(JsonObject json, String key, String fallback) {
		JsonElement value = json.get(key);
		if (value == null || value.isJsonNull()) {
			return fallback;
		}
		if (value.isJsonPrimitive()) {
			return value.getAsString();
		}
		return fallback;
	}
}
