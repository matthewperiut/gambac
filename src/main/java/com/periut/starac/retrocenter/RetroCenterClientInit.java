package com.periut.starac.retrocenter;

import com.periut.starac.retrocenter.sync.OslSyncPlayClient;

import net.ornithemc.osl.entrypoints.api.client.ClientModInitializer;
import net.ornithemc.osl.networking.api.client.ClientConnectionEvents;

/** OSL "client-init" entrypoint (OSL-gated, see RetroCenterInit). */
public final class RetroCenterClientInit implements ClientModInitializer {

	@Override
	public void initClient() {
		OslSyncPlayClient.register();
		ClientConnectionEvents.PLAY_READY.register(OslSyncPlayClient::onPlayReady);
	}
}
