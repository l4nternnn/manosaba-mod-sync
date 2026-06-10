package com.lantern.manosabamodsync.net;

import com.lantern.manosabamodsync.ManosabaModSync;
import com.lantern.manosabamodsync.server.ServerSyncManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class SyncNetworking {
	private static final int MAX_MANIFEST_PACKET_SIZE = 1024 * 1024;
	private static boolean registered;

	private SyncNetworking() {
	}

	public static void registerPayloadTypes() {
		if (registered) {
			return;
		}
		PayloadTypeRegistry.configurationS2C().registerLarge(ManifestPayload.ID, ManifestPayload.CONFIG_CODEC, MAX_MANIFEST_PACKET_SIZE);
		PayloadTypeRegistry.configurationC2S().register(ClientStatusPayload.ID, ClientStatusPayload.CONFIG_CODEC);
		PayloadTypeRegistry.configurationC2S().register(SyncResultPayload.ID, SyncResultPayload.CONFIG_CODEC);
		PayloadTypeRegistry.playS2C().registerLarge(ManifestPayload.ID, ManifestPayload.CODEC, MAX_MANIFEST_PACKET_SIZE);
		PayloadTypeRegistry.playC2S().register(ClientStatusPayload.ID, ClientStatusPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SyncResultPayload.ID, SyncResultPayload.CODEC);
		registered = true;
	}

	public static void registerServerReceivers() {
		ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((handler, server) ->
				ServerSyncManager.get().queueConfigurationSync(handler));
		ServerConfigurationNetworking.registerGlobalReceiver(ClientStatusPayload.ID, (payload, context) -> {
			ServerSyncManager.get().handleConfigurationStatus(context.networkHandler(), payload.report());
		});
		ServerConfigurationNetworking.registerGlobalReceiver(SyncResultPayload.ID, (payload, context) -> {
			ServerSyncManager.get().handleConfigurationResult(context.networkHandler(), payload.report());
		});
		ServerPlayNetworking.registerGlobalReceiver(ClientStatusPayload.ID, (payload, context) -> {
			ServerSyncManager.get().handleStatus(context.player(), payload.report());
		});
		ServerPlayNetworking.registerGlobalReceiver(SyncResultPayload.ID, (payload, context) -> {
			ServerSyncManager.get().handleResult(context.player(), payload.report());
		});
		ManosabaModSync.LOGGER.info("Registered Manosaba Mod Sync server networking");
	}
}
