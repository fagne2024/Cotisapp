from pathlib import Path

p = Path(r"c:\dev\GestionCotisation\frontend\src\app\features\operations\cotisation-mois\cotisation-mois.component.html")
t = p.read_text(encoding="utf-8")
d = "div"
old = f"""          <{d} class="hist-summary">
            <{d} class="hist-sum green">
              <{d} class="n">{{{{ historiqueTotaux().cotisations }}}}</{d}>
              <{d} class="lbl">Cotisations</{d}>
              <{d} class="sub">{{{{ formatFcfa(historiqueTotaux().montantCotisations) }}}} F</{d}>
            </{d}>
            <{d} class="hist-sum or">
              <{d} class="n">{{{{ historiqueTotaux().solidarite }}}}</{d}>
              <{d} class="lbl">Solidarités</{d}>
              <{d} class="sub">{{{{ formatFcfa(historiqueTotaux().montantSolidarite) }}}} F</{d}>
            </{d}>
          </{d}>""".replace("{{{{", "{{").replace("}}}}", "}}")

new = f"""          <{d} class="hist-summary hist-summary-3">
            <{d} class="hist-sum green">
              <{d} class="n">{{{{ historiqueTotaux().hebdo }}}}</{d}>
              <{d} class="lbl">Cotisations hebdo</{d}>
              <{d} class="sub">{{{{ formatFcfa(historiqueTotaux().montantHebdo) }}}} F</{d}>
            </{d}>
            <{d} class="hist-sum pi">
              <{d} class="n">{{{{ historiqueTotaux().mois }}}}</{d}>
              <{d} class="lbl">Cotisations mensuelles</{d}>
              <{d} class="sub">{{{{ formatFcfa(historiqueTotaux().montantMois) }}}} F</{d}>
            </{d}>
            <{d} class="hist-sum or">
              <{d} class="n">{{{{ historiqueTotaux().solidarite }}}}</{d}>
              <{d} class="lbl">Solidarités</{d}>
              <{d} class="sub">{{{{ formatFcfa(historiqueTotaux().montantSolidarite) }}}} F</{d}>
            </{d}>
          </{d}>""".replace("{{{{", "{{").replace("}}}}", "}}")

if old not in t:
    raise SystemExit("old block not found")
p.write_text(t.replace(old, new), encoding="utf-8", newline="\n")
print("ok")
