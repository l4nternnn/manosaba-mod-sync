package com.lantern.manosabamodsync.client;

import com.lantern.manosabamodsync.ManosabaModSync;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class ManosabaModSyncClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		if (!FabricLoader.getInstance().isModLoaded("modernui")) {
			ManosabaModSync.LOGGER.error("Modern UI is required for the Manosaba Mod Sync client UI");
			return;
		}
		MultiplayerSyncButton.register();
		ClientSyncController.initialize();
	}
}
