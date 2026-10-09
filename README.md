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
  from a sprint to a creep. It lurks first — circling in silence — then screams once and comes.
  After midnight it stops stalling. Its first hit takes the item out of your hand, and if
  you're marked, its eyes are open.

## Building

```bash
./gradlew :fabric:build :neoforge:build
# -> fabric/build/libs/smallhours-fabric-1.21.1-0.4.0.jar
# -> neoforge/build/libs/smallhours-neoforge-1.21.1-0.4.0.jar
```

Requires JDK 21+ (a registered toolchain or `org.gradle.java.installations.paths` pointing at
one — ModDevGradle needs a real JDK 21 for its artifact pipeline; compilation itself uses
`--release 21`).

## Dev servers

```bash
./gradlew :fabric:runServer     # or :neoforge:runServer
```

Run dirs are pre-configured (flat world, eula accepted; Fabric on port 25599, NeoForge on
25699 with RCON on 25590 — the two cannot share a port, run one at a time or keep these).
Spawn any of the three via their spawn eggs (creative → Spawn Eggs tab) and wait for night.
Note: entity selectors only see loaded chunks — `forceload add` first when testing by RCON.

## Configuration

Both loaders read `config/smallhours.json` (written with defaults on first boot; restart to
apply):

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | master switch — `false` = the mod does nothing on its own |
| `omen` / `understudy` / `patient` | `true` | per-entity natural-spawn gates (eggs always work) |
| `skip_first_night` | `true` | spawn-director skips the world's first night |
| `patient_min_dread` | `60` | dread floor below which the Patient never hunts you |
| `mimicry` | `true` | the Understudy lures with sounds *you* triggered recently |
| `cat_ritual` | `true` | waking beside a tamed cat lifts the mark |

Fine-grained numbers (speeds, ranges, budgets, all deltas) stay in
`common/.../Tuning.java`.

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
