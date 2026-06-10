package com.lantern.manosabamodsync.client;

import com.google.gson.JsonParseException;
import com.lantern.manosabamodsync.ManosabaModSync;
import com.lantern.manosabamodsync.client.screen.ManualCheckInfoScreen;
import com.lantern.manosabamodsync.client.screen.SyncFinishedScreen;
import com.lantern.manosabamodsync.client.screen.SyncProgressScreen;
import com.lantern.manosabamodsync.client.screen.SyncRequiredScreen;
import com.lantern.manosabamodsync.config.ClientSyncConfig;
import com.lantern.manosabamodsync.config.ConfigManager;
import com.lantern.manosabamodsync.manifest.ManifestCodec;
import com.lantern.manosabamodsync.manifest.SyncManifest;
import com.lantern.manosabamodsync.net.ClientStatusPayload;
import com.lantern.manosabamodsync.net.ClientStatusReport;
import com.lantern.manosabamodsync.net.ManifestPayload;
import com.lantern.manosabamodsync.net.SyncResultPayload;
import com.lantern.manosabamodsync.net.SyncResultReport;
import com.lantern.manosabamodsync.sync.LocalModInfo;
import com.lantern.manosabamodsync.sync.SyncPlan;
import com.lantern.manosabamodsync.sync.SyncPlanner;
import icyllis.modernui.mc.MuiModApi;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.packet.CustomPayload;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ClientSyncController {
	private static ClientSyncConfig config = new ClientSyncConfig();

	private ClientSyncController() {
	}

	public static void initialize() {
		config = ConfigManager.loadClientConfig();
		config.autoCheckOnJoin = false;
		if (config.autoApplyStagedOnStartup) {
			StartupApplyManager.applyStagedOnStartup(config);
		}
		registerReceivers();
		ManosabaModSync.LOGGER.info("Manosaba Mod Sync client initialized");
	}

	public static ClientSyncConfig config() {
		return config;
	}

	private static void registerReceivers() {
		ClientConfigurationNetworking.registerGlobalReceiver(ManifestPayload.ID, (payload, context) ->
				handleManifest(payload.manifestJson()));
		ClientPlayNetworking.registerGlobalReceiver(ManifestPayload.ID, (payload, context) ->
				handleManifest(payload.manifestJson()));
	}

	private static void handleManifest(String manifestJson) {
		if (!config.enabled || !config.autoCheckOnJoin) {
			return;
		}

		CompletableFuture.supplyAsync(() -> createPlan(manifestJson))
				.whenComplete((context, throwable) -> {
					if (throwable != null) {
						ManosabaModSync.LOGGER.error("Failed to process sync manifest", throwable);
						sendResult(SyncResultReport.failed("Manifest processing failed"));
						openFinished("无法解析服务器同步清单。", true);
						return;
					}

					SyncPlan plan = context.plan();
					if (plan.isComplete()) {
						sendStatus(ClientStatusReport.ok());
						ManosabaModSync.LOGGER.info("Client mods match server manifest");
						return;
					}

					sendStatus(ClientStatusReport.syncRequired(plan.missingMods().size(), plan.mismatchedMods().size()));
					openRequired(context.manifest(), plan);
				});
	}

	private static PlanningContext createPlan(String manifestJson) {
		try {
			SyncManifest manifest = ManifestCodec.fromJson(manifestJson);
			List<LocalModInfo> localMods = ClientModScanner.scanDefaultModsDirectory();
			SyncPlan plan = SyncPlanner.plan(manifest, localMods);
			ManosabaModSync.LOGGER.info("Sync plan: missing={}, mismatched={}, valid={}, extra={}",
					plan.missingMods().size(),
					plan.mismatchedMods().size(),
					plan.validMods().size(),
					plan.extraMods().size());
			return new PlanningContext(manifest, plan);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("Invalid manifest JSON", e);
		}
	}

	public static void openRequired(SyncManifest manifest, SyncPlan plan) {
		MinecraftClient.getInstance().execute(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			Screen previous = client.currentScreen;
			var screen = MuiModApi.get().createScreen(
					new SyncRequiredScreen(manifest, plan),
					null,
					previous,
					"Manosaba Mod Sync"
			);
			client.setScreen(screen);
		});
	}

	public static void openProgress(SyncManifest manifest, SyncPlan plan) {
		MinecraftClient.getInstance().execute(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			var screen = MuiModApi.get().createScreen(
					new SyncProgressScreen(manifest, plan),
					null,
					client.currentScreen,
					"Manosaba Mod Sync"
			);
			client.setScreen(screen);
		});
	}

	public static void openFinished(String message, boolean failed) {
		MinecraftClient.getInstance().execute(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			var screen = MuiModApi.get().createScreen(
					new SyncFinishedScreen(message, failed),
					null,
					client.currentScreen,
					"Manosaba Mod Sync"
			);
			client.setScreen(screen);
		});
	}

	public static void openManualCheckInfo(Screen previous) {
		MinecraftClient.getInstance().execute(() -> {
			MinecraftClient client = MinecraftClient.getInstance();
			var screen = MuiModApi.get().createScreen(
					new ManualCheckInfoScreen(previous),
					null,
					previous,
					"Manosaba Mod Sync"
			);
			client.setScreen(screen);
		});
	}

	public static void sendStatus(ClientStatusReport report) {
		if (sendConfigurationPacket(ClientStatusPayload.of(report))) {
			return;
		}
		try {
			if (ClientPlayNetworking.canSend(ClientStatusPayload.ID)) {
				ClientPlayNetworking.send(ClientStatusPayload.of(report));
			}
		} catch (IllegalStateException e) {
			ManosabaModSync.LOGGER.debug("Cannot send client sync status outside play connection", e);
		}
	}

	public static void sendResult(SyncResultReport report) {
		if (sendConfigurationPacket(SyncResultPayload.of(report))) {
			return;
		}
		try {
			if (ClientPlayNetworking.canSend(SyncResultPayload.ID)) {
				ClientPlayNetworking.send(SyncResultPayload.of(report));
			}
		} catch (IllegalStateException e) {
			ManosabaModSync.LOGGER.debug("Cannot send sync result outside play connection", e);
		}
	}

	private static boolean sendConfigurationPacket(CustomPayload payload) {
		try {
			if (ClientConfigurationNetworking.canSend(payload.getId())) {
				ClientConfigurationNetworking.send(payload);
				return true;
			}
		} catch (IllegalStateException e) {
			ManosabaModSync.LOGGER.debug("Cannot send sync packet outside configuration connection", e);
		}
		return false;
	}

	private record PlanningContext(SyncManifest manifest, SyncPlan plan) {
	}
}
