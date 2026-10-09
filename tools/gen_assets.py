#!/usr/bin/env python3
"""
Small Hours (modid `smallhours`) asset generator.

Reproduces the entire resource set with one command:

    python3 tools/gen_assets.py

  - three 64x64 player-skin-layout entity textures (PIL)
  - 128x128 mod icon + pack.png (PIL)
  - five lo-fi .ogg sound effects (ffmpeg lavfi graphs)
  - sounds.json, lang/en_us.json, pack.mcmeta

Only touches common/src/main/resources/** . No Java, no gradle.

NOTE on audio encoding: this machine's ffmpeg 8.1 build has no libvorbis;
the native `vorbis` encoder is experimental and stereo-only. We therefore
render a mono signal graph and duplicate it to both channels (dual-mono
stereo) with `pan=stereo|c0=c0|c1=c0`. Content is mono; container is
stereo ogg/vorbis, which Minecraft plays fine.
"""

import json
import os
import random
import subprocess
import sys

from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(REPO, "common", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "smallhours")
TEX_DIR = os.path.join(ASSETS, "textures", "entity")
SND_DIR = os.path.join(ASSETS, "sounds")
LANG_DIR = os.path.join(ASSETS, "lang")
FFMPEG = os.environ.get("FFMPEG", "/usr/local/bin/ffmpeg")
FFPROBE = os.environ.get("FFPROBE", "/usr/local/bin/ffprobe")


def clamp(v):
    return 0 if v < 0 else (255 if v > 255 else v)


# --------------------------------------------------------------------------
# Skin painting helpers
# --------------------------------------------------------------------------

# Classic 64x64 player-skin layout, base (inner) layer. Each part is a box
# of faces: top, bottom, right, front, left, back (x0, y0, x1, y1).
HEAD = {
    "top":    (8, 0, 16, 8),
    "bottom": (16, 0, 24, 8),
    "right":  (0, 8, 8, 16),
    "front":  (8, 8, 16, 16),
    "left":   (16, 8, 24, 16),
    "back":   (24, 8, 32, 16),
}
BODY = {
    "top":    (20, 16, 28, 20),
    "bottom": (28, 16, 36, 20),
    "right":  (16, 20, 20, 32),
    "front":  (20, 20, 28, 32),
    "left":   (28, 20, 32, 32),
    "back":   (32, 20, 40, 32),
}
RARM = {
    "top":    (44, 16, 48, 20),
    "bottom": (48, 16, 52, 20),
    "right":  (40, 20, 44, 32),
    "front":  (44, 20, 48, 32),
    "left":   (48, 20, 52, 32),
    "back":   (52, 20, 56, 32),
}
LARM = {
    "top":    (36, 48, 40, 52),
    "bottom": (40, 48, 44, 52),
    "right":  (32, 52, 36, 64),
    "front":  (36, 52, 40, 64),
    "left":   (40, 52, 44, 64),
    "back":   (44, 52, 48, 64),
}
RLEG = {
    "top":    (4, 16, 8, 20),
    "bottom": (8, 16, 12, 20),
    "right":  (0, 20, 4, 32),
    "front":  (4, 20, 8, 32),
    "left":   (8, 20, 12, 32),
    "back":   (12, 20, 16, 32),
}
LLEG = {
    "top":    (20, 48, 24, 52),
    "bottom": (24, 48, 28, 52),
    "right":  (16, 52, 20, 64),
    "front":  (20, 52, 24, 64),
    "left":   (24, 52, 28, 64),
    "back":   (28, 52, 32, 64),
}
PARTS = [HEAD, BODY, RARM, LARM, RLEG, LLEG]

# Per-face brightness so the boxes read as 3D and not as flat plastic.
FACE_SHADE = {
    "top": 1.06, "bottom": 0.78, "front": 1.00,
    "back": 0.88, "right": 0.94, "left": 0.90,
}

