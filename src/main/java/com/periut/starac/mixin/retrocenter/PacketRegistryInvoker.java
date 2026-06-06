package com.periut.starac.mixin.retrocenter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.network.packet.Packet;

/**
 * Exposes b1.7.3's package-private packet-table registration — the same
 * trick OSL's networking impl uses for its own carrier packet.
 */
@Mixin(Packet.class)
public interface PacketRegistryInvoker {

	@Invoker("register")
	static void retrocenter$register(int id, boolean s2c, boolean c2s, Class<? extends Packet> clazz) {
		throw new AssertionError();
	}
}
