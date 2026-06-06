# RetroCenter — multiplayer hub on starac

This branch (`retrocenter`, forked from `ornithe-lwjgl3`) adds the multiplayer
hub feature to starac: double-click a server → the required mods sync over
OSL Networking → a per-server child Minecraft instance boots **in this same
JVM** and takes over the window → leaving the server hands the window
straight back to the hub.

## The flow — pre-login probe on the very first connection

Mismatched mods would never survive a world join (unknown packet ids,
unknown block ids in chunk data), so the sync happens BEFORE LoginHello,
on the same socket as the first connection attempt:

```
hub dials the server (any path: multiplayer screen, child autoconnect, ...)
 └─ server handshake reply arrives → ClientNetworkHandlerProbeMixin HOLDS
    the login and sends a sync QUERY on this same connection
    (carrier: RetroSyncPacket, vanilla packet table id 230 — the same
     mechanism OSL builds its own play-phase carrier on, usable pre-login)
     ├─ server kicks us ("Bad packet id") or 4s silence
     │   → server has NO retrocenter: cache that verdict for host:port,
     │     reconnect cleanly WITHOUT probing — vanilla servers stay vanilla
     ├─ manifest arrives, fabric-loader already reports every server mod
     │  at the exact version (client extras are fine)
     │   → release the held handshake: login proceeds ON THIS SOCKET
     │     (zero-overhead path — this is how child instances join;
     │      retroauth's join logic runs on the re-invocation)
     └─ manifest arrives, mods missing → ModSyncScreen over the HELD
        pre-login connection (server's login timeout frozen by
        ServerLoginHandlerSyncMixin; no world, no player entity):
         1. consent (mod list + sizes + RCE warning)
         2. tick-driven chunked download on the same socket
            (28 KB chunks, sha256-verified, cached in servers/.cache/)
         3. profile ready: servers/<host>_<port>/mods/ + stats/ ...
         4. drop the probe connection, boot child in-process, hand
            over the window
             └─ child autoconnects with the hub's session, probes,
                matches, logs straight in
             └─ child reaches TitleScreen/DisconnectedScreen → tears
                down; hub wakes, shows where you left off
```

OSL's role after this rework: the play-phase channels are a post-join
safety net (re-validate the manifest in-game, warn if the server's mods
changed since sync). The probe transport itself is OSL-free — so sync
works even when OSL is absent on either side; only the in-game re-check
is OSL-gated.

## Architecture

### One JVM, two Minecrafts

