#!/usr/bin/env python3
"""Restaure l'UTF-8 dans membres-page.component.html."""
from __future__ import annotations

import re
from pathlib import Path

PATH = Path(__file__).resolve().parents[1] / "frontend/src/app/features/membres/membres-page.component.html"


def fix_mojibake(text: str) -> str:
    """UTF-8 lu comme CP1252 puis ré-enregistré en UTF-8."""
    try:
        return text.encode("cp1252").decode("utf-8")
    except (UnicodeDecodeError, UnicodeEncodeError):
        try:
            return text.encode("latin-1").decode("utf-8")
        except (UnicodeDecodeError, UnicodeEncodeError):
            return text


def main() -> None:
    raw = PATH.read_text(encoding="utf-8")
    if raw.startswith("\ufeff"):
        raw = raw[1:]
    if raw.startswith("?<"):
        raw = raw[1:]

    text = fix_mojibake(raw)

    # Apostrophes / ellipses résiduelles
    text = text.replace("\ufffd", "")
    text = re.sub(r"membres\?+", "membres…", text)
    text = re.sub(r"membre\?+", "membre…", text)
    text = re.sub(r"Création\?+", "Création…", text)
    text = re.sub(r"Téléchargement\?+", "Téléchargement…", text)
    text = re.sub(r"en cours\?+", "en cours…", text)
    text = re.sub(r"Suppression\?+", "Suppression…", text)
    text = re.sub(r"d\?+import", "d'import", text)
    text = re.sub(r"l\?+email", "l'email", text)
    text = re.sub(r"s\?+il", "s'il", text)
    text = text.replace("NÂ°", "N°")

    # Emojis : motifs connus (fichier d'origine)
    replacements = [
        (r"[\?\ufffd\uFFFD]+ Exporter", "📊 Exporter"),
        (r"[\?\ufffd\uFFFD]+ Importer des membres", "📥 Importer des membres"),
        (r"[\?\ufffd\uFFFD]+ Importer", "📥 Importer"),
        (r"[\?\ufffd\uFFFD]+ Modèle CSV", "⬇ Modèle CSV"),
        (r"[\?\ufffd\uFFFD]+ Modèle Excel", "⬇ Modèle Excel"),
        (r"⬇ Modèle(?! CSV| Excel)", "⬇ Modèle"),
        (r"[\?\ufffd\uFFFD]+ Tableau", "📋 Tableau"),
        (r"[\?\ufffd\uFFFD]+ Cartes", "🏇 Cartes"),
        (r"[\?\ufffd\uFFFD]+ Bureau", "👑 Bureau"),
        (r"[\?\ufffd\uFFFD]+ Simples", "👤 Simples"),
        (r"[\?\ufffd\uFFFD]+ Suspendus", "⚠ Suspendus"),
        (r"[\?\ufffd\uFFFD]+ Membres du bureau", "👑 Membres du bureau"),
        (r"aria-hidden=\"true\">[\?\ufffd\uFFFD]+</div>\s*\n\s*<p class=\"empty-state-title\"", 'aria-hidden="true">👥</motion>\n        <p class="empty-state-title"'),
        (r"bc-crown\" aria-hidden=\"true\">[\?\ufffd\uFFFD]+</div>", 'bc-crown" aria-hidden="true">👑</div>'),
        (r"[\?\ufffd\uFFFD]+ Informations personnelles", "👤 Informations personnelles"),
        (r"[\?\ufffd\uFFFD]+ Poste &amp; Cat", "👑 Poste &amp; Cat"),
        (r"[\?\ufffd\uFFFD]+ Comptes à créer", "🏦 Comptes à créer"),
        (r"[\?\ufffd\uFFFD]+ Comptes de l", "📂 Comptes de l"),
        (r"[\?\ufffd\uFFFD]+ Nouveau type", "➕ Nouveau type"),
        (r"<div class=\"po-ico\">[\?\ufffd\uFFFD]+</div>", '<div class="po-ico">👤</div>', 1),
        (r"import-file-name\">[\?\ufffd\uFFFD]+ ", 'import-file-name">📄 '),
        (r"Fermer\">[\?\ufffd\uFFFD]+</button>", 'Fermer">×</button>'),
        (r"class=\"act-btn view\"[^>]*>[\?\ufffd\uFFFD]+", 'class="act-btn view" aria-label="Voir la fiche">👁'),
        (r"class=\"act-btn del\"[^>]*>[\?\ufffd\uFFFD]+", 'class="act-btn del" aria-label="Supprimer">🗑'),
        (r"page-btn[^>]*disabled\]=\"page\(\) <= 1\"[^>]*>[\?\ufffd\uFFFD]+</button>", 'page-btn" (click)="goPage(page() - 1)" [disabled]="page() <= 1">‹</button>'),
        (r"page-btn[^>]*goPage\(page\(\) \+ 1\)[^>]*>[\?\ufffd\uFFFD]+</button>", 'page-btn" (click)="goPage(page() + 1)" [disabled]="page() >= totalPages()">›</button>'),
        (r"sc-zero\">[\?\ufffd\uFFFD]+</span>", 'sc-zero">—</span>'),
        (r"â‚¬", "€"),
    ]
    for item in replacements:
        if len(item) == 3:
            pat, repl, count = item
            text = re.sub(pat, repl, text, count=count)
        else:
            pat, repl = item
            text = re.sub(pat, repl, text)

    # Poste icons (ordre des boutons dans le formulaire)
    poste_icons = ["👤", "👑", "🎖", "📝", "📋", "💼", "🔍"]
    for ico in poste_icons:
        text = re.sub(
            r'<div class="po-ico">[\?\ufffd\uFFFD👤👑🎖📝📋💼🔍]*</motion>',
            f'<div class="po-ico">{ico}</motion>',
            text,
            count=1,
        )
        text = text.replace("</motion>", "</div>").replace("<motion ", "<motion ").replace("<motion>", "<div>")

    text = text.replace("</motion>", "</div>")
    text = re.sub(r"<motion(\s|>)", r"<div\1", text)

    # Comptes icons
    for pat, ico in [
        (r'<div class="ca-ico">[\?\ufffd📅]*</div>\s*\n\s*<motion class="ca-lbl c-g1">Épargne hebdo', '<motion class="ca-ico">📅</div>\n            <div class="ca-lbl c-g1">Épargne hebdo'),
    ]:
        text = re.sub(pat, ico, text, count=1)

    ca_icons = ["📅", "📆", "🤝", "⚠", "🚫"]
    for ico in ca_icons:
        text = re.sub(r'<div class="ca-ico">[\?\ufffd\w]*</div>', f'<div class="ca-ico">{ico}</div>', text, count=1)

    PATH.write_text(text, encoding="utf-8", newline="\n")
    assert "Président" in text and "Ã" not in text
    print("OK — encodage corrigé:", PATH)


if __name__ == "__main__":
    main()
