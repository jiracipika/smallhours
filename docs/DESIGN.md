# Small Hours — Character Bible & Systems Design

> A 1.21.1 Fabric + NeoForge survival-horror mod.
> Target feeling: **the world knows you're alone, and something disagrees.**

Design language: vanilla's own dark layer (Warden / Deep Dark) — restraint, sound, darkness,
patience. Quiet dread over jump scares. Every entity obeys strict, learnable rules. Every entity
has counterplay. Nothing screams without buildup.

**The one connective mechanic:** a hidden per-player **DREAD** meter (0–100) that rises with
encounters and darkness, sinks with safety, and gates escalation across the whole cast.
The player is never shown a number — they learn it through consequence.

---

## The cast

| Tier | Name | Role | Fear |
|---|---|---|---|
| 1 | **The Omen** | Apparition that only watches | Being seen |
| 2 | **The Understudy** | Sound-mimic that lures you off position | Being deceived |
| 3 | **The Patient** | Apex stalker; watching only slows it | Being hunted |

All three use the vanilla humanoid rig — silhouette, scale, and skin carry the design.
They never spawn in your face; a spawn-director places them plausibly and per-player.

---

## The Omen — *It saw you first.*

**Appearance:** ~2.0 blocks tall, thin (HumanoidModel at 1.12 scale). Near-black desaturated
figure; a blank pale face with two black eye pits and no mouth. No walk cycle — it is *already
there* when you look. The head tracks you perfectly.

