package com.lantern.manosabamodsync.net;

import com.lantern.manosabamodsync.ManosabaModSync;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ManifestPayload(String manifestJson) implements CustomPayload {
	public static final Id<ManifestPayload> ID = new Id<>(Identifier.of(ManosabaModSync.MOD_ID, "manifest"));
	public static final PacketCodec<PacketByteBuf, ManifestPayload> CONFIG_CODEC = PacketCodec.tuple(
			PacketCodecs.string(1024 * 1024),
			ManifestPayload::manifestJson,
			ManifestPayload::new
	);
	public static final PacketCodec<RegistryByteBuf, ManifestPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.string(1024 * 1024),
			ManifestPayload::manifestJson,
			ManifestPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
