package com.periut.starac.retrocenter.sync;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.List;

import com.periut.starac.retrocenter.RetroCenter;

import net.minecraft.client.Minecraft;
import net.ornithemc.osl.networking.api.client.ClientPlayNetworking;

/**
 * OSL play-phase validation, client side (OSL-gated — only classloaded via
 * the client-init entrypoint). The heavy lifting happens pre-login over the
 * probe; this is the in-game safety net: after joining, re-check the
 * server's manifest and log loudly if the server's mods changed under us.
 */
public final class OslSyncPlayClient {

	private OslSyncPlayClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerListener(SyncChannels.MANIFEST, (context, buffer) -> {
			byte[] data = buffer.readByteArray();
			ModManifest manifest = ModManifest.read(new DataInputStream(new ByteArrayInputStream(data)));
			List<ModManifest.Entry> missing = ModSyncClient.missingMods(manifest);
			if (missing.isEmpty()) {
				RetroCenter.log("play-phase manifest check OK (" + manifest.entries.size() + " server mods)");
			} else {
				RetroCenter.log("WARNING: server mods changed since sync — " + missing.size()
						+ " mods missing/mismatched; reconnect to re-sync");
			}
		});
	}

	public static void onPlayReady(Minecraft minecraft) {
		if (!ClientPlayNetworking.isPlayReady(SyncChannels.HELLO)) {
			return; // server has no retrocenter+OSL — nothing to validate
		}
		try {
			ClientPlayNetworking.send(SyncChannels.HELLO,
					buf -> buf.writeVarInt(RetroCenter.SYNC_PROTOCOL_VERSION));
		} catch (Throwable t) {
			RetroCenter.log("failed to send play-phase hello: " + t);
		}
	}
}