**Behavior state machine:**
- **SELECT** — the director samples up to 8 spots on a 30–60 block ring during eligible windows
  (night surface, dark caves, tier-gated; 1 sighting/night at dread 0, up to 3 at tier 3, +1
  marked; never on a world's first night). A spot needs: block light < 7, solid ground, air, a
  clear line of sight to you, and not dead-center in your view (periphery is plausible).
- **WATCHING** — stands perfectly still, head tracking. Max dwell 25s.
- **TRIGGERED** — stared at (crosshair-on-entity, cumulative 2.5s within 60s window) · player
  within 15 blocks · light at its block jumps ≥ 4 (a flared torch) · dwell expiry.
- **VANISHING** — surfaces: one quiet wood-step, gone (step-back). Caves: silent, gone.
  Freeze-then-gone is the default: hard despawn the first instant it is unobserved.
- **GONE** — sighting budget decremented. No particles. Ever.

**Audio:** one custom cue — `omen_sting` (1.5s low swell), once per sighting, first-of-night.
Nothing else, ever.

**Dread:** +5 per registered sighting (+4 close-trigger), capped +12/night.

**Counterplay:** *look at it to send it away; light to banish it faster.* Staring costs 2.5s of
exposure. Approaching is punished. A tamed cat within 12 blocks pushes its spawn distance out.
Ignoring it works — it's gone within 25s.

**Never does:** never attacks, never approaches, never takes damage, never interacts with blocks.
It is scenery that looks back. The moment it does anything else it becomes a mob — and mobs
aren't uncanny.

---

## The Understudy — *It never says them the way you did, because it says them exactly the same every time.*

**Appearance:** 0.9 blocks tall (HumanoidModel at 0.55 scale). Sallow child in a faded tunic.
Eyes 1px wider apart than any player face; mouth a straight line 1px too wide. Arms swing
*fully synchronized*, like a wind-up toy. Idle: motionless crouch at the torch-line's edge.

**Behavior state machine:**
- **DORMANT** — spawned in darkness (light ≤ 3), 24–40 blocks out, outside your view cone.
  Silent, motionless.
- **LURE** — every 7–10s: plays a sound you want to trust — chest close, door open, eating,
  a hurt grunt — **twice, 1.2–1.8s apart, both at pitch exactly 1.0**. Between plays it
  repositions 6–10 blocks, always landing outside your ±55° view cone, always in darkness.
  Max 3 cycles per encounter. Audio is **private** — only you hear it.
- **CAUGHT** — you find it first: it freezes 2.5s, arms over its face, chittering at 20% volume.
  Then: pounce if you're within 5 blocks; flee if you're not.
- **POUNCE** — once per encounter. Leap ≤ 3.5 blocks, 3 damage, shriek, big dread spike. → FLEE.
- **FLEE** — 1.35× your sprint for 4s toward darkness; if hit: shriek, same flee. In light ≥ 9
  it cannot pounce or reposition — it VANISHES after 10s.
- **VANISH** — despawns; chitter at 30% volume from its last position; 60s cooldown before it
  can re-lure you; max 2 encounters per player-night.

**THE TELL (the centerpiece):** *it is a recording.* Minecraft randomizes sound pitch
(~0.8–1.2) on every play; you have thousands of hours of subconscious exposure to that
variation. The Understudy plays everything at **exactly 1.0, always twice** — byte-identical
repeats. Learnable in 2–3 encounters. The flaw is the horror: it learned the sound, not the
variation. It has been *rehearsing*.

**Second-order tells** (consequences of two hard rules — *never enters light ≥ 9; never enters
your view cone*): the sound source never resolves (no chest, no door); chased sounds always
replay from where you're NOT looking; in multiplayer **its audio is private** — "did you hear
that?" "No." = fake.

**Mimicry (v0.2):** a per-player ring buffer records the sounds you personally triggered in
the last five minutes — blocks you broke or placed, doors you opened, chests you closed. When
it lures you it now prefers *that* repertoire: the chest YOU closed, the door YOU opened,
replayed at pitch 1.0 from a place where nothing is. You know that sound. You made it.
(config: `mimicry`; falls back to the generic repertoire when your buffer is empty.)

**Dread:** pounce +8, evaded +5, caught +3, failed lure +2, kill **−12** + 3-night suppression.
One clean encounter ≈ +8; a pounce night ≈ +21; killing caps the bleed.

**Counterplay:** run the double-play test; burn its 3-cycle budget by chasing sounds; hold a
torch perimeter and watch its *edge*; back against a wall and spin to deny reposition lanes
until the view-cone rule forces it into your gaze. Panic (charging the sound blind) walks you
into the pounce. Preparation wins.

---

## The Patient — *You can stop it. You cannot stop it.*

**Appearance:** 2.1+ blocks (HumanoidModel at 1.17 scale). A desaturated grey photo-negative of
a player — like a photograph of someone, drained. Eyes closed. For one second it reads as "a
player." Then it reads as wrong. When observed, the only motion is its head, tracking your exact
position, mid-stride, statue-still.

**The inversion (the design's spine):** the SCP-173 freeze is a lie. Observation does not stop
The Patient — it only slows it from a **7.2 b/s hunt** to a **0.8 b/s nightmare creep**. A
stare-contest is a countdown measured in blocks. You can stop it. You cannot stop it.

**Behavior state machine:**
- Spawned (never in view — ring-sampled outside your ~55° cone, light ≤ 4, dread ≥ 60, night;
  1 per player-night, 2 if marked) only when you're worth hunting.
- **HUNT (unobserved)** — closes at 7.2 b/s with slow, heavy, ground-matched footsteps.
  Silence means it's watching back.
- **CREEP (observed)** — 0.8 b/s, head tracking, near-silent. It is *still closing.*
- **STRIKE** — within 2.5 blocks: the first hit of an engagement **steals your held item** (it
  now carries it — you can see your sword in its hand) for 2 damage; later hits 8 damage,
  1.5s cooldown. It screams once, as the hunt's starting gun. Not after. Not before.
- **BANISH** — dawn freezes it solid; it despawns with a distant shriek.

**Escalation:** at dread ≥ 85 (Marked) it spawns earlier, creeps at 1.2 b/s, hunts at 8.0,
remembers you 50% longer, and keeps stolen items across nights.

**Why not just kill it:** 100 HP + regenerates 1 HP/2s while unobserved — long fights undo
themselves. The only loot is *your own items back*. And the killer becomes marked: guaranteed
Phase-3 behavior next night. Kill to recover gear now and pay in escalation; avoid it and it
escalates slowly anyway. Both are real choices.

**When it kills:** your stolen item is still in its hand. The dread floor is set at pre-death
−10. The meter remembers.

**Dread:** sighting +4 · proximity (+1/10s within 24 blocks) · hunt start +8 · steal +8 · hit
+6 · kills you +25 · you kill it +12 (and it marks you).

**Counterplay (the learnable rules):**
1. **Silence is the alarm.** Footsteps stop → it's centered and creeping.
2. The **100° cone** is "observed." Peripheral vision doesn't count — it drifts at your screen edge.
3. Break line of sight and *wait*, then don't return the way you came.
4. Two players trading stares walk it backward — it creeps the whole time.
5. Water slows it to a crawl. Boats buy time, not safety.
6. Dawn is absolute.

---

## The Dread economy (the spine)

Per-player, 0–100, server-authoritative, persisted across deaths and sessions.
Thresholds: **25 Watched · 50 Hunted · 75 Stalked · 100 MARKED.**

**Sources (per-night caps in parens):**

| Source | Delta |
|---|---|
| Omen sighting (+4 close) | +5 (12) |
| Understudy pounce / evaded / caught / failed lure | +8 / +5 / +3 / +2 (20) |
| Patient proximity · hit · steal · hunt-start | +1/10s (6) · +6 · +8 · +8 (20) |
| Night darkness (sky-exposed, light ≤ 3) | +1/60s (5) |
| Underground (dread < 50) | +1/90s (4) |
| Patient kills you | +25 |

**Sinks:**

| Sink | Delta |
|---|---|
| Sleep a full night | −10 |
| Well-lit roofed room (light ≥ 9) | −1/30s (−6) |
| Tamed cat within 12 blocks | −1/60s (−8) |
| Direct daylight | −1/45s (−8) |
| Death (any cause) | dread → min(dread, 20); unmarks |
| Passive decay (after 60s quiet) | −1/2min, floor 5 once maxEver ≥ 25 |

**The floor:** once the world has noticed you (maxEver ≥ 25), dread never fully returns to 0.
It remembers.

**MARKED (100):** the Omen gets +1 sighting budget and a 24-block "invitation" sighting; the
Understudy prefers you and pounces sooner; the Patient counts you as a valid hunt target, wins
ties, and escalates to Phase 3. In-fiction tells: your cat hisses at you. You are never shown
the number. Unmark: die (the cheap, ugly out) — or the cat-and-bed ritual: wake up beside a
tamed cat within 8 blocks and the mark lifts (same 85-scar the death-out leaves, −10 dread,
and a purr only you hear) (v0.2; config: `cat_ritual`).

**Multiplayer:** meters are strictly per-UUID. Entities target the highest-dread player in
range (ties → nearest). Witness rule: seeing something together earns the witness half credit —
"did you see that?" "see what?"

---

## The Ambient Director

A cheap server-tick system. Per-player, per-tier, with night caps, 90s minimum between any two
events, 120s mute after real encounters, and nothing during combat:

| Event | Tier | What it does |
|---|---|---|
| Fake footsteps | 25+ | 4–6 steps behind you, matched to your ground, **stop when you stop** |
| Distant wood knocks | 0+ | 2–3 knocks, 24–40 blocks, random azimuth |
| Door creak | 50+ | an *actual placed door* within 24 blocks, at night, when you're roofed |
| Whispers | 75+ | 2–3 blocks directly behind your head |
| Heartbeat | threat-driven | warden heartbeat while something is hunting you |

And a spawn-director that places all three entities plausibly: view-cone rejection, light
gates, LOS checks, per-night budgets — everything tunable in one `Tuning.java`.

---

## v0.1 implementation status

**Shipped:** all three entities with their full core loops; the dread economy (sources, sinks,
caps, decay, floors, marked); the ambient director (footsteps, knocks, whispers, heartbeat);
the spawn director; private multiplayer audio; spawn eggs; custom skins + 5 synthesized sounds;
both loaders build from one shared source tree.

**v0.2 (shipped):** `config/smallhours.json` (master switch, per-entity spawn gates,
first-night skip, Patient dread floor, mimicry + ritual toggles); Understudy mimicry — its
lures now prefer sounds the target player actually triggered in the last 5 minutes; the
cat-and-bed unmark ritual (wake beside a tamed cat → mark lifted). Also: neoforge.mods.toml
version is now templated from gradle.properties, and the NeoForge dev server moved to port
25699 so both dev servers can run side by side.

**v0.3 (shipped):** the door-creak ambient event (50+: you're roofed, it's night, and a
*real placed door* within 24 blocks creaks open at full vanilla volume — nothing opens);
the Patient's kills now use a custom damage type so the death line reads **"X stopped
running."** (in multiplayer: "X stopped running. Y was still there."); and the hidden
challenge advancement **"It Saw You First"** — granted the first time the Omen vanishes
with you as its witness.

**v0.4 (shipped):** the Patient's full escalation ladder. **LURK** — on arrival it is
silent: no scream, no footsteps, no threat heartbeat, just something circling 24–36 blocks
out, head tracking you. It commits when its patience runs out (5–10s), at **midnight**, or
the moment you stray within 14 blocks — and *that* is when the scream fires (the hunt's
starting gun moved here from acquisition). **Midnight ramp** — from tick 18000 the creep
and hunt speeds both step up (hunt 1.5→1.75; marked 1.8→2.0). **Closing step** — while
unobserved and 12+ blocks out it may take one ground-true, light-shy (≤4) step of 6–10
blocks that never lands inside anyone's view cone; a single heavy footstep is the only
evidence. **Eyes open** — when its current prey is marked, its eyes are open (2×3 pale
whites, pinprick pupils, `patient_eyes_open.png`); it's synced entity data, so everyone
sees the eyes — only the marked player knows why.

**v0.5 (shipped — the character bible is now fully implemented):** **memory across
nights** — the Patient that's still holding your things at dawn stashes them in your dread
state if you're marked, and the next night's Patient arrives wearing your sword (kill it to
take them back; die unmarked and the memory dies too). **Witness rule** — when the Omen
vanishes with a witness, every other eligible player who could see it earns half the
sighting's dread: "did you see that?" "see what?". **Config** — the Patient phases are now
server-owner knobs too (`patient_lurk`, `patient_midnight_ramp`, `patient_closing_steps`,
`patient_eyes`, plus `witness_rule`).

**Roadmap:** the design doc is done. Candidates beyond it: the Understudy replaying
*chat* (needs a soft-voice TTS or very careful text-to-sound design), Modrinth/CurseForge
release packaging, and a Mayview modpack slot.
