package com.lantern.manosabamodsync.config;

import java.util.List;

public class ClientSyncConfig {
	public boolean enabled = true;
	public boolean autoCheckOnJoin = false;
	public boolean autoApplyStagedOnStartup = true;
	public boolean allowDownload = true;
	public List<String> allowedDownloadHosts = List.of(
			"download.example.com"
	);
	public String syncDirectory = "manosaba-sync";
	public boolean backupOldMods = true;
}
