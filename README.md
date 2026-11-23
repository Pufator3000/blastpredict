# Blast Predict (Fabric 1.21.11, client-side)

Makes a server-side 3x3x2 "Blast" feel instant: when your main block finishes breaking, the other 17 blocks of the
formation disappear locally right away instead of waiting for the server's block updates.

The server stays in charge. The mod sends no packets, creates no drops and never keeps a block the server says is
there. It only changes what your screen shows until the server answers.

## Commands (client-only, never sent to the server)

| Command | What it does |
|---|---|
| `/bp` | Toggle prediction on/off (`/bp on`, `/bp off`, `/bp status`) |
| `/fo` | Fixed orientation: formation follows the face of the block you mine (extends into the wall/floor/ceiling) |
| `/po` | Player orientation: formation follows where you look (nearest of N/S/E/W/up/down) |
| `/bp match` | Toggle "only blocks of the same type as the mined block" (default: on) |

Settings are saved in `config/blastpredict.properties`. The server's own `/blast` is unaffected: it still decides
whether real Blast mining happens.

## Build

You need Java 21.

**Option A, GitHub (no local setup):** create a repo, upload everything in this folder, open the *Actions* tab, run
*Build mod*, then download the `blastpredict-jar` artifact.

**Option B, local:** install Gradle 9.2.1 (or open the folder in IntelliJ IDEA) and run `gradle build`.
The jar is `build/libs/blastpredict-1.0.0.jar` (not the `-sources` one).

Install it in `.minecraft/mods` together with Fabric Loader 0.18.1+ and Fabric API for 1.21.11.

If Gradle cannot resolve a version, update `gradle.properties` with the values from https://fabricmc.net/develop

## How reconciliation works

- Local break finished -> remember the original state of each predicted block, set it to air locally.
- Any server block update or chunk-delta entry for a position (both go through `ClientWorld#handleBlockUpdate`)
  removes our guess for it; the server's state is applied normally, so there is no overwrite of newer data.
- Not confirmed within 1.5 s + 3x your ping (max 5 s): restore the block, but only if it is still our predicted air.
- Joining/leaving a server or changing dimension drops all guesses without touching the world.

## Testing checklist

1. `/bp status` shows ON, then mine with server `/blast` active.
2. If extra blocks vanish and then pop back after about 2 s, the server's formation differs from the selected mode: try the other one (`/fo` vs `/po`).
3. If blocks of other types disappear on the server but not on your screen, run `/bp match` to predict any breakable block.
4. Check with high ping (or a VPN) that nothing flickers.

## Limits

- Every player who wants the smooth effect needs the mod. A vanilla client cannot predict.
- The mod cannot know the Skript's exact rules. It guesses the 3x3x2 shape from the selected mode and undoes wrong guesses.
- Written against Yarn 1.21.11+build.4; the three hooked methods are `ClientPlayerInteractionManager#breakBlock(BlockPos)`
  and `ClientWorld#handleBlockUpdate(BlockPos, BlockState, int)`.
