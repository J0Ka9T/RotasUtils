"""Hand-authored 16x16 item sprites: runes, gems, scrolls, sigils, rings, keys.

Every sprite is drawn from ASCII rows with a small palette of hue-shifted ramps (light from the top
left, a coloured outline instead of black), so the set shares one look. Sigils are built from a few
frame shapes and a hand-drawn emblem each, so no two share a silhouette and emblem.
"""
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'common', 'src', 'main', 'resources',
                   'assets', 'rotasutils', 'textures', 'item')


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def save(name, grid):
    """grid: 16 rows of 16 (r, g, b) tuples or None."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        for x, c in enumerate(row):
            if c:
                img.putpixel((x, y), (*c, 255))
    img.save(os.path.join(OUT, name + '.png'))


def ascii_sprite(rows, pal):
    assert len(rows) == 16, len(rows)
    grid = []
    for r in rows:
        assert len(r) == 16, (r, len(r))
        grid.append([rgb(pal[ch]) if ch in pal else None for ch in r])
    return grid


def overlay(grid, rows, pal, x0, y0):
    for j, r in enumerate(rows):
        for i, ch in enumerate(r):
            if ch in pal:
                grid[y0 + j][x0 + i] = rgb(pal[ch])
    return grid


TABLET = [
    "................",
    "....oooooooo....",
    "...ohhbbbbbbo...",
    "..ohhbbbbbbbbo..",
    "..ohbbbbbbbbbo..",
    "..obbbbbbbbbbo..",
    "..obbbbbbbbbdo..",
    "..obbbbbbbbbdo..",
    "..obbbbbbbbbdo..",
    "..obbbbbbbbddo..",
    "..obbbbbbbbddo..",
    "..obbbbbbbdddo..",
    "...oddbbbdddoo..",
    "....ooooooooo...",
    "................",
    "................",
]
STONE = {'o': '#2b2630', 'h': '#8f8896', 'b': '#6c6674', 'd': '#4c4757'}

SIGNS = {
    'fire': ([
        "...3....",
        "..33....",
        "..323...",
        ".3223.3.",
        ".32123..",
        "3221123.",
        "3211123.",
        ".32223..",
        "..333...",
    ], {'1': '#fff2a8', '2': '#ff9a2e', '3': '#c2361c'}),
    'frost': ([
        "...1...",
        ".1.2.1.",
        "..323..",
        "1223221",
        "..323..",
        ".1.2.1.",
        "...1...",
    ], {'1': '#eaffff', '2': '#7fd8f2', '3': '#3d86c9'}),
    'fury': ([
        "...111..",
        "..112...",
        ".112....",
        ".11222..",
        "..1122..",
        "...112..",
        "..12....",
        ".2......",
    ], {'1': '#fff4a0', '2': '#f6b21e', '3': '#b8620f'}),
    'lifesteal': ([
        ".22.22.",
        "2112112",
        "2111112",
        ".21112.",
        "..212..",
        "...2...",
    ], {'1': '#ff8a94', '2': '#d6283f', '3': '#7d1226'}),
    'venom': ([
        "...2...",
        "..212..",
        "..212..",
        ".21112.",
        ".21112.",
        ".22112.",
        "..222..",
    ], {'1': '#c9f56a', '2': '#5fbf3a', '3': '#2f7a2a'}),
}


def runes():
    for name, (rows, pal) in SIGNS.items():
        grid = ascii_sprite(TABLET, STONE)
        w, h = len(rows[0]), len(rows)
        x0, y0 = 8 - w // 2, 3 + (9 - h) // 2 + 1
        overlay(grid, rows, pal, x0, y0)
        grid[2][11] = None
        grid[3][12] = rgb('#4c4757')
        grid[10][3] = rgb('#4c4757')
        save('rune_' + name, grid)


GEM = [
    "................",
    ".......oo.......",
    "......ohho......",
    ".....ohhllo.....",
    "....ohhllmmo....",
    "....ohllmmmo....",
    "...ohhllmmmdo...",
    "...ohllmmmmdo...",
    "...ohllmmmddo...",
    "...ollmmmmddo...",
    "....ollmmmddo...",
    "....ollmmmdo....",
    ".....olmmdo.....",
    "......oddo......",
    ".......oo.......",
    "................",
]


def gems():
    ramps = {
        'elunium': dict(o='#123a2e', h='#d8ffd0', l='#7fe29a', m='#2fae74', d='#17755a'),
        'oridecon': dict(o='#14284d', h='#dbeeff', l='#82bdf5', m='#3b7fd6', d='#2453a3'),
    }
    for name, pal in ramps.items():
        save(name, ascii_sprite(GEM, pal))
        g = ascii_sprite(GEM, pal)
        for x, y in ((8, 6), (8, 7), (7, 8), (8, 8), (8, 9)):
            g[y][x] = rgb(pal['h'])
        for y in range(4, 13):
            g[y][7] = rgb(pal['l']) if y % 2 else g[y][7]
        for x, y in ((2, 2), (13, 3), (12, 12), (3, 11)):
            g[y][x] = rgb('#ffffff')
        for x, y in ((1, 2), (3, 2), (2, 1), (2, 3), (14, 3), (12, 3), (13, 2), (13, 4)):
            g[y][x] = rgb(pal['h'])
        save('enriched_' + name, g)


SCROLL = [
    "................",
    "..oooooooooooo..",
    ".oRRRRRRRRRRRRo.",
    ".orRrrrrrrrrrro.",
    "..oPPPPPPPPPPo..",
    "..oPttttttttPo..",
    "..oPPPPPPPPPdo..",
    "..oPttttttPPdo..",
    "..oPPPPPPPPPdo..",
    "..oPtttttPPPdo..",
    "..oPPPPPPPPddo..",
    ".orRrrrrrrrrrro.",
    ".oRRRRRRRRRRRRo.",
    "..oooooooooooo..",
    "................",
    "................",
]


def scrolls():
    paper = dict(o='#3b2a22', P='#efe0b8', d='#cdb98c', R='#a8703a', r='#7c4a26')
    specs = {
        'blessing_scroll': (dict(t='#8ba36a'), ('#5fae5a', '#b9f08c'), 'leaf'),
        'certificate_scroll': (dict(t='#6d5aa8'), ('#7b52c9', '#e6cf6a'), 'ribbon'),
        'protection_scroll': (dict(t='#8f4a3c'), ('#b8302a', '#ff9a86'), 'wax'),
    }
    for name, (ink, (seal, hi), kind) in specs.items():
        pal = {**paper, **ink}
        g = ascii_sprite(SCROLL, pal)
        seal_shape = [
            ".oo.",
            "oSHo",
            "oSSo",
            ".oo.",
        ]
        for j, r in enumerate(seal_shape):
            for i, ch in enumerate(r):
                c = {'o': '#33161a', 'S': seal, 'H': hi}.get(ch)
                if c:
                    g[9 + j][10 + i] = rgb(c)
        if kind == 'ribbon':
            for y in (13, 14):
                g[y][11] = rgb('#7b52c9')
                g[y][12] = rgb('#5a3aa0')
        save(name, g)
    glyphs = {
        'scroll_abyss': ([
            "..3..3..",
            ".232232.",
            "23122132",
            "21111112",
            ".211112.",
            "..2112..",
            "...22...",
        ], {'1': '#e7c8ff', '2': '#9a62e0', '3': '#5a2f96'}),
        'scroll_celestial': ([
            "...1....",
            "..121...",
            "1.222.1.",
            ".22322..",
            "1.222.1.",
            "..121...",
            "...1....",
        ], {'1': '#ffffff', '2': '#8fd0ff', '3': '#3f7fd6'}),
    }
    for name, (rows, gp) in glyphs.items():
        pal = {**paper, 't': '#efe0b8'}
        g = ascii_sprite(SCROLL, pal)
        overlay(g, rows, gp, 4, 5)
        save(name, g)


def shape_mask(kind):
    pts = set()
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            if kind == 'round':
                ok = dx * dx + dy * dy <= 7.4 ** 2
            elif kind == 'hex':
                ok = abs(dy) <= 7.4 and abs(dx) * 0.866 + abs(dy) * 0.5 <= 6.9
            elif kind == 'diamond':
                ok = abs(dx) + abs(dy) <= 8.2
            elif kind == 'shield':
                ok = abs(dx) <= 6.6 and dy >= -7.4 and (dy < 1 or abs(dx) <= 6.6 - (dy - 1) * 0.72)
            else:
                ok = False
            if ok:
                pts.add((x, y))
    return pts


def medal(kind, metal, field, emblem, epal):
    """metal: (outline, light, mid, dark); field: (light, dark) of the recessed centre."""
    mask = shape_mask(kind)

    def edge_dist(x, y):
        d = 0
        while d < 4:
            d += 1
            for nx, ny in ((x - d, y), (x + d, y), (x, y - d), (x, y + d)):
                if (nx, ny) not in mask:
                    return d
        return 4

    g = [[None] * 16 for _ in range(16)]
    o, lt, md, dk = (rgb(c) for c in metal)
    f_lt, f_dk = rgb(field[0]), rgb(field[1])
    for (x, y) in mask:
        d = edge_dist(x, y)
        if d == 1:
            g[y][x] = o
        elif d <= 3:
            lit = (x + y) < 15
            g[y][x] = lt if (lit and d == 2) else (md if d == 2 or lit else dk)
        else:
            g[y][x] = f_lt if (x + y) < 14 else f_dk
    for (x, y) in mask:
        if edge_dist(x, y) == 4 and ((x - 1, y) in mask and edge_dist(x - 1, y) == 3
                                     or (x, y - 1) in mask and edge_dist(x, y - 1) == 3):
            g[y][x] = f_dk
    overlay(g, emblem, epal, 4, 4)
    return g


EMBLEMS = {
    'eye': ([
        "........",
        "..2222..",
        ".211112.",
        "21122112",
        "21233212",
        ".212212.",
        "..2222..",
        "........",
    ], 'eye'),
    'flame': ([
        "...2....",
        "..22....",
        "..212.2.",
        ".2112.2.",
        ".21112..",
        ".21112..",
        "..222...",
        "........",
    ], 'flame'),
    'sun': ([
        "...2....",
        ".2.1.2..",
        "..111...",
        "2111112.",
        "..111...",
        ".2.1.2..",
        "...2....",
        "........",
    ], 'sun'),
    'crown': ([
        "........",
        "2..2..2.",
        "21.21.12",
        "21121112",
        "21111112",
        "22222222",
        "........",
        "........",
    ], 'crown'),
    'star': ([
        "...1....",
        "...1....",
        "..212...",
        "1122211.",
        "..212...",
        "...1....",
        "...1....",
        "........",
    ], 'star'),
    'crack': ([
        "...11...",
        "..11....",
        "..112...",
        "...11...",
        "..112...",
        "..11....",
        "...11...",
        "..11....",
    ], 'crack'),
    'blades': ([
        "1......1",
        ".1....1.",
        "..1..1..",
        "...11...",
        "...11...",
        "..1..1..",
        ".2....2.",
        "22....22",
    ], 'blades'),
    'swirl': ([
        "..2222..",
        ".2....2.",
        "2..22..2",
        "2.2..2.2",
        "2.2.22.2",
        "2..2..2.",
        ".2...22.",
        "..2222..",
    ], 'swirl'),
    'maw': ([
        "1.1.1.1.",
        "11111111",
        "22222222",
        "23333332",
        "22222222",
        ".1.1.1.1",
        "..1.1.1.",
        "........",
    ], 'maw'),
}


def sigils():
    metals = {
        'crimson': ('#2a0d12', '#e07a70', '#a42c2c', '#5c1418'),
        'violet': ('#1a1030', '#b89af0', '#6c48c0', '#3a2478'),
        'gold': ('#3a2410', '#ffe08a', '#d9a03a', '#8a5a1c'),
        'bronze': ('#2e1a1c', '#e0aa98', '#a8705e', '#68403a'),
        'iron': ('#15151c', '#8b8fa8', '#565a70', '#30323f'),
        'green': ('#0b1f14', '#79c88a', '#3a8a58', '#1c4a34'),
        'void': ('#08060c', '#5a5068', '#332c40', '#1a1622'),
    }
    fields = {
        'crimson': ('#4a1018', '#2a080e'), 'violet': ('#2c1a54', '#180d34'), 'gold': ('#5a3a16', '#38220c'),
        'bronze': ('#4a2c30', '#2c181c'), 'iron': ('#1c1c28', '#0e0e16'), 'green': ('#14382a', '#0a2016'),
        'void': ('#120e18', '#08060a'),
    }
    red = {'1': '#ffb0a0', '2': '#ff5040', '3': '#1a0508'}
    vio = {'1': '#f0dcff', '2': '#b070ff', '3': '#0e0618'}
    gld = {'1': '#fff2b0', '2': '#ffc040', '3': '#3a1c04'}
    grn = {'1': '#d8ffc8', '2': '#5fdc7a', '3': '#04140a'}
    wht = {'1': '#ffffff', '2': '#ffd9a0', '3': '#3a1c04'}
    specs = [
        ('crimson_sigil', 'round', 'crimson', 'eye', red),
        ('crimson_herald_sigil', 'hex', 'crimson', 'crown', red),
        ('eldritch_sigil', 'round', 'violet', 'eye', vio),
        ('gold_sigil', 'round', 'gold', 'sun', gld),
        ('gold_herald_sigil', 'hex', 'gold', 'crown', gld),
        ('herald_sigil', 'hex', 'bronze', 'star', {'1': '#ffe8d8', '2': '#e0a090', '3': '#2c1418'}),
        ('rift_sigil', 'diamond', 'violet', 'crack', vio),
        ('sky_clash_sigil', 'shield', 'iron', 'blades', {'1': '#ffe89a', '2': '#a070ff', '3': '#0e0e16'}),
        ('sundering_sigil', 'round', 'gold', 'crack', wht),
        ('tentacle_rift', 'round', 'green', 'swirl', grn),
        ('void_maw', 'round', 'void', 'maw', {'1': '#e8e0f0', '2': '#7a6a90', '3': '#20101a'}),
        ('void_sigil', 'round', 'green', 'eye', grn),
    ]
    for name, shape, metal, emb, epal in specs:
        rows = EMBLEMS[emb][0]
        save(name, medal(shape, metals[metal], fields[metal], rows, epal))
    quarters = [
        "..rrgg..",
        ".rrrggg.",
        "rrrrgggg",
        "rrrrgggg",
        "yyyyvvvv",
        "yyyyvvvv",
        ".yyyvvv.",
        "..yyvv..",
    ]
    qpal = {'r': '#ff5040', 'g': '#5fdc7a', 'y': '#ffc040', 'v': '#8f6cff'}
    save('convergence_sigil', medal('round', metals['gold'], fields['gold'], quarters, qpal))


KEY = [
    "................",
    "....oooo........",
    "...oHHVVo.......",
    "..oHvvvVVo......",
    "..oHv..vVo......",
    "..oHv..vVo......",
    "..oVvvvVVo......",
    "...oVVVVo.......",
    "....ooBBo.......",
    ".....oBBBo......",
    "......oBBBo.....",
    ".......oBBBo.o..",
    "........oBBBoBo.",
    ".........oBBBBo.",
    "..........oooo..",
    "................",
]


def keys():
    pal = {'o': '#140c26', 'H': '#f0e2ff', 'V': '#8d5ce0', 'v': '#4b2a94', 'B': '#b48ae8'}
    g = ascii_sprite(KEY, pal)
    for x, y in ((4, 4), (5, 5)):
        g[y][x] = rgb('#ffffff')
    save('rift_key', g)


RING = [
    "................",
    "................",
    "......oGGo......",
    ".....oGwwGo.....",
    ".....oGGGGo.....",
    "....oBBooBBo....",
    "...oBLLBBBBBo...",
    "..oBLo......oBo.",
    "..oBo........oBo",
    "..oBo........odo",
    "..oBBo......odBo",
    "...oBBo....odBo.",
    "....oBBbbbddBo..",
    ".....ooddddoo...",
    "................",
    "................",
]


def rings():
    specs = {
        'affinity_ring_abyss': dict(o='#120a24', G='#a25cf0', w='#f4e2ff', B='#5c4a7c', L='#9a88c0', b='#3e3058', d='#2a2040'),
        'affinity_ring_celestial': dict(o='#0c1e3a', G='#5cc0ff', w='#ffffff', B='#c8d4e6', L='#f2f6fc', b='#8ea0bc', d='#6a7c98'),
    }
    for name, pal in specs.items():
        save(name, ascii_sprite(RING, pal))


if __name__ == '__main__':
    for f in (runes, gems, scrolls, sigils, keys, rings):
        f()
        print('drew', f.__name__)
