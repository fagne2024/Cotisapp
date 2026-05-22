from pathlib import Path

p = Path(r"c:\dev\GestionCotisation\frontend\src\app\features\operations\cotisation-mois\cotisation-mois.component.html")
t = p.read_text(encoding="utf-8")
t = t.replace(
    "{{ resumeAujourdhui().montant | number:'1.0-0' }}",
    "{{ formatFcfa(resumeAujourdhui().montant) }}",
)
p.write_text(t, encoding="utf-8", newline="\n")
print("ok")