# Second-layer (overlay) regions are left fully transparent: vanilla skips
# alpha-0 overlay pixels, which is the standard for player skins.
OVERLAY_BOXES = [
    (32, 0, 64, 16),    # hat
    (0, 32, 16, 48),    # right leg overlay
    (16, 32, 40, 48),   # body overlay
    (40, 32, 56, 48),   # right arm overlay
    (0, 48, 16, 64),    # left leg overlay
    (48, 48, 64, 64),   # left arm overlay
]


def noise_fill(img, box, color, rng, amp=6, shade=1.0, vfade=None):
    """Fill a rect with `color` + per-pixel uniform noise (one noise value
    per pixel, applied to all channels, so desaturated colors stay
    desaturated). `vfade` = (top_scale, bottom_scale) multiplies `shade`
    linearly down the rect."""
    x0, y0, x1, y1 = box
    px = img.load()
    r0 = clamp(int(color[0] * shade))
    g0 = clamp(int(color[1] * shade))
    b0 = clamp(int(color[2] * shade))
    h = max(1, y1 - y0)
    for y in range(y0, y1):
        t = (y - y0) / (h - 1) if h > 1 else 0.0
        if vfade:
            s = vfade[0] + (vfade[1] - vfade[0]) * t
        else:
            s = 1.0
        r = clamp(int(r0 * s))
        g = clamp(int(g0 * s))
        b = clamp(int(b0 * s))
        for x in range(x0, x1):
            n = rng.randint(-amp, amp)
            px[x, y] = (clamp(r + n), clamp(g + n), clamp(b + n), 255)


def fill_part(img, part, color, rng, amp=6, shade=1.0, vfade=None,
              skip=()):
    """Paint every face of a part box with noise_fill."""
    for face, box in part.items():
        if face in skip:
            continue
        noise_fill(img, box, color, rng, amp=amp,
                   shade=shade * FACE_SHADE[face], vfade=vfade)


def paint_rect(img, box, color):
    """Hard-paint a rect (no noise) - for eyes, mouths, precise features."""
    x0, y0, x1, y1 = box
    px = img.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = (color[0], color[1], color[2], 255)


def new_skin():
    """Fully transparent 64x64 RGBA canvas."""
    return Image.new("RGBA", (64, 64), (0, 0, 0, 0))


def fill_unused(img, color, rng):
    """Paint the one pocket of the 64x64 grid that belongs to no box
    (56..64 x 16..32) so the base layer is opaque everywhere."""
    noise_fill(img, (56, 16, 64, 32), color, rng, amp=6)


# --------------------------------------------------------------------------
# The three creatures
# --------------------------------------------------------------------------

def build_omen():
    """The Omen: color-drained near-black silhouette. #101018 base, robe a
    touch lighter on the torso, blank pale face, two pure-black eye pits,
    no mouth, slightly pale hands."""
    rng = random.Random(20261001)
    img = new_skin()
    base = (0x10, 0x10, 0x18)        # near-black desaturated indigo
    robe = (0x1A, 0x1A, 0x28)        # slightly lighter robe shade
    hair = (0x0D, 0x0D, 0x15)
    pale = (0xC9, 0xC4, 0xB4)        # blank face patch
    hand = (0xA6, 0xA1, 0x92)        # slightly pale hands

    fill_unused(img, base, rng)
    fill_part(img, HEAD, hair, rng, amp=5, skip=("front", "bottom"))
    noise_fill(img, HEAD["bottom"], (0x09, 0x09, 0x10), rng, amp=4)
    # torso robe with a gentle darken toward the hem
    fill_part(img, BODY, robe, rng, amp=6, vfade=(1.02, 0.72))
    fill_part(img, RARM, base, rng, amp=6, skip=("bottom",))
    fill_part(img, LARM, base, rng, amp=6, skip=("bottom",))
    noise_fill(img, RARM["bottom"], hand, rng, amp=5)
    noise_fill(img, LARM["bottom"], hand, rng, amp=5)
    fill_part(img, RLEG, (0x0E, 0x0E, 0x16), rng, amp=5)
    fill_part(img, LLEG, (0x0E, 0x0E, 0x16), rng, amp=5)

    # Face: pale blank patch, two 2x2 pure-black pits, no mouth.
    noise_fill(img, HEAD["front"], pale, rng, amp=5)
    paint_rect(img, (9, 11, 11, 13), (0, 0, 0))
    paint_rect(img, (13, 11, 15, 13), (0, 0, 0))
    return img


