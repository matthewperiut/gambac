package com.periut.starac.retrocenter;

import com.periut.starac.retrocenter.sync.SyncChannels;

import net.ornithemc.osl.entrypoints.api.ModInitializer;
import net.ornithemc.osl.networking.api.ChannelRegistry;

/**
 * OSL "init" entrypoint — invoked by osl-entrypoints on both sides, only
 * when OSL is installed (the OSL gate: absent OSL, this class never loads).
 */
public final class RetroCenterInit implements ModInitializer {

	@Override
	public void init() {
		ChannelRegistry.register(SyncChannels.HELLO);
		ChannelRegistry.register(SyncChannels.MANIFEST);
		ChannelRegistry.register(SyncChannels.FILE_REQ);
		ChannelRegistry.register(SyncChannels.FILE_CHUNK);
		RetroCenter.log("sync channels registered");
	}
}
