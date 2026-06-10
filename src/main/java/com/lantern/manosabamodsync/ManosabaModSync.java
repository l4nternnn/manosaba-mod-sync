package com.lantern.manosabamodsync;

import com.lantern.manosabamodsync.net.SyncNetworking;
import com.lantern.manosabamodsync.server.ServerSyncManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManosabaModSync implements ModInitializer {
	public static final String MOD_ID = "manosaba_mod_sync";
	public static final String MOD_VERSION = "1.0.2";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SyncNetworking.registerPayloadTypes();
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
			SyncNetworking.registerServerReceivers();
			ServerSyncManager.get().initialize();
		}
		LOGGER.info("Manosaba Mod Sync initialized");
	}
}