def build_understudy():
    """The Understudy: sallow child. Skin #CFC5A5, faded tunic #8A7F66,
    dark mop of hair #241E16; eyes 1px too far apart, mouth 1px too wide,
    faint darker eye sockets."""
    rng = random.Random(20261002)
    img = new_skin()
    skin = (0xCF, 0xC5, 0xA5)
    skin_dark = (0xC2, 0xB8, 0x98)
    tunic = (0x8A, 0x7F, 0x66)
    hair = (0x24, 0x1E, 0x16)
    eye = (0x1A, 0x12, 0x0A)
    socket = (0xBD, 0xB2, 0x90)
    mouth = (0x3A, 0x2E, 0x24)

    fill_unused(img, skin, rng)
    # skin everywhere first (arms, legs, head sides)
    fill_part(img, HEAD, hair, rng, amp=5, skip=("front", "bottom"))
    noise_fill(img, HEAD["bottom"], skin_dark, rng, amp=5)
    fill_part(img, BODY, tunic, rng, amp=6, vfade=(1.0, 0.88))
    # short sleeves: top 4 rows of each arm in tunic, rest bare skin
    for arm in (RARM, LARM):
        for face, (x0, y0, x1, y1) in arm.items():
            if face == "bottom":
                noise_fill(img, (x0, y0, x1, y1), skin_dark, rng, amp=5)
                continue
            sh = FACE_SHADE[face]
            noise_fill(img, (x0, y0, x1, y0 + 4), tunic, rng, amp=6, shade=sh)
            noise_fill(img, (x0, y0 + 4, x1, y1), skin, rng, amp=6, shade=sh)
    fill_part(img, RLEG, skin, rng, amp=6)
    fill_part(img, LLEG, skin, rng, amp=6)
    noise_fill(img, RLEG["bottom"], skin_dark, rng, amp=5)
    noise_fill(img, LLEG["bottom"], skin_dark, rng, amp=5)

    # Face. Grid: x 8..15, y 8..15. Eyes 2px wide, one pixel wider apart
    # than a player face (player: 9-10 / 13-14; ours: 9-10 / 14-15).
    noise_fill(img, HEAD["front"], skin, rng, amp=5)
    # jagged fringe of the mop
    paint_rect(img, (8, 8, 16, 9), hair)
    for x in (9, 11, 14):
        paint_rect(img, (x, 9, x + 1, 10), hair)
    # faint sockets (1px rims above/below each eye, subtle), then eyes
    paint_rect(img, (9, 10, 11, 11), socket)
    paint_rect(img, (9, 13, 11, 14), socket)
    paint_rect(img, (14, 10, 16, 11), socket)
    paint_rect(img, (14, 13, 16, 14), socket)
    paint_rect(img, (9, 11, 11, 13), eye)
    paint_rect(img, (14, 11, 16, 13), eye)
    # tiny straight mouth, 4px wide (one too many)
    paint_rect(img, (10, 14, 14, 15), mouth)
    return img


