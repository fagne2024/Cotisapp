# -*- coding: utf-8 -*-
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "frontend/src/app/features/operations/cotisation-mois/cotisation-mois.component.html"
t = p.read_text(encoding="utf-8")

header_old = """      <motion class="pt">{{ typeUi() === 'hebdo' ? 'Saisir une cotisation' : 'Saisir une cotisation mensuelle' }}</motion>""".replace("motion", "div")
header_new = """      <div class="pt">
        @if (typeUi() === 'historique') {
          Historique des cotisations
        } @else if (typeUi() === 'hebdo') {
          Saisir une cotisation
        } @else {
          Saisir une cotisation mensuelle
        }
      </div>"""

ps_old = """      <motion class="ps">
        Règle active : {{ regleActive().libelle }} ·""".replace("motion", "motion")
ps_old = """      <div class="ps">
        Règle active : {{ regleActive().libelle }} ·"""
ps_new = """      <motion class="ps">
        @if (typeUi() === 'historique') {
          Cotisations hebdo, mensuelles et solidarité enregistrées pour l'organisation.
        } @else {
          Règle active : {{ regleActive().libelle }} ·""".replace("motion", "motion")
ps_new = ps_new.replace("<motion", "<motion").replace("motion class", "div class", 1)
# fix ps_new properly
ps_new = """      <div class="ps">
        @if (typeUi() === 'historique') {
          Cotisations hebdo, mensuelles et solidarité enregistrées pour l'organisation.
        } @else {
          Règle active : {{ regleActive().libelle }} ·"""

# close the @else block before closing ps div - find amende line end
amende_line = "        · Amende {{ formatFcfa(regleActive().montantAmendeMin) }} – {{ formatFcfa(regleActive().montantAmendeMax) }} F\n      </motion>"
amende_line = amende_line.replace("</motion>", "</motion>").replace("<motion", "<div").replace("</motion>", "</motion>")
amende_line = """        · Amende {{ formatFcfa(regleActive().montantAmendeMin) }} – {{ formatFcfa(regleActive().montantAmendeMax) }} F
        }
      </div>"""

t = t.replace(header_old, header_new)
# replace ps block
idx = t.index("      <div class=\"ps\">")
idx2 = t.index("      </div>", idx) + len("      </motion>")
idx2 = t.index("      </div>", idx) + len("      </div>")
old_ps_block = t[idx:idx2]
new_ps_block = ps_new + """
        {{ formatFcfa(regleActive().montantMin) }} – {{ formatFcfa(regleActive().montantMax) }} FCFA
        @if (regleActive().solidariteAuto) {
          · Solidarité auto {{ formatFcfa(regleActive().montantSolidarite) }} F
        }
""" + amende_line
t = t[:idx] + new_ps_block + t[idx2:]

tab_insert = """    <button
      type="button"
      class="type-tab"
      [class.on-blue]="typeUi() === 'historique'"
      (click)="setType('historique')"
    >
      <span>📋 Historique</span>
      <span class="tt-sub">Cotisations & solidarité</span>
    </button>
  </motion>"""
tab_insert = tab_insert.replace("</motion>", "</div>").replace("<motion", "<motion")
tab_insert = """    <button
      type="button"
      class="type-tab"
      [class.on-blue]="typeUi() === 'historique'"
      (click)="setType('historique')"
    >
      <span>📋 Historique</span>
      <span class="tt-sub">Cotisations & solidarité</span>
    </button>
  </motion>"""
tab_insert = """    <button
      type="button"
      class="type-tab"
      [class.on-blue]="typeUi() === 'historique'"
      (click)="setType('historique')"
    >
      <span>📋 Historique</span>
      <span class="tt-sub">Cotisations & solidarité</span>
    </button>
  </div>"""

marker = "      <span class=\"tt-sub\">Épargne mois · {{ formatFcfa(regleMois().montantMin) }}–{{ formatFcfa(regleMois().montantMax) }}</span>\n    </button>\n  </motion>"
marker = marker.replace("</motion>", "</div>")
t = t.replace(
    "      <span class=\"tt-sub\">Épargne mois · {{ formatFcfa(regleMois().montantMin) }}–{{ formatFcfa(regleMois().montantMax) }}</span>\n    </button>\n  </motion>".replace("</motion>", "</motion>"),
    "      <span class=\"tt-sub\">Épargne mois · {{ formatFcfa(regleMois().montantMin) }}–{{ formatFcfa(regleMois().montantMax) }}</span>\n    </button>\n" + tab_insert.replace("\n  </div>", "\n"),
)
# simpler: insert before closing type-tabs
anchor = "    </button>\n  </div>\n\n  <div class=\"main-grid\">"
if anchor not in t:
    anchor = "    </button>\n  </div>\n\n  <div class=\"main-grid\">"
insert_btn = """    <button
      type="button"
      class="type-tab"
      [class.on-blue]="typeUi() === 'historique'"
      (click)="setType('historique')"
    >
      <span>📋 Historique</span>
      <span class="tt-sub">Cotisations & solidarité</span>
    </button>
"""
if "[class.on-blue]" not in t:
    t = t.replace(
        "      <span class=\"tt-sub\">Épargne mois · {{ formatFcfa(regleMois().montantMin) }}–{{ formatFcfa(regleMois().montantMax) }}</span>\n    </button>\n  </motion>".replace("motion", "div"),
        "      <span class=\"tt-sub\">Épargne mois · {{ formatFcfa(regleMois().montantMin) }}–{{ formatFcfa(regleMois().montantMax) }}</span>\n    </button>\n" + insert_btn,
        1,
    )

