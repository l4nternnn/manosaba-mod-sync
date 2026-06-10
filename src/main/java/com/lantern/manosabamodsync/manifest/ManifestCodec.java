package com.lantern.manosabamodsync.manifest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

public final class ManifestCodec {
	private static final Gson GSON = new GsonBuilder()
			.disableHtmlEscaping()
			.setPrettyPrinting()
			.create();

	private ManifestCodec() {
	}

	public static String toJson(SyncManifest manifest) {
		return GSON.toJson(manifest);
	}

	public static SyncManifest fromJson(String json) {
		try {
			SyncManifest manifest = GSON.fromJson(json, SyncManifest.class);
			if (manifest == null) {
				throw new JsonParseException("Manifest is empty");
			}
			return manifest;
		} catch (RuntimeException e) {
			throw new JsonParseException("Unable to parse sync manifest", e);
		}
	}
}
