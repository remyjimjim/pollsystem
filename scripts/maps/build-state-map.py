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

A state that redrew its districts for 2026 (the Census file has the old lines)
gets numbered boxes instead until its official map is added (decided
2026-11-04): python build-state-map.py STATE_CODE --grid N OUT_JSON

Its official map, once found (needs pyproj too; the file can be in any
projection, read from its .prj):
  ... STATE_CODE STATE_FIPS COUNTIES_JSON CD_ZIP OUT_JSON \
      --districts OFFICIAL.zip --field DISTRICT_FIELD --source "Who published it, plan name, date"
  (CD_ZIP is then unused; the state outline still comes from COUNTIES_JSON.)
"""
import io, json, math, sys, zipfile

if len(sys.argv) > 2 and sys.argv[2] == '--grid':
    # Numbered boxes, laid out roughly 8:5, drawn by the same page as a map.
    code, n, out = sys.argv[1], int(sys.argv[3]), sys.argv[4]
    cols = max(1, math.ceil(math.sqrt(n * 1.6)))
    rows = math.ceil(n / cols)
    W, gap = 1000.0, 14.0
    w = (W - (cols + 1) * gap) / cols
    h = min(w * 0.62, 160.0)
    H = round(rows * h + (rows + 1) * gap)
    districts, rects = {}, []
    for i in range(n):
        x, y = gap + (i % cols) * (w + gap), gap + (i // cols) * (h + gap)
        r = f'M{x:.1f},{y:.1f}h{w:.1f}v{h:.1f}h{-w:.1f}Z'
        rects.append(r)
        districts[str(i + 1)] = {'path': r, 'label': [round(x + w / 2, 1), round(y + h / 2 + 6, 1)],
                                 'box': [round(x, 1), round(y, 1), round(w, 1), round(h, 1)], 'small': w < 90}
    json.dump({'state': code, 'width': W, 'height': H, 'grid': True,
               'source': f'Numbered boxes: {code} redrew its congressional districts for 2026; its official map is not added yet',
               'outline': ''.join(rects), 'borders': '', 'districts': districts}, open(out, 'w'), separators=(',', ':'))
    print(f'{code}: grid of {n} districts, {cols} columns, {W:.0f}x{H}')
    sys.exit(0)

import shapefile
from shapely.geometry import shape
from shapely.ops import unary_union, linemerge, polylabel, transform

code, fips, counties_path, cd_path, out = sys.argv[1:6]
exclude = set(sys.argv[sys.argv.index('--exclude-counties') + 1].split(',')) if '--exclude-counties' in sys.argv else set()

d = json.load(open(counties_path))
outline = unary_union([shape(f['geometry']).buffer(0) for f in d['features']
                       if f['id'].startswith(fips) and f['id'] not in exclude]).buffer(0.0005).buffer(-0.0005)

def arg(name, default=None):
    return sys.argv[sys.argv.index(name) + 1] if name in sys.argv else default

dists = {}
source = 'US Census Bureau cartographic boundaries (public domain): counties; congressional districts, 119th Congress (2024)'
if '--districts' in sys.argv:
    # The state's own 2026 plan: one shape per district, numbered in --field.
    z = zipfile.ZipFile(arg('--districts'))
    base = next(n[:-4] for n in z.namelist() if n.lower().endswith('.shp'))
    r = shapefile.Reader(shp=io.BytesIO(z.read(base + '.shp')), shx=io.BytesIO(z.read(base + '.shx')), dbf=io.BytesIO(z.read(base + '.dbf')))
    prj = next((n for n in z.namelist() if n.lower().endswith('.prj')), None)
    wkt = z.read(prj).decode(errors='replace') if prj else ''
    project = None
    if 'PROJCS' in wkt:   # metres in some projection: back to longitude / latitude
        from pyproj import CRS, Transformer
        t = Transformer.from_crs(CRS.from_wkt(wkt), 'EPSG:4326', always_xy=True)
        project = lambda g: transform(t.transform, g)
    field = arg('--field', 'District')
    for sr in r.iterShapeRecords():
        g = shape(sr.shape.__geo_interface__)
        dists[int(sr.record.as_dict()[field])] = (project(g) if project else g).buffer(0)
    source = 'US Census Bureau counties (public domain), outline; districts: ' + arg('--source', base.split('/')[-1])
else:
    z = zipfile.ZipFile(cd_path); base = 'cb_2024_us_cd119_500k'
    r = shapefile.Reader(shp=io.BytesIO(z.read(base + '.shp')), shx=io.BytesIO(z.read(base + '.shx')), dbf=io.BytesIO(z.read(base + '.dbf')))
    names = [f[0] for f in r.fields[1:]]
    for sr in r.iterShapeRecords():
        rec = dict(zip(names, sr.record))
        if rec['STATEFP'] == fips:
            dists[int(rec['CD119FP']) or 1] = shape(sr.shape.__geo_interface__).buffer(0)   # "00" = at-large

# Alaska's Aleutians cross the 180th meridian: move east-of-180 pieces next to
# the rest (longitude - 360), or the state would span the globe.
if outline.bounds[2] - outline.bounds[0] > 180:
    west = lambda g: transform(lambda x, y, z=None: (tuple(v - 360 if v > 0 else v for v in x), y), g)
    outline = west(outline).buffer(0)
    dists = {n: west(g).buffer(0) for n, g in dists.items()}

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
    'source': source,
    'excludedCounties': sorted(exclude),
    'outline': path(rings(outline), True),
    'borders': ''.join(path(lines(b), False) for b in borders),
    'districts': districts,
}, open(out, 'w'), separators=(',', ':'))
print(f'{code}: {len(districts)} districts, {W:.0f}x{H}, small: {[n for n, v in districts.items() if v["small"]]}')

# Official 2026 maps used so far (each replaces its state's grid; sources in docs/DEVLOG.md):
#   TX  Texas Legislative Council, PLANC2333 (HB 4, 2025), data.capitol.texas.gov/dataset/planc2333
#       ... TX 48 counties.json cd.zip TX.json --districts planc2333.zip --field District
