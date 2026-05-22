# -*- coding: utf-8 -*-
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "frontend/src/app/features/membres/membres-page.component.html"
t = p.read_text(encoding="utf-8")
m = "mot" + "ion"
d = "div"
t = t.replace("<" + m + " ", "<" + d + " ")
t = t.replace("</" + m + ">", "</" + d + ">")

replacements = [
    ("pickModalPoste('president')", "???", "\U0001f451"),
    ("pickModalPoste('vice_president')", "???", "\U0001f3c5"),
    ("pickModalPoste('sga')", "???", "\U0001f4cb"),
    ("pickModalPoste('tresorier')", "\U0001f4dd", "\U0001f4bc"),
    ("pickModalPoste('superviseur')", "\U0001f4dd", "\U0001f50d"),
]
for anchor, old, new in replacements:
    i = t.find(anchor)
    if i < 0:
        continue
    chunk = t[i : i + 400]
    needle = f'<{d} class="po-ico">{old}</{d}>'
    if needle in chunk:
        chunk = chunk.replace(needle, f'<{d} class="po-ico">{new}</{d}>', 1)
        t = t[:i] + chunk + t[i + 400 :]

t = t.replace(f'<{d} class="ca-ico">?</{d}>', f'<{d} class="ca-ico">\U0001f3f7</{d}>')
t = t.replace("?? Cr", "\u2705 Cr")
t = t.replace("?? Lancer", "\u2705 Lancer")
t = t.replace("\U0001f3c7 Cartes", "\U0001f5c3 Cartes")

p.write_text(t, encoding="utf-8", newline="\n")
print("ok")
