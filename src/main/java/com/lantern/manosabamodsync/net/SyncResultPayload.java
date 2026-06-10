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

public record SyncResultPayload(String resultJson) implements CustomPayload {
	private static final Gson GSON = new Gson();

	public static final Id<SyncResultPayload> ID = new Id<>(Identifier.of(ManosabaModSync.MOD_ID, "sync_result"));
	public static final PacketCodec<PacketByteBuf, SyncResultPayload> CONFIG_CODEC = PacketCodec.tuple(
			PacketCodecs.STRING,
			SyncResultPayload::resultJson,
			SyncResultPayload::new
	);
	public static final PacketCodec<RegistryByteBuf, SyncResultPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.STRING,
			SyncResultPayload::resultJson,
			SyncResultPayload::new
	);

	public static SyncResultPayload of(SyncResultReport report) {
		return new SyncResultPayload(GSON.toJson(report));
	}

	public SyncResultReport report() {
		try {
			return GSON.fromJson(resultJson, SyncResultReport.class);
		} catch (JsonParseException e) {
			return SyncResultReport.failed("Unable to parse sync result");
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
