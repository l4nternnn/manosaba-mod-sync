package com.lantern.manosabamodsync.net;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.lantern.manosabamodsync.ManosabaModSync;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ClientStatusPayload(String statusJson) implements CustomPayload {
	private static final Gson GSON = new Gson();

	public static final Id<ClientStatusPayload> ID = new Id<>(Identifier.of(ManosabaModSync.MOD_ID, "client_status"));
	public static final PacketCodec<PacketByteBuf, ClientStatusPayload> CONFIG_CODEC = PacketCodec.tuple(
			PacketCodecs.STRING,
			ClientStatusPayload::statusJson,
			ClientStatusPayload::new
	);
	public static final PacketCodec<RegistryByteBuf, ClientStatusPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.STRING,
			ClientStatusPayload::statusJson,
			ClientStatusPayload::new
	);

	public static ClientStatusPayload of(ClientStatusReport report) {
		return new ClientStatusPayload(GSON.toJson(report));
	}

	public ClientStatusReport report() {
		try {
			return GSON.fromJson(statusJson, ClientStatusReport.class);
		} catch (JsonParseException e) {
			return new ClientStatusReport("SYNC_FAILED", 0, 0, "Unable to parse client status");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
