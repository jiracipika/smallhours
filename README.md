# Small Hours

A Minecraft **1.21.1** survival-horror mod for **Fabric** and **NeoForge**.

> The world knows you're alone. Something disagrees.

Quiet dread, learnable rules, and one hidden mechanic: a per-player **Dread** meter that
remembers what you've seen and escalates the world accordingly.

**The cast** (full design in [docs/DESIGN.md](docs/DESIGN.md)):

- **The Omen** — an apparition on the treeline that only watches. Stare at it and it leaves.
  Feed it dread.
- **The Understudy** — a child-sized mimic that lures you with sounds you want to trust.
  Its tell: it plays every sound at pitch 1.0, always twice. It's a recording.
- **The Patient** — the stalker that freeze-mechanics lied about: watching it only slows it
  from a sprint to a creep. Its first hit takes the item out of your hand.

## Building

```bash
./gradlew :fabric:build :neoforge:build
# -> fabric/build/libs/smallhours-fabric-1.21.1-0.1.0.jar
# -> neoforge/build/libs/smallhours-neoforge-1.21.1-0.1.0.jar
```

Requires JDK 21+ (a registered toolchain or `org.gradle.java.installations.paths` pointing at
one — ModDevGradle needs a real JDK 21 for its artifact pipeline; compilation itself uses
`--release 21`).

## Dev servers

```bash
./gradlew :fabric:runServer     # or :neoforge:runServer
```

Run dirs are pre-configured (flat world, port 25599, eula accepted). Spawn any of the three
via their spawn eggs (creative → Spawn Eggs tab) and wait for night.

## Layout

```
common/          # shared source (Mojmap on both loaders): entities, dread system,
                 # spawn/ambient director, renderers
fabric/          # Fabric entrypoints + registrations (Loom, fabric-loom-remap)
neoforge/        # NeoForge entrypoints + registrations (ModDevGradle 2)
tools/           # gen_assets.py — regenerates all textures (PIL) + sounds (ffmpeg)
docs/DESIGN.md   # the character bible: state machines, dread economy, counterplay
```

No Mixins. All tuning lives in one file: `common/.../Tuning.java`.

## Design rules the code honors

1. Nothing spawns in your face — the spawn director view-cone-rejects every position.
2. Every entity has counterplay written into its ruleset (see DESIGN.md).
3. The dread meter is never shown; players learn it through consequence.
4. Multiplayer-safe: per-player meters, per-player private audio, highest-dread targeting.

## License

MIT