def build_patient(eyes_open=False):
    """The Patient: desaturated grey photo-negative. Shirt #D8D4CC, pants
    #4A4845, skin #C7C2BA; two 1px closed-eye lines, no open eyes.
    eyes_open=True is the marked-prey variant: 2x3 pale eye whites, pinprick
    pupils. Same drained palette - the eyes are the only thing awake."""
    rng = random.Random(20261003)
    img = new_skin()
    skin = (0xC7, 0xC2, 0xBA)
    shirt = (0xD8, 0xD4, 0xCC)
    pants = (0x4A, 0x48, 0x45)
    hair = (0xB6, 0xB0, 0xA7)
    closed = (0x8A, 0x84, 0x78)
    open_eye = (0xE8, 0xE4, 0xDC)

    fill_unused(img, skin, rng)
    fill_part(img, HEAD, hair, rng, amp=4, skip=("front", "bottom"))
    noise_fill(img, HEAD["bottom"], skin, rng, amp=4)
    fill_part(img, BODY, shirt, rng, amp=4)
    fill_part(img, RARM, skin, rng, amp=4)
    fill_part(img, LARM, skin, rng, amp=4)
    noise_fill(img, RARM["bottom"], (0xBF, 0xB9, 0xB0), rng, amp=4)
    noise_fill(img, LARM["bottom"], (0xBF, 0xB9, 0xB0), rng, amp=4)
    fill_part(img, RLEG, pants, rng, amp=4)
    fill_part(img, LLEG, pants, rng, amp=4)
    noise_fill(img, RLEG["bottom"], (0x35, 0x33, 0x2F), rng, amp=3)
    noise_fill(img, LLEG["bottom"], (0x35, 0x33, 0x2F), rng, amp=3)

    # Face: still, colorless, eyes CLOSED - two 1px horizontal lines.
    noise_fill(img, HEAD["front"], skin, rng, amp=4)
    if eyes_open:
        paint_rect(img, (9, 11, 11, 14), open_eye)
        paint_rect(img, (13, 11, 15, 14), open_eye)
        paint_rect(img, (10, 13, 11, 14), closed)
        paint_rect(img, (14, 13, 15, 14), closed)
    else:
        paint_rect(img, (9, 12, 11, 13), closed)
        paint_rect(img, (13, 12, 15, 13), closed)
    return img


# --------------------------------------------------------------------------
# Icon / pack.png art
# --------------------------------------------------------------------------

def build_icon(seed=20261007):
    """128x128: dark indigo night-sky gradient, thin pale crescent, black
    ridgeline, tiny pale humanoid on the ridge. Drawn at 512 and minified
    for smooth edges, then grain on top."""
    rng = random.Random(seed)
    S = 512
    img = Image.new("RGB", (S, S))
    px = img.load()
    top = (8, 8, 26)
    horizon = (36, 32, 62)
    hy = int(S * 0.70)
    for y in range(S):
        t = min(1.0, y / hy)
        r = int(top[0] + (horizon[0] - top[0]) * t)
        g = int(top[1] + (horizon[1] - top[1]) * t)
        b = int(top[2] + (horizon[2] - top[2]) * t)
        for x in range(S):
            n = rng.randint(-3, 3)
            px[x, y] = (clamp(r + n), clamp(g + n), clamp(b + n))

    # --- crescent moon: pale circle minus offset circle ---
    mx, my, mr = int(S * 0.72), int(S * 0.22), int(S * 0.115)
    cx, cy, cr = mx + int(S * 0.048), my - int(S * 0.022), int(S * 0.105)
    moon = Image.new("L", (S, S), 0)
    md = ImageDraw.Draw(moon)
    md.ellipse((mx - mr, my - mr, mx + mr, my + mr), fill=255)
    md.ellipse((cx - cr, cy - cr, cx + cr, cy + cr), fill=0)
    img.paste((0xE9, 0xE5, 0xD2), (0, 0), moon)

    # --- ridgeline: black silhouette polygon with a flat perch ---
    ridge_pts = [
        (0.00, 0.715), (0.12, 0.665), (0.22, 0.700), (0.33, 0.645),
        (0.44, 0.690), (0.52, 0.640), (0.60, 0.640), (0.68, 0.695),
        (0.78, 0.655), (0.88, 0.700), (1.00, 0.670),
    ]
    poly = [(int(x * S), int(y * S)) for x, y in ridge_pts]
    poly += [(S, S), (0, S)]
    ridge = Image.new("L", (S, S), 0)
    rd = ImageDraw.Draw(ridge)
    rd.polygon(poly, fill=255)
    img.paste((0x07, 0x07, 0x0C), (0, 0), ridge)

    # --- tiny pale humanoid standing on the flat perch (x 0.52..0.60) ---
    fig = Image.new("L", (S, S), 0)
    fd = ImageDraw.Draw(fig)
    fx = int(S * 0.558)
    ground = int(0.640 * S)
    scale = S / 128.0  # design in 128px units
    head_r = int(2.6 * scale)
    head_cy = ground - int(11.2 * scale)
    # head
    fd.ellipse((fx - head_r, head_cy - head_r,
                fx + head_r, head_cy + head_r), fill=255)
    # torso
    tw = int(2.2 * scale)
    fd.rectangle((fx - tw, head_cy + head_r - 1,
                  fx + tw, ground - int(4.4 * scale)), fill=255)
    # arms (thin, at sides)
    fd.rectangle((fx - tw - int(1.2 * scale), head_cy + head_r,
                  fx - tw, ground - int(5.6 * scale)), fill=255)
    fd.rectangle((fx + tw, head_cy + head_r,
                  fx + tw + int(1.2 * scale), ground - int(5.6 * scale)),
                 fill=255)
    # legs
    fd.rectangle((fx - tw, ground - int(4.6 * scale),
                  fx - int(0.4 * scale), ground), fill=255)
    fd.rectangle((fx + int(0.4 * scale), ground - int(4.6 * scale),
                  fx + tw, ground), fill=255)
    img.paste((0xC9, 0xC4, 0xB4), (0, 0), fig)

    out = img.resize((128, 128), Image.LANCZOS)
    # grain
    opx = out.load()
    for y in range(128):
        for x in range(128):
            r, g, b = opx[x, y][:3]
            n = rng.randint(-4, 4)
            opx[x, y] = (clamp(r + n), clamp(g + n), clamp(b + n))
    return out


