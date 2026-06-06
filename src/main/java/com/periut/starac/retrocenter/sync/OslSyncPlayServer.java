package com.periut.starac.retrocenter.sync;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import com.periut.starac.retrocenter.RetroCenter;

import net.ornithemc.osl.networking.api.server.ServerPlayNetworking;

/**
 * OSL play-phase manifest service, server side (OSL-gated — only
 * classloaded via the server-init entrypoint). Answers in-game hellos with
 * the same manifest the pre-login probe serves.
 */
public final class OslSyncPlayServer {

	private OslSyncPlayServer() {
	}

	public static void register() {
		ServerPlayNetworking.registerListener(SyncChannels.HELLO, (context, buffer) -> {
			int protocol = buffer.readVarInt();
			if (protocol != RetroCenter.SYNC_PROTOCOL_VERSION) {
				return;
			}
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ManifestStore.get().write(new DataOutputStream(bytes));
			ServerPlayNetworking.send(context.player(), SyncChannels.MANIFEST,
					buf -> buf.writeByteArray(bytes.toByteArray()));
		});
		RetroCenter.log("play-phase manifest channel registered");
	}
}
