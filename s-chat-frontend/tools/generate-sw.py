#!/usr/bin/env python3
"""Regenerates the PRECACHE list inside sw.js from the files that actually exist.
Run from the frontend/ folder after adding or removing any file under js/, css/ or assets/:   python3 tools/generate-sw.py
Also bump VERSION in sw.js for each release so browsers drop their old cache."""
import glob, re
files = sorted(['/', '/offline.html', '/manifest.webmanifest', '/favicon.ico', '/css/app.css'] + ['/' + f for f in glob.glob('js/**/*.js', recursive=True)] + ['/' + f for f in glob.glob('assets/*.png')])
s = open('sw.js').read()
block = 'const PRECACHE = [\n  ' + ',\n  '.join('"%s"' % f for f in files) + '\n];'
s, n = re.subn(r'const PRECACHE = \[.*?\];', block, s, count=1, flags=re.S)
assert n == 1, "PRECACHE block not found"
open('sw.js', 'w').write(s); print(len(files), "files in the precache list")