# --------------------------------------------------------------------------
# Sounds (ffmpeg)
# --------------------------------------------------------------------------

VORBIS_ARGS = ["-c:a", "vorbis", "-strict", "-2", "-q:a", "4"]
DUAL_MONO = "pan=stereo|c0=c0|c1=c0"


def render_sound(name, duration, chains, out_path):
    """chains: list of lavfi source strings; fc_body builds the mono
    graph; we append dual-mono duplication and encode vorbis."""
    cmd = [FFMPEG, "-y", "-v", "error"]
    for src in chains:
        cmd += ["-f", "lavfi", "-i", src]
    return cmd


def synth(name, inputs, fc_body, out_path):
    """inputs: list of lavfi source strings; fc_body: filter_complex that
    consumes [0:a]..[n:a] and produces [mono]. We append dual-mono pan."""
    fc = fc_body + f";[mono]{DUAL_MONO}[out]"
    cmd = [FFMPEG, "-y", "-v", "error"]
    for src in inputs:
        cmd += ["-f", "lavfi", "-i", src]
    cmd += [
        "-filter_complex", fc, "-map", "[out]",
        "-ar", "44100", "-ac", "2",
    ] + VORBIS_ARGS + [out_path]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"ffmpeg failed for {name}:\n{proc.stderr}")
    print(f"  wrote {os.path.relpath(out_path, REPO)}")


