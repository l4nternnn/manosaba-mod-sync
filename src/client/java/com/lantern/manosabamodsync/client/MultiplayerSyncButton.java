package com.lantern.manosabamodsync.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.concurrent.atomic.AtomicBoolean;

public final class MultiplayerSyncButton {
	private static final AtomicBoolean REGISTERED = new AtomicBoolean();

	private MultiplayerSyncButton() {
	}

	public static void register() {
		if (!REGISTERED.compareAndSet(false, true)) {
			return;
		}
		ScreenEvents.AFTER_INIT.register(MultiplayerSyncButton::afterInit);
	}

	private static void afterInit(MinecraftClient client, Screen screen, int scaledWidth, int scaledHeight) {
		if (!(screen instanceof MultiplayerScreen)) {
			return;
		}

		ButtonWidget button = ButtonWidget.builder(Text.literal("\u68c0\u67e5\u66f4\u65b0"),
						ignored -> ClientSyncController.openManualCheckInfo(screen))
				.dimensions(scaledWidth / 2 + 158, scaledHeight - 28, 74, 20)
				.build();
		Screens.getButtons(screen).add(button);
	}
}
