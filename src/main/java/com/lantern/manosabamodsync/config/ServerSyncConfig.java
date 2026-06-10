package com.lantern.manosabamodsync.config;

import java.util.List;

public class ServerSyncConfig {
	public boolean enabled = true;
	public String packId = "manosaba-script-server";
	public String packName = "Manosaba Script Server";
	public String packVersion = "2026.06.08";
	public String baseDownloadUrl = "https://example.com/mods/";
	public boolean requireClientSyncMod = true;
	public boolean disconnectOnMissingSyncMod = true;
	public List<String> syncMods = List.of("fabric-api");
	public List<String> ignoreMods = List.of("spark", "servercore", "lithium", "krypton");
	public List<String> allowedDownloadHosts = List.of("example.com");
}
