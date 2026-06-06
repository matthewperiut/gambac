package com.periut.starac.retrocenter;

import com.periut.starac.mixin.retrocenter.PacketRegistryInvoker;
import com.periut.starac.retrocenter.net.RetroSyncPacket;

import net.fabricmc.api.ModInitializer;

/**
 * Plain fabric-loader entrypoint — runs whether or not OSL is installed.
 *
 * Registers the OSL-free pre-login carrier packet (the probe transport —
 * the same packet-table mechanism OSL builds its own carrier on). The OSL
 * play-phase validation lives behind the OSL entrypoints (RetroCenterInit
 * & friends) which OSL itself invokes, so when osl-networking is absent
 * those classes never load and only the in-game re-check is lost — the
 * probe, sync and child handoff all still work.
 */
public final class RetroCenterMain implements ModInitializer {

	@Override
	public void onInitialize() {
		RetroCenter.captureLaunchArguments();

		// Pre-login sync carrier, both directions (id configurable via
		// -Dretrocenter.packetId; default 230).
		PacketRegistryInvoker.retrocenter$register(RetroSyncPacket.ID, true, true, RetroSyncPacket.class);
		RetroCenter.log("sync carrier packet registered at id " + RetroSyncPacket.ID);

		if (RetroCenter.isSyncAvailable()) {
			RetroCenter.log("OSL networking present — play-phase manifest validation enabled");
		} else {
			RetroCenter.log("OSL networking not installed — play-phase validation off; probe sync still works");
		}
		if (RetroCenter.isChildInstance()) {
			RetroCenter.log("running as child instance");
		}
	}
}
