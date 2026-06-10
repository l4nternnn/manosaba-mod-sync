package com.lantern.manosabamodsync.server;

import com.lantern.manosabamodsync.ManosabaModSync;
import com.lantern.manosabamodsync.config.ConfigManager;
import com.lantern.manosabamodsync.config.ServerSyncConfig;
import com.lantern.manosabamodsync.manifest.ManifestCodec;
import com.lantern.manosabamodsync.manifest.ManifestGenerator;
import com.lantern.manosabamodsync.manifest.SyncManifest;
import com.lantern.manosabamodsync.net.ClientStatusReport;
import com.lantern.manosabamodsync.net.ManifestPayload;
import com.lantern.manosabamodsync.net.SyncResultReport;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import net.minecraft.server.network.ServerPlayerConfigurationTask;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public final class ServerSyncManager {
	private static final ServerSyncManager INSTANCE = new ServerSyncManager();
	private static final ServerPlayerConfigurationTask.Key CONFIG_TASK_KEY =
			new ServerPlayerConfigurationTask.Key(ManosabaModSync.MOD_ID + ":sync_check");

	private ServerSyncConfig config = new ServerSyncConfig();
	private SyncManifest manifest = ManifestGenerator.generate(config);
	private String manifestJson = ManifestCodec.toJson(manifest);

	private ServerSyncManager() {
	}

	public static ServerSyncManager get() {
		return INSTANCE;
	}

	public void initialize() {
		config = ConfigManager.loadServerConfig();
		manifest = ManifestGenerator.generate(config);
		manifestJson = ManifestCodec.toJson(manifest);
		ManosabaModSync.LOGGER.info("Server manifest ready for pack {} {} with {} mod(s)",
				manifest.packId(), manifest.packVersion(), manifest.mods().size());
	}

	public void sendManifest(ServerPlayerEntity player, PacketSender sender) {
		if (!config.enabled) {
			return;
		}

		if (!ServerPlayNetworking.canSend(player, ManifestPayload.ID)) {
			String name = player.getName().getString();
			ManosabaModSync.LOGGER.warn("Player {} does not advertise Manosaba Mod Sync channel", name);
			if (config.requireClientSyncMod && config.disconnectOnMissingSyncMod) {
				sender.disconnect(Text.literal("This server requires Manosaba Mod Sync."));
			}
			return;
		}

		sender.sendPacket(new ManifestPayload(manifestJson));
		ManosabaModSync.LOGGER.info("Sent sync manifest with {} mod(s) to {}", manifest.mods().size(), player.getName().getString());
	}

	public void queueConfigurationSync(ServerConfigurationNetworkHandler handler) {
		if (!config.enabled) {
			return;
		}

		if (!ServerConfigurationNetworking.canSend(handler, ManifestPayload.ID)) {
			ManosabaModSync.LOGGER.warn("Configuring client does not advertise Manosaba Mod Sync channel");
			if (config.requireClientSyncMod && config.disconnectOnMissingSyncMod) {
				handler.disconnect(Text.literal("This server requires Manosaba Mod Sync."));
			}
			return;
		}

		((FabricServerConfigurationNetworkHandler) handler).addTask(new ManifestConfigurationTask(manifestJson));
		ManosabaModSync.LOGGER.info("Queued sync manifest configuration task with {} mod(s)", manifest.mods().size());
	}

	public void handleConfigurationStatus(ServerConfigurationNetworkHandler handler, ClientStatusReport report) {
		ManosabaModSync.LOGGER.info("Config sync status: {} (missing={}, mismatched={}) - {}",
				report.status(),
				report.missingCount(),
				report.mismatchedCount(),
				report.message());
		if ("OK".equals(report.status())) {
			((FabricServerConfigurationNetworkHandler) handler).completeTask(CONFIG_TASK_KEY);
		}
	}

	public void handleConfigurationResult(ServerConfigurationNetworkHandler handler, SyncResultReport report) {
		ManosabaModSync.LOGGER.info("Config sync result: {} - {}", report.status(), report.message());
		if ("RESTART_REQUIRED".equals(report.status())) {
			handler.disconnect(Text.literal("Mods synchronized. Please restart Minecraft and reconnect."));
		} else if ("SYNC_FAILED".equals(report.status())) {
			handler.disconnect(Text.literal("Mod synchronization failed: " + report.message()));
		}
	}

	public void handleStatus(ServerPlayerEntity player, ClientStatusReport report) {
		ManosabaModSync.LOGGER.info("Player {} sync status: {} (missing={}, mismatched={}) - {}",
				player.getName().getString(),
				report.status(),
				report.missingCount(),
				report.mismatchedCount(),
				report.message());
	}

	public void handleResult(ServerPlayerEntity player, SyncResultReport report) {
		ManosabaModSync.LOGGER.info("Player {} sync result: {} - {}",
				player.getName().getString(),
				report.status(),
				report.message());
	}

	private record ManifestConfigurationTask(String manifestJson) implements ServerPlayerConfigurationTask {
		@Override
		public void sendPacket(Consumer<Packet<?>> sender) {
			sender.accept(ServerConfigurationNetworking.createS2CPacket(new ManifestPayload(manifestJson)));
		}

		@Override
		public Key getKey() {
			return CONFIG_TASK_KEY;
		}
	}
}