def build_sounds():
    os.makedirs(SND_DIR, exist_ok=True)

    # 1) omen_sting ~1.5s: 48Hz sine swell + faint brown noise; fade in
    #    0.4s, fade out starting 0.9s; dead clean after 1.5s.
    synth(
        "omen_sting",
        ["sine=frequency=48:sample_rate=44100:duration=1.5",
         "anoisesrc=color=brown:sample_rate=44100:amplitude=0.55:duration=1.5"],
        "[0:a]volume=0.95[s];"
        "[1:a]volume=0.16,lowpass=f=200[n];"
        "[s][n]amix=inputs=2:duration=first:normalize=0,"
        "lowpass=f=320,"
        "afade=t=in:st=0:d=0.4,"
        "afade=t=out:st=0.9:d=0.6,"
        "volume=0.9[mono]",
        os.path.join(SND_DIR, "omen_sting.ogg"),
    )

    # 2) understudy_shriek ~0.8s: white noise bandpassed 1200-2800Hz,
    #    fast 14Hz tremolo, sharp single burst.
    synth(
        "understudy_shriek",
        ["anoisesrc=color=white:sample_rate=44100:amplitude=0.85:duration=0.8"],
        "[0:a]highpass=f=1200,lowpass=f=2800,"
        "tremolo=f=14:d=0.9,"
        "afade=t=in:st=0:d=0.015,"
        "afade=t=out:st=0.45:d=0.35,"
        "volume=0.72[mono]",
        os.path.join(SND_DIR, "understudy_shriek.ogg"),
    )

    # 3) understudy_chitter ~1.2s: stuttering clicks - noise 2k-5k,
    #    24Hz tremolo, medium fades.
    synth(
        "understudy_chitter",
        ["anoisesrc=color=white:sample_rate=44100:amplitude=0.7:duration=1.2"],
        "[0:a]highpass=f=2000,lowpass=f=5000,"
        "tremolo=f=24:d=0.85,"
        "afade=t=in:st=0:d=0.05,"
        "afade=t=out:st=0.7:d=0.5,"
        "volume=0.62[mono]",
        os.path.join(SND_DIR, "understudy_chitter.ogg"),
    )

    # 4) patient_scream ~1.5s: brown+white mix bandpassed 300-900Hz, slow
    #    6Hz tremolo, lowpassed, abrupt start, long fade out.
    synth(
        "patient_scream",
        ["anoisesrc=color=brown:sample_rate=44100:amplitude=0.7:duration=1.5",
         "anoisesrc=color=white:sample_rate=44100:amplitude=0.5:duration=1.5"],
        "[0:a]volume=0.55[b];"
        "[1:a]volume=0.28[w];"
        "[b][w]amix=inputs=2:duration=first:normalize=0,"
        "highpass=f=300,lowpass=f=900,"
        "tremolo=f=6:d=0.8,"
        "lowpass=f=1200,"
        "afade=t=out:st=0.6:d=0.9,"
        "volume=0.85[mono]",
        os.path.join(SND_DIR, "patient_scream.ogg"),
    )

    # 5) whisper ~2.5s: breathy bandpassed noise (300-1400Hz), very slow
    #    ~3Hz undulation, quiet.
    synth(
        "whisper",
        ["anoisesrc=color=white:sample_rate=44100:amplitude=0.6:duration=2.5"],
        "[0:a]highpass=f=300,lowpass=f=1400,"
        "tremolo=f=3:d=0.7,"
        "afade=t=in:st=0:d=0.3,"
        "afade=t=out:st=1.9:d=0.6,"
        "volume=0.45[mono]",
        os.path.join(SND_DIR, "whisper.ogg"),
    )


def verify_sounds():
    """ffprobe every ogg: vorbis in ogg container, right duration; check
    omen_sting tail is actually silent."""
    expectations = {
        "omen_sting": 1.5,
        "understudy_shriek": 0.8,
        "understudy_chitter": 1.2,
        "patient_scream": 1.5,
        "whisper": 2.5,
    }
    ok = True
    for name, want in expectations.items():
        path = os.path.join(SND_DIR, f"{name}.ogg")
        probe = subprocess.run(
            [FFPROBE, "-v", "error", "-show_entries",
             "stream=codec_name,sample_rate,channels",
             "-show_entries", "format=format_name,duration",
             "-of", "json", path],
            capture_output=True, text=True, check=True)
        data = json.loads(probe.stdout)
        stream = data["streams"][0]
        fmt = data["format"]
        dur = float(fmt["duration"])
        good = (stream["codec_name"] == "vorbis"
                and "ogg" in fmt["format_name"]
                and int(stream["sample_rate"]) == 44100
                and abs(dur - want) <= 0.15)
        print(f"  {name}.ogg: {stream['codec_name']}/{fmt['format_name']} "
              f"{stream['sample_rate']}Hz ch={stream['channels']} "
              f"dur={dur:.2f}s (target {want}) -> "
              f"{'OK' if good else 'MISMATCH'}")
        ok = ok and good
    # tail silence check for omen_sting
    tail = subprocess.run(
        [FFMPEG, "-v", "info", "-i", os.path.join(SND_DIR, "omen_sting.ogg"),
         "-af", "atrim=start=1.35,volumedetect", "-f", "null", "-"],
        capture_output=True, text=True)
    for line in tail.stderr.splitlines():
        if "mean_volume" in line:
            print("  omen_sting tail (1.35s+):", line.split("] ")[-1])
    return ok