hist_panel = """
  @if (typeUi() === 'historique') {
    <div class="historique-panel">
      <div class="card">
        <motion class="card-pad">
          <div class="card-h historique-h">
            <span class="card-title">📋 Toutes les cotisations & solidarités</span>
            <button type="button" class="btn-refresh" (click)="chargerHistorique()">Actualiser</button>
          </motion>
          <div class="filters-row historique-filters">
            <select class="filter-select" [value]="filtreHistType()" (change)="onFiltreHistType($event)">
              <option value="tous">Tous les types</option>
              <option value="hebdo">Cotisation hebdo</option>
              <option value="mois">Cotisation mois</option>
              <option value="solidarite">Solidarité</option>
            </select>
            <input
              type="search"
              class="filter-search"
              placeholder="Membre, période, observation…"
              [value]="filtreHistRecherche()"
              (input)="onFiltreHistRecherche($event)"
              aria-label="Filtrer l'historique"
            />
            <span class="filtre-count">{{ historiqueFiltre().length }} ligne(s)</span>
          </motion>
          <div class="hist-summary">
            <div class="hist-sum green">
              <div class="n">{{ historiqueTotaux().cotisations }}</motion>
              <div class="lbl">Cotisations</div>
              <div class="sub">{{ formatFcfa(historiqueTotaux().montantCotisations) }} F</motion>
            </motion>
            <div class="hist-sum or">
              <div class="n">{{ historiqueTotaux().solidarite }}</motion>
              <div class="lbl">Solidarités</div>
              <div class="sub">{{ formatFcfa(historiqueTotaux().montantSolidarite) }} F</motion>
            </motion>
          </motion>
          <div class="table-wrap">
            <table class="hist-table">
              <thead>
                <tr>
                  <th>Membre</th>
                  <th>Type</th>
                  <th>Période</th>
                  <th>Montant</th>
                  <th>Date</th>
                </tr>
              </thead>
              <tbody>
                @if (historiqueLoading()) {
                  <tr>
                    <td colspan="5" class="empty-filtre">Chargement…</td>
                  </tr>
                } @else {
                  @for (h of historiqueFiltre(); track h.ligneId) {
                    <tr>
                      <td>
                        <div class="h-name">{{ h.membreNom }}</div>
                        <div class="h-code">{{ h.codeMembre }}</div>
                      </td>
                      <td>
                        @if (h.typeLigne === 'HEBDO') {
                          <span class="badge b-green">Hebdo</span>
                        } @else if (h.typeLigne === 'MOIS') {
                          <span class="badge b-pi">Mois</span>
                        } @else {
                          <span class="badge b-or">Solidarité</span>
                        }
                      </td>
                      <td class="h-periode">{{ h.periode }}</td>
                      <td class="h-amt cr-c">+{{ formatFcfa(h.montant) }} F</td>
                      <td class="h-date">{{ h.dateLabel }}</td>
                    </tr>
                  } @empty {
                    <tr>
                      <td colspan="5" class="empty-filtre">Aucune opération enregistrée.</td>
                    </tr>
                  }
                }
              </tbody>
            </table>
          </motion>
        </motion>
      </motion>
    </motion>
  } @else {
    <div class="main-grid">
""".replace("<motion", "<motion").replace("</motion>", "</motion>")
hist_panel = hist_panel.replace("<motion", "<motion")
for _ in range(20):
    hist_panel = hist_panel.replace("<motion", "<div").replace("</motion>", "</div>")

if "@if (typeUi() === 'historique')" not in t:
    t = t.replace("\n  <div class=\"main-grid\">\n", hist_panel, 1)
    # close @else before form end - before last closing divs
    close_else = "\n  }\n"
    anchor_end = "  </div>\n  </form>\n</div>"
    if anchor_end in t and close_else not in t.split("main-grid")[1][:50]:
        t = t.replace(
            "    </div>\n  </motion>\n  </form>".replace("motion", "motion"),
            "    </motion>\n  }\n  </form>".replace("motion", "motion"),
        )
        t = t.replace("    </div>\n  </div>\n  </form>", "    </div>\n  </motion>\n  }\n  </form>".replace("motion", "motion"))
        # find pattern: right-panel closes then main-grid
        old_end = "    </div>\n  </div>\n  </form>"
        if old_end in t:
            t = t.replace(old_end, "    </div>\n  </div>\n  }\n  </form>", 1)

tout_voir = '<span class="card-action" tabindex="0" role="button">Tout voir</span>'
if "setType('historique')" not in t:
    t = t.replace(
        tout_voir,
        '<span class="card-action" tabindex="0" role="button" (click)="setType(\'historique\')">Tout voir</span>',
        1,
    )

p.write_text(t, encoding="utf-8", newline="\n")
print("done", "[class.on-blue]" in p.read_text(encoding="utf-8"))
