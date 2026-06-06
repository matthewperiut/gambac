package com.periut.starac.retrocenter.sync;

import net.ornithemc.osl.core.api.util.NamespacedIdentifier;
import net.ornithemc.osl.networking.api.ChannelIdentifiers;

/**
 * OSL channel ids for the mod-sync protocol. Loading this class requires
 * osl-networking — only ever referenced from the OSL entrypoints and the
 * sync pipeline (see the gating rule in RetroCenter).
 *
 * Protocol (rides the live play connection; beta carrier packet caps each
 * message at 32767 bytes, hence CHUNK_SIZE):
 *
 *   C→S hello     { varint protocolVersion }
 *   S→C manifest  { ModManifest }
 *   C→S file_req  { string sha256, varint chunkIndex }
 *   S→C file_chunk{ string sha256, varint chunkIndex, varint chunkCount, byteArray data }
 */
public final class SyncChannels {

	public static final NamespacedIdentifier HELLO = ChannelIdentifiers.from("retrocenter", "hello");
	public static final NamespacedIdentifier MANIFEST = ChannelIdentifiers.from("retrocenter", "manifest");
	public static final NamespacedIdentifier FILE_REQ = ChannelIdentifiers.from("retrocenter", "file_req");
	public static final NamespacedIdentifier FILE_CHUNK = ChannelIdentifiers.from("retrocenter", "file_chunk");

	/** Keep well under the 32767-byte beta custom-payload limit. */
	public static final int CHUNK_SIZE = 28_000;

	private SyncChannels() {
	}
}
