# Builds preview.html with the SVG(s) inline, so they take the page's text colour.
import re, sys
names = sys.argv[1:] or ["ballotbox-sketch-v1.svg"]
def inline(name, h):
    svg = open(name).read()
    svg = re.sub(r'<svg ([^>]*?)width="\d+" height="\d+"', rf'<svg \1style="height:{h}px;width:auto"', svg, count=1)
    return svg
rows = []
for n in names:
    rows.append(f'<h2>{n}: in today\'s 64px header (logo 56px)</h2><header>{inline(n,56)}<span>BallotBox</span><div class="nav"><span>Search Polls</span><span>Help</span><span>Login</span></div></header>')
    rows.append(f'<h2>{n}: in an 80px header (logo 72px)</h2><header class="tall">{inline(n,72)}<span>BallotBox</span><div class="nav"><span>Search Polls</span><span>Help</span><span>Login</span></div></header>')
    rows.append(f'<h2>{n}: on white, large</h2><div class="light">{inline(n,210)}</div>')
open("preview.html","w").write("""<!doctype html><html><head><meta charset="utf-8"><title>BallotBox logo preview</title>
<style>
 body{margin:0;padding:8px 32px 32px;font-family:system-ui,sans-serif;background:#f1f5f9;color:#1e293b}
 header{background:#1e293b;color:#fff;height:64px;display:flex;align-items:center;gap:12px;padding:0 32px}
 header.tall{height:80px} header span{font-size:20px;font-weight:600}
 .nav{margin-left:auto;display:flex;gap:24px;font-size:15px;color:#e2e8f0}
 .nav span{font-weight:400;font-size:15px}
 h2{font-size:13px;color:#64748b;margin:20px 0 6px;font-weight:600}
 .light{background:#fff;color:#1e293b;padding:16px;display:inline-block;border:1px solid #e2e8f0}
</style></head><body>""" + "\n".join(rows) + "</body></html>")
