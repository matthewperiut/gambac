package com.periut.starac.retrocenter;

import com.periut.starac.retrocenter.sync.OslSyncPlayServer;

import net.ornithemc.osl.entrypoints.api.server.ServerModInitializer;

/** OSL "server-init" entrypoint (OSL-gated, see RetroCenterInit). */
public final class RetroCenterServerInit implements ServerModInitializer {

	@Override
	public void initServer() {
		OslSyncPlayServer.register();
	}
}