# --------------------------------------------------------------------------
# Metadata files
# --------------------------------------------------------------------------

SOUNDS_JSON = {
    "omen_sting": {"subtitle": "subtitles.smallhours.omen_sting",
                   "sounds": ["smallhours:omen_sting"]},
    "understudy_shriek": {"subtitle": "subtitles.smallhours.understudy_shriek",
                          "sounds": ["smallhours:understudy_shriek"]},
    "understudy_chitter": {"subtitle": "subtitles.smallhours.understudy_chitter",
                           "sounds": ["smallhours:understudy_chitter"]},
    "patient_scream": {"subtitle": "subtitles.smallhours.patient_scream",
                       "sounds": ["smallhours:patient_scream"]},
    "whisper": {"subtitle": "subtitles.smallhours.whisper",
                "sounds": ["smallhours:whisper"]},
}

LANG_JSON = {
    "entity.smallhours.omen": "The Omen",
    "entity.smallhours.understudy": "The Understudy",
    "entity.smallhours.patient": "The Patient",
    "item.smallhours.omen_spawn_egg": "Omen Spawn Egg",
    "item.smallhours.understudy_spawn_egg": "Understudy Spawn Egg",
    "item.smallhours.patient_spawn_egg": "Patient Spawn Egg",
    "subtitles.smallhours.omen_sting": "Something sees you",
    "subtitles.smallhours.understudy_shriek": "Understudy pounces",
    "subtitles.smallhours.understudy_chitter": "Understudy chitters",
    "subtitles.smallhours.patient_scream": "The Patient screams",
    "subtitles.smallhours.whisper": "Whispers",
    "death.attack.smallhours.stopped_running": "%1$s stopped running.",
    "death.attack.smallhours.stopped_running.player": "%1$s stopped running. %2$s was still there.",
    "advancement.smallhours.it_saw_you_first.title": "It Saw You First",
    "advancement.smallhours.it_saw_you_first.desc": "Something on the treeline noticed you before you noticed it.",
}

PACK_MCMETA = {"pack": {"description": "Small Hours resources",
                        "pack_format": 34}}


def write_json(path, obj, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(json.dumps(obj, indent=indent, ensure_ascii=False) + "\n")
    # self-check
    with open(path, "r", encoding="utf-8") as f:
        json.load(f)
    print(f"  wrote {os.path.relpath(path, REPO)}")


# --------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------

def main():
    argv = set(sys.argv[1:])
    os.makedirs(TEX_DIR, exist_ok=True)

    print("textures:")
    skins = {
        "omen": build_omen(),
        "understudy": build_understudy(),
        "patient": build_patient(),
        "patient_eyes_open": build_patient(eyes_open=True),
    }
    for name, img in skins.items():
        path = os.path.join(TEX_DIR, f"{name}.png")
        img.save(path)
        print(f"  wrote {os.path.relpath(path, REPO)} "
              f"({img.size[0]}x{img.size[1]})")

    icon = build_icon()
    icon.save(os.path.join(ASSETS, "icon.png"))
    icon.save(os.path.join(RES, "pack.png"))
    print("  wrote assets/smallhours/icon.png (128x128)")
    print("  wrote pack.png (128x128)")

    print("metadata:")
    write_json(os.path.join(ASSETS, "sounds.json"), SOUNDS_JSON)
    write_json(os.path.join(LANG_DIR, "en_us.json"), LANG_JSON)
    write_json(os.path.join(RES, "pack.mcmeta"), PACK_MCMETA, indent=None)

    if "--skip-sounds" not in argv:
        print("sounds:")
        build_sounds()
        verify_sounds()
    print("done.")


if __name__ == "__main__":
    main()