`InProcessChildLauncher` boots a fresh fabric-loader (`KnotClient.main`)
inside a `ChildClassLoader`. Everything is isolated (fresh statics, fresh
mixins, the child's own mod set from its profile dir) EXCEPT a short shared
list delegated parent-first:

- `org.lwjgl.*` — the windowing facade + LWJGL3. Mandatory (JNI natives bind
  to one classloader per JVM) and the whole point: shared `Display` statics
  mean handing the window over is a context switch, not IPC.
- `com.periut.starac.retrocenter.bridge.*` — `HubBridge` + `ChildConfig`,
  the coordination statics.
- logging + netty (stateless infra).

The child must NOT see starac's full artifact (its in-tree `org.lwjgl` would
be self-loaded by the child's Knot). It instead gets the **childshim jar**
(`./gradlew childShimJar` — starac minus `org/lwjgl/**` + `natives/**`) in
its mods folder; `ServerProfiles.seedInfraMods` puts it there along with
retroauth/OSL jars from the hub's mods dir.

### Window ownership (the "screen buffer switch")

All in the shared facade, OSL-free:

- `HubBridge` — instance id per thread tree (InheritableThreadLocal), owner
  token, park/wake (wait/notify), child config + exit reason.
- `Display.update()` — non-owner hub releases the GL context, parks (zero
  CPU), re-acquires on wake. One context, one window; only currency moves.
- `Display.create()` — child attach path: wait for hub to park, bind context.
- `Display.destroy()` — child detach path: release context, window survives.
- `Mouse`/`Keyboard` `poll()`/`next()` — non-owner sees no input.
- `MinecraftStopExitMixin` — b1.7.3's `Minecraft.stop()` calls
  `System.exit`; children skip it so the JVM (and the hub) survive.
- `TitleScreenChildMixin` / `DisconnectedScreenChildMixin` — a child landing
  on those screens shuts down (`scheduleStop`), reason carried to the hub.

### OSL gating (works without OSL)

OSL is a **suggests**, not a depends. The OSL entrypoints (`init` /
`client-init` / `server-init`) are only invoked by osl-entrypoints itself,
so when OSL is absent none of the OSL-importing classes
(`RetroCenterInit/ClientInit/ServerInit`, `sync/*`, `gui/ModSyncScreen`)
are ever classloaded — no NoClassDefFoundError, hub feature dormant,
everything else (windowing, de-AWT, Wayland fixes) untouched. The plain
fabric `main` entrypoint (`RetroCenterMain`) is OSL-free and just logs which
mode you're in.

### Auth sharing (retroauth)

retroauth keeps all auth state derived from the raw sessionId string
(`token:<accessToken>:<uuid>`), parsed in its `SessionMixin` whenever a
`Session` is constructed. So the child simply gets the hub's
`(username, sessionId)` via `ChildConfig` and `MinecraftAppletMixin` builds
`new Session(username, sessionId)` — retroauth in the child tree re-derives
the token and does its sessionserver join on connect. **Zero retroauth
changes needed.** (retroauth just needs to be in the child's mods —
`seedInfraMods` handles that.)

### What the child shares with the hub vs keeps per-server

- **Shared with the hub** (read AND written at the hub's game dir, one
  source of truth — `MinecraftChildDirsMixin` redirects the `GameOptions`
  and `TexturePacks` constructors): `options.txt` (video/sound settings,
  keybinds, selected texture pack) and the `texturepacks/` folder. Changing
  a setting on a server changes it everywhere, like one game.
- **Per-server** (the profile dir is the child's `--gameDir`): `mods/`
  (every mod the server offers gets installed there — the full manifest,
  not just the missing ones), `stats/` (achievements), `saves/`,
  `screenshots/`.

### Mod-set rule

The client checks fabric-loader's reported mods against the server manifest:
- every server mod present at the exact version → connect/play as-is,
- client extras → always fine,
- anything missing/mismatched → sync + child relaunch.

Servers exclude jars from the manifest via `config/retrocenter/no-sync.txt`
(one file name per line) — for server-only plugins/admin tools.

## Status (honest)

| Piece | State |
|---|---|
| Build (compile + remap + childshim) on biny-ornithe/gen2, loader 0.18.3, OSL 0.19.0-alpha.10 | ✅ builds |
| Pre-login probe transport (held handshake, carrier id 230, login-timeout freeze, no-mod verdict cache + clean vanilla reconnect) | implemented, needs live client↔server testing |
| Mod-set comparison gate (exact versions required, client extras fine) | implemented |
| Tick-driven chunked download + sha cache + consent UI over the held connection | implemented |
| OSL play-phase re-validation | implemented (OSL-gated) |
| Window handoff (park/attach/detach/input gates/exit suppression) | implemented, **untested at runtime** |
| In-process child boot (ChildClassLoader + KnotClient) | implemented, **experimental** — classpath topology needs dev-run debugging (expect iteration here) |
| Auth sharing via ChildConfig | implemented (depends on retroauth in child mods) |
| OSL-absent operation | probe + sync + handoff fully OSL-free; only in-game re-check is gated |

Known gaps / next steps:
1. **Runtime bring-up** of the child boot in a loom dev run (`runClient` +
   `runServer`): the java.class.path rewrite + classpath-mod duplication in
   dev is the most likely first failure point.
2. Loader bump note: dev now uses fabric-loader **0.18.3** (OSL requires
   ≥0.18); without OSL installed, older loaders still work.
3. Hub audio while parked (OpenAL device stays open; child AL init may
   conflict — paulscode may degrade to silent in one tree).
4. Carrier packet id 230 could collide with another mod's custom packet —
   configurable via `-Dretrocenter.packetId`, must match on both sides.
5. Trust UX is minimal (consent + sha pinning via cache); hash-pin per
   server with re-prompt on change is a small follow-up.
6. `childShimJar` is named-mapped (right for dev runs); production needs a
   remapped variant (RemapJarTask on the shim) shipped at
   `config/retrocenter/childshim.jar` or via `-Dretrocenter.childShimJar`.
