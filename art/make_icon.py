"""Builds the adaptive launcher icon layers from the hero mark.

Run from anywhere:  python art/make_icon.py   (needs Pillow).
Rewrites res/mipmap-*/ic_launcher_foreground.png and ic_launcher_monochrome.png plus art/icon_preview.png.

Foreground: the runner cropped out of the hero (wordmark masked), feathered into a flat colour
that matches the artwork's background, scaled so the figure sits inside the 66dp safe zone.
Monochrome: the bright strokes of the same composite as an alpha mask, for themed icons.
"""
import os
from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "painani-hero-mark.png")
RES = os.path.join(HERE, "..", "app", "src", "main", "res")

DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
CANVAS = 432                     # xxxhdpi master, 108dp @ 4x
FIGURE_SPAN = 288                # px of the master the crop occupies (figure ~81% of that = ~233px < 264px safe zone)
CROP = (140, 120, 1060, 1040)    # square around the runner, above the wordmark
FEATHER = 70                     # px of crop edge that fades out

im = Image.open(SRC).convert("RGBA")

# Mask the wordmark and tagline with nearby background so the feet can be cropped generously.
bg_sample = im.getpixel((1100, 1050))
im.paste(Image.new("RGBA", (1200, 200), bg_sample), (0, 1000))

crop = im.crop(CROP)
w = crop.size[0]

# Square-edge feather: alpha ramps from 0 at the edge to 1 FEATHER px in.
ramp = Image.linear_gradient("L").resize((w, FEATHER))  # 0 at top -> 255 at bottom
edge = Image.new("L", (w, w), 255)
edge.paste(ramp, (0, 0))
edge.paste(ramp.transpose(Image.FLIP_TOP_BOTTOM), (0, w - FEATHER))
crop.putalpha(ImageChops.multiply(edge, edge.transpose(Image.ROTATE_90)))

# Flat canvas colour = average of the crop's border ring, so the feather lands on itself.
px = crop.convert("RGB").load()
ring = [px[x, y] for x in range(w) for y in (0, 1, w - 2, w - 1)] + [px[x, y] for y in range(w) for x in (0, 1, w - 2, w - 1)]
flat = tuple(sum(c[i] for c in ring) // len(ring) for i in range(3))
print("background colour %s -> set ic_launcher_background in res/values/colors.xml" % ("#%02X%02X%02X" % flat))

master = Image.new("RGBA", (CANVAS, CANVAS), flat + (255,))
off = (CANVAS - FIGURE_SPAN) // 2
master.alpha_composite(crop.resize((FIGURE_SPAN, FIGURE_SPAN), Image.LANCZOS), (off, off))

# Monochrome layer: luminance of the bright strokes -> alpha. Body fills are near the background
# so they drop out, leaving the outline figure, which is what a themed icon wants.
mono_alpha = master.convert("L").point(lambda v: max(0, min(255, int((v - 70) * 255 / 110))))
mono = Image.new("RGBA", (CANVAS, CANVAS), (255, 255, 255, 0))
mono.putalpha(mono_alpha.filter(ImageFilter.GaussianBlur(0.6)))

for name, size in DENSITIES.items():
    d = os.path.join(RES, f"mipmap-{name}")
    os.makedirs(d, exist_ok=True)
    master.resize((size, size), Image.LANCZOS).save(os.path.join(d, "ic_launcher_foreground.png"), optimize=True)
    mono.resize((size, size), Image.LANCZOS).save(os.path.join(d, "ic_launcher_monochrome.png"), optimize=True)


def masked(img, size):
    """The icon as a launcher shows it: circular mask over the 72dp visible area of the 108dp layer."""
    vis = int(size * 72 / 108)
    inner = img.resize((size, size), Image.LANCZOS)
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).ellipse(((size - vis) // 2, (size - vis) // 2, (size + vis) // 2, (size + vis) // 2), fill=255)
    out = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    out.paste(inner, (0, 0), m)
    return out.crop(((size - vis) // 2, (size - vis) // 2, (size + vis) // 2, (size + vis) // 2))


sheet = Image.new("RGBA", (900, 340), (240, 240, 240, 255))
x = 10
for s in (432, 216, 144, 96, 72):
    ic = masked(master, s)
    sheet.paste(ic, (x, 10), ic)
    x += ic.size[0] + 16
mono_prev = Image.new("RGBA", (216, 216), (30, 30, 30, 255))
mono_prev.paste(Image.new("RGBA", (216, 216), (200, 220, 255, 255)), (0, 0), mono.resize((216, 216), Image.LANCZOS))
sheet.paste(masked(mono_prev, 216), (x, 10))
sheet.save(os.path.join(HERE, "icon_preview.png"))
print("done")
