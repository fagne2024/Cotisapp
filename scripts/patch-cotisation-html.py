# -*- coding: utf-8 -*-
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "frontend/src/app/features/operations/cotisation-mois/cotisation-mois.component.html"
t = p.read_text(encoding="utf-8")
tag = "div"
start = t.index("          <" + tag + ' class="op-list">')
end = t.index("      <" + tag + ' class="card">', start + 50)
nb = f"""          <{tag} class="op-list">
            @for (op of cotisationsRecentes(); track op.libelle + op.meta) {{
              <{tag} class="op-row">
                <{tag} class="op-ico {{{{ op.iconeClass }}}}">💰</{tag}>
                <{tag} class="op-info">
                  <{tag} class="op-name">{{{{ op.libelle }}}}</{tag}>
                  <{tag} class="op-meta">{{{{ op.meta }}}}</{tag}>
                </{tag}>
                <{tag} class="op-amt" [class.cr-c]="op.iconeClass === 'g3'" [class.pi-c]="op.iconeClass === 'pi2'">
                  +{{{{ formatFcfa(op.montant) }}}}
                </{tag}>
              </{tag}>
            }} @empty {{
              <p class="empty-filtre">Aucune cotisation récente.</p>
            }}
          </{tag}>
"""
t = t[:start] + nb + t[end:]
t = t.replace(
    f'<{tag} class="big">8</{tag}>',
    f'<{tag} class="big">{{{{ resumeAujourdhui().nombre }}}}</{tag}>',
    1,
)
t = t.replace(
    f'<{tag} class="big">41 500</{tag}>',
    f'<{tag} class="big">{{{{ resumeAujourdhui().montant | number:\'1.0-0\' }}}}</{tag}>',
    1,
)
p.write_text(t, encoding="utf-8", newline="\n")
print("ok")
