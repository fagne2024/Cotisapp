# -*- coding: utf-8 -*-
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "frontend/src/app/features/membres/membres-page.component.html"
text = path.read_text(encoding="utf-8")

# Balises erronees eventuelles
text = text.replace("<mot ", "<div ").replace("</mot>", "</div>")
text = text.replace("<motion ", "<motion ").replace("</motion>", "</motion>")
text = text.replace("<motion ", "<div ").replace("</motion>", "</div>")

icons = [
    ("Pr\u00e9sident(e)", "\U0001f451"),
    ("Vice-pr\u00e9sident(e)", "\U0001f396\ufe0f"),
    ("Secr\u00e9taire G\u00e9n\u00e9ral", "\U0001f4dd"),
    ("S.G. Adjoint(e)", "\U0001f4cb"),
    ("Tr\u00e9sorier(\u00e8re)", "\U0001f4bc"),
    ("Superviseur", "\U0001f50d"),
]

for label, ico in icons:
    needle = f'<div class="po-label">{label}</div>'
    pos = text.find(needle)
    if pos == -1:
        continue
    before = text[:pos]
    broken = '<motion class="po-ico">'.replace("motion", "div")
    broken = '<div class="po-ico">???</div>'
    bpos = before.rfind(broken)
    if bpos == -1:
        broken2 = '<div class="po-ico">??</div>'
        bpos = before.rfind(broken2)
        broken = broken2 if bpos != -1 else broken
    if bpos != -1:
        text = text[:bpos] + f'<div class="po-ico">{ico}</div>' + text[bpos + len(broken) :]

text = text.replace('<div class="ca-ico">?</div>', '<div class="ca-ico">\U0001f3f7</div>')
text = text.replace("?? Cr\u00e9er le membre", "\u2705 Cr\u00e9er le membre")
text = text.replace("?? Lancer l'import", "\u2705 Lancer l'import")
text = text.replace('<div class="po-ico">???</motion>', '<div class="po-ico">👑</div>')
text = text.replace('<div class="po-ico">???</div>', '<div class="po-ico">👑</div>')

path.write_text(text, encoding="utf-8", newline="\n")
print("ok")
