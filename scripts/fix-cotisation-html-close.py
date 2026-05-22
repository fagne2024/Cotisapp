from pathlib import Path
p = Path(r"c:\dev\GestionCotisation\frontend\src\app\features\operations\cotisation-mois\cotisation-mois.component.html")
t = p.read_text(encoding="utf-8")
old = "          </div>\n      <motion class=\"card\">".replace("motion", "motion")
old = "          </div>\n      <div class=\"card\">"
new = "          </motion>\n        </motion>\n      </motion>\n      <motion class=\"card\">".replace("motion", "motion")
new = "          </div>\n        </div>\n      </motion>\n      <div class=\"card\">".replace("</motion>", "</div>").replace("<motion", "<div")
if old not in t:
    raise SystemExit("pattern not found")
p.write_text(t.replace(old, new, 1), encoding="utf-8", newline="\n")
print("fixed")
