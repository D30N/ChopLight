#!/usr/bin/env python3
"""Render ChopLight app screenshots with PIL (2x scale), matching the real UI."""
from PIL import Image, ImageDraw, ImageFont
import os

S = 2
W, H = 360 * S, 740 * S
BG = (18, 18, 18)          # #121212
CARD = (30, 30, 30)        # #1E1E1E
RED = (179, 38, 30)        # #B3261E
RED_LT = (255, 218, 214)   # #FFDAD6
NAVBG = (26, 26, 26)       # #1A1A1A
WHITE = (255, 255, 255)
GREY = (176, 176, 176)     # #B0B0B0
GREY2 = (136, 136, 136)    # #888888
TORCH_OFF = (42, 42, 42)   # #2A2A2A
TORCH_OFF_B = (58, 58, 58) # #3A3A3A
TRACK = (58, 58, 58)
BLUE = (77, 163, 255)      # #4DA3FF
SHOT_DIR = os.path.expanduser("~/workspace/choplight/screenshots")
FONT_PATH = os.path.expanduser("~/.fonts/NotoSansMalayalam.ttf")


def font(size, bold=False):
    f = ImageFont.truetype(FONT_PATH, size * S, layout_engine=ImageFont.Layout.RAQM)
    if bold:
        f.stroke_width = max(1, size * S // 28)
    return f


def rr(d, box, r, fill, outline=None, width=1):
    b = [v * S for v in box]
    d.rounded_rectangle(b, radius=r * S, fill=fill, outline=outline, width=width * S)


def txt(d, xy, s, f, fill, anchor="la"):
    d.text((xy[0] * S, xy[1] * S), s, font=f, fill=fill, anchor=anchor)


def base():
    im = Image.new("RGB", (W, H), BG)
    return im, ImageDraw.Draw(im)


def icon_torch(d, cx, cy, r, col):
    """Simple flashlight: body + head + light rays."""
    s = S
    # body
    d.rounded_rectangle([(cx - r * 0.35) * s, (cy - r * 0.55) * s,
                         (cx + r * 0.35) * s, (cy + r * 0.55) * s],
                        radius=r * 0.18 * s, fill=col)
    # head (wider)
    d.rounded_rectangle([(cx - r * 0.55) * s, (cy - r * 0.95) * s,
                         (cx + r * 0.55) * s, (cy - r * 0.45) * s],
                        radius=r * 0.15 * s, fill=col)
    # rays
    for a in (-35, -15, 5, 25):
        import math
        x1 = cx + math.cos(math.radians(a - 90)) * r * 1.05
        y1 = cy + math.sin(math.radians(a - 90)) * r * 1.05
        x2 = cx + math.cos(math.radians(a - 90)) * r * 1.45
        y2 = cy + math.sin(math.radians(a - 90)) * r * 1.45
        d.line([x1 * s, y1 * s, x2 * s, y2 * s], fill=col, width=int(r * 0.16 * s))


def icon_home(d, cx, cy, col):
    s = S
    d.polygon([(cx - 11) * s, cy * s, (cx + 11) * s, cy * s, cx * s, (cy - 9) * s], fill=col)
    d.rectangle([(cx - 8) * s, cy * s, (cx + 8) * s, (cy + 10) * s], fill=col)


def icon_gesture(d, cx, cy, col):
    """Two chopping motion arcs."""
    s = S
    d.arc([(cx - 10) * s, (cy - 10) * s, (cx + 2) * s, (cy + 10) * s],
          200, 340, fill=col, width=2 * s)
    d.arc([(cx - 2) * s, (cy - 10) * s, (cx + 10) * s, (cy + 10) * s],
          200, 340, fill=col, width=2 * s)
    # small motion ticks
    d.line([(cx - 12) * s, (cy - 6) * s, (cx - 8) * s, (cy - 6) * s], fill=col, width=2 * s)
    d.line([(cx + 8) * s, (cy + 2) * s, (cx + 12) * s, (cy + 2) * s], fill=col, width=2 * s)


def icon_settings(d, cx, cy, col):
    s = S
    import math
    r = 7
    for k in range(8):
        a = math.radians(k * 45)
        x1 = cx + math.cos(a) * r * 0.9
        y1 = cy + math.sin(a) * r * 0.9
        x2 = cx + math.cos(a) * r * 1.35
        y2 = cy + math.sin(a) * r * 1.35
        d.line([x1 * s, y1 * s, x2 * s, y2 * s], fill=col, width=2 * s)
    d.ellipse([(cx - r * 0.85) * s, (cy - r * 0.85) * s,
               (cx + r * 0.85) * s, (cy + r * 0.85) * s], outline=col, width=2 * s)
    d.ellipse([(cx - 2.5) * s, (cy - 2.5) * s, (cx + 2.5) * s, (cy + 2.5) * s], fill=col)


def header(d):
    d.rectangle([0, 0, W, 104 * S], fill=RED)
    icon_torch(d, 42, 52, 17, WHITE)
    txt(d, (72, 28), "ChopLight", font(22, True), WHITE)
    txt(d, (72, 60), "Chop twice to toggle the flashlight", font(13), RED_LT)


def navbar(d, active=0):
    y = 740 - 64
    d.rectangle([0, y * S, W, H], fill=NAVBG)
    d.line([0, y * S, W, y * S], fill=(45, 45, 45), width=S)
    items = [(icon_home, "Home"), (icon_gesture, "Gestures"), (icon_settings, "Settings")]
    for i, (icfn, lb) in enumerate(items):
        cx = 360 * (i + 0.5) / 3
        on = i == active
        icfn(d, cx, y + 22, RED if on else GREY2)
        txt(d, (cx, y + 42), lb, font(11, on), RED if on else GREY2, anchor="ma")


def switch(d, x, y, on=True):
    """A toggle switch, x,y = left/top of track."""
    tw, th = 46, 26
    track_col = RED if on else (70, 70, 70)
    rr(d, [x, y, x + tw, y + th], th / 2, track_col)
    tx = x + tw - 13 - 3 if on else x + 3 + 13
    d.ellipse([(tx - 10) * S, (y + 3) * S, (tx + 10) * S, (y + 23) * S], fill=WHITE)


def card(d, x, y, w, h):
    rr(d, [x, y, x + w, y + h], 12, CARD)


# ---------------- SCREEN 1: HOME (torch ON) ----------------
im, d = base()
header(d)
# big circular torch button, ON state
cx = 180
d.ellipse([(cx - 80) * S, 120 * S, (cx + 80) * S, 280 * S], fill=RED)
icon_torch(d, cx, 188, 34, WHITE)
txt(d, (cx, 232), "ON", font(18, True), WHITE, anchor="ma")
txt(d, (cx, 300), "Torch is ON", font(20, True), WHITE, anchor="ma")
# listen card
card(d, 24, 344, 312, 96)
txt(d, (40, 360), "Listen for chop-chop", font(16, True), WHITE)
txt(d, (40, 386), "Detects the gesture in the", font(13), GREY)
txt(d, (40, 404), "background, even with screen off", font(13), GREY)
switch(d, 262, 372, on=True)
txt(d, (180, 462), "Tip: make two quick chopping motions,", font(13), GREY2, anchor="ma")
txt(d, (180, 482), "like chopping vegetables.", font(13), GREY2, anchor="ma")
navbar(d, 0)
im.save(f"{SHOT_DIR}/home.png")

# ---------------- SCREEN 2: GESTURES ----------------
im, d = base()
header(d)
# sensitivity card
card(d, 16, 120, 328, 150)
txt(d, (32, 136), "Chop sensitivity", font(16, True), WHITE)
txt(d, (32, 162), "12 m/s^2", font(14, True), RED)
ty = 206  # slider track y
d.line([32 * S, ty * S, 328 * S, ty * S], fill=TRACK, width=3 * S)
px = 32 + (328 - 32) * 0.57  # progress 57
d.line([32 * S, ty * S, px * S, ty * S], fill=RED, width=3 * S)
d.ellipse([(px - 9) * S, (ty - 9) * S, (px + 9) * S, (ty + 9) * S], fill=RED)
txt(d, (32, 222), "Left: gentler chops ignored.", font(12), GREY2)
txt(d, (32, 240), "Right: even small shakes count.", font(12), GREY2)
# motion meter card
card(d, 16, 282, 328, 148)
txt(d, (32, 298), "Live motion meter", font(16, True), WHITE)
my = 340  # meter y
rr(d, [32, my, 328, my + 28], 6, TORCH_OFF)
d.rounded_rectangle([32 * S, my * S, (32 + 296 * 0.62) * S, (my + 28) * S],
                    radius=6 * S, fill=RED)
thx = 32 + 296 * 0.80  # threshold tick
d.line([thx * S, (my - 4) * S, thx * S, (my + 32) * S], fill=WHITE, width=2 * S)
txt(d, (32, my + 38), "Chop your phone and watch the bar spike", font(12), GREY2)
txt(d, (32, my + 56), "past the white line.", font(12), GREY2)
# how-to card
card(d, 16, 442, 328, 196)
txt(d, (32, 458), "How to chop", font(16, True), WHITE)
steps = ["1. Hold your phone firmly in one hand.",
         "2. Make two quick downward chops.",
         "3. The flashlight toggles on the second chop.",
         "",
         "Works with the screen on or off, as long as",
         "listening is enabled."]
yy = 488
for st in steps:
    txt(d, (32, yy), st if st else " ", font(14), GREY)
    yy += 22
navbar(d, 1)
im.save(f"{SHOT_DIR}/gestures.png")

# ---------------- SCREEN 3: SETTINGS ----------------
im, d = base()
header(d)
# start on boot card
card(d, 16, 120, 328, 84)
txt(d, (32, 136), "Start on boot", font(16, True), WHITE)
txt(d, (32, 162), "Keep listening after a restart", font(13), GREY)
switch(d, 274, 148, on=True)
# battery card
card(d, 16, 216, 328, 236)
txt(d, (32, 232), "Battery optimization", font(16, True), WHITE)
txt(d, (32, 258), "Android may kill background listening to save", font(13), GREY)
txt(d, (32, 278), "battery. Disable optimization for ChopLight.", font(13), GREY)
rr(d, [32, 306, 268, 342], 8, RED)
txt(d, (150, 324), "Disable battery optimization", font(13, True), WHITE, anchor="mm")
txt(d, (32, 358), "On Vivo phones also do this:", font(12), GREY2)
txt(d, (32, 378), "Settings > Battery > Background power", font(12), GREY2)
txt(d, (32, 396), "consumption management > ChopLight > Allow", font(12), GREY2)
txt(d, (32, 414), "iManager > Autostart > enable ChopLight", font(12), GREY2)
# about card
card(d, 16, 464, 328, 148)
txt(d, (32, 480), "About", font(16, True), WHITE)
txt(d, (32, 506), "ChopLight 1.0.1", font(13), GREY)
txt(d, (32, 526), "Chop twice to toggle your flashlight.", font(13), GREY)
txt(d, (32, 556), "This app was made by Deon", font(13), GREY)
txt(d, (32, 580), "@deepak.deon", font(14, True), BLUE)
navbar(d, 2)
im.save(f"{SHOT_DIR}/settings.png")

print("screenshots done")
