#!/usr/bin/env python3
"""
build-state-map.py: one state's district map for /maps/XX (decided 2026-11-03).

Reads Census public-domain boundaries and writes frontend/public/maps/XX.json:
the state outline, the borders between congressional districts, each district's
shape (to zoom to and highlight) and a spot for its label, all as SVG path data
in a 1000-wide box. The page draws and colors it live from /api/polls/maps/XX/results.

Inputs (download once, e.g. into tmp/maps/):
  counties.json  https://raw.githubusercontent.com/plotly/datasets/master/geojson-counties-fips.json
  cd.zip         https://www2.census.gov/geo/tiger/GENZ2024/shp/cb_2024_us_cd119_500k.zip  (119th Congress)

Usage (needs shapely and pyshp; e.g. in python:3.12-slim with pip install shapely pyshp):
  python build-state-map.py STATE_CODE STATE_FIPS COUNTIES_JSON CD_ZIP OUT_JSON [--exclude-counties FIPS,...]
  e.g. WA 53 tmp/maps/counties.json tmp/maps/cd.zip frontend/public/maps/WA.json --exclude-counties 53029
       (Washington without Island County, as chosen 2026-11-02)
"""
import io, json, math, sys, zipfile
import shapefile
from shapely.geometry import shape
from shapely.ops import unary_union, linemerge, polylabel

code, fips, counties_path, cd_path, out = sys.argv[1:6]
exclude = set(sys.argv[sys.argv.index('--exclude-counties') + 1].split(',')) if '--exclude-counties' in sys.argv else set()

d = json.load(open(counties_path))
outline = unary_union([shape(f['geometry']).buffer(0) for f in d['features']
                       if f['id'].startswith(fips) and f['id'] not in exclude]).buffer(0.0005).buffer(-0.0005)

z = zipfile.ZipFile(cd_path); base = 'cb_2024_us_cd119_500k'
r = shapefile.Reader(shp=io.BytesIO(z.read(base + '.shp')), shx=io.BytesIO(z.read(base + '.shx')), dbf=io.BytesIO(z.read(base + '.dbf')))
names = [f[0] for f in r.fields[1:]]
dists = {}
for sr in r.iterShapeRecords():
    rec = dict(zip(names, sr.record))
    if rec['STATEFP'] == fips:
        dists[int(rec['CD119FP']) or 1] = shape(sr.shape.__geo_interface__).buffer(0)   # "00" = at-large

TOL = 0.002   # degrees (~150 m): small file, no visible loss at page size
outline = outline.simplify(TOL)
minx, miny, maxx, maxy = outline.bounds
k = math.cos(math.radians((miny + maxy) / 2))           # longitude shrinks with latitude
W = 1000.0
scale = W / ((maxx - minx) * k)
H = round((maxy - miny) * scale)
def xy(x, y): return (round((x - minx) * k * scale, 1), round((maxy - y) * scale, 1))

def rings(g):
    t = g.geom_type
    if t == 'Polygon': yield g.exterior; yield from g.interiors
    elif t in ('MultiPolygon', 'GeometryCollection'):
        for p in g.geoms: yield from rings(p)
def lines(g):
    t = g.geom_type
    if t in ('LineString', 'LinearRing'): yield g
    elif t in ('MultiLineString', 'GeometryCollection'):
        for p in g.geoms: yield from lines(p)
def path(parts, close):
    out = []
    for ln in parts:
        pts = [xy(*c) for c in ln.coords]
        if len(pts) < 2: continue
        out.append('M' + 'L'.join(f'{x},{y}' for x, y in pts) + ('Z' if close else ''))
    return ''.join(out)

inside = outline.buffer(0.002)
keys = sorted(dists)
borders = []
for i, a in enumerate(keys):
    for b in keys[i + 1:]:
        line = dists[a].boundary.intersection(dists[b].boundary).intersection(inside)
        parts = [g for g in getattr(line, 'geoms', [line]) if g.geom_type in ('LineString', 'MultiLineString')]
        u = unary_union(parts) if parts else None
        if u is None or u.is_empty: continue
        borders.append((u if u.geom_type == 'LineString' else linemerge(u)).simplify(TOL))

districts = {}
for n, g in dists.items():
    part = g.intersection(outline).simplify(TOL)
    big = max(getattr(part, 'geoms', [part]), key=lambda p: p.area)
    pt = polylabel(big, tolerance=0.001)
    bx0, by0, bx1, by1 = part.bounds
    (x0, y1), (x1, y0) = xy(bx0, by0), xy(bx1, by1)
    districts[str(n)] = {
        'path': path(rings(part), True),
        'label': list(xy(pt.x, pt.y)),
        'box': [x0, y0, round(x1 - x0, 1), round(y1 - y0, 1)],
        # Districts too small for a full label at the whole-state size.
        'small': big.area * scale * scale * k < 2500,
    }

json.dump({
    'state': code, 'width': W, 'height': H,
    'source': 'US Census Bureau cartographic boundaries (public domain): counties; congressional districts, 119th Congress (2024)',
    'excludedCounties': sorted(exclude),
    'outline': path(rings(outline), True),
    'borders': ''.join(path(lines(b), False) for b in borders),
    'districts': districts,
}, open(out, 'w'), separators=(',', ':'))
print(f'{code}: {len(districts)} districts, {W:.0f}x{H}, small: {[n for n, v in districts.items() if v["small"]]}')
