import { ActivatedRoute, ParamMap, Router } from '@angular/router';

/** Lit un paramètre enum ; retourne `fallback` si absent ou invalide. */
export function qpEnum<T extends string>(
  pm: ParamMap,
  key: string,
  allowed: readonly T[],
  fallback: T
): T {
  const v = pm.get(key);
  return v && (allowed as readonly string[]).includes(v) ? (v as T) : fallback;
}

/** Lit un paramètre texte (recherche), tronqué si besoin. */
export function qpString(pm: ParamMap, key: string, maxLen = 200): string {
  const v = pm.get(key)?.trim() ?? '';
  return v.length > maxLen ? v.slice(0, maxLen) : v;
}

/**
 * Construit le patch pour `queryParams` : `null` supprime une clé ;
 * les valeurs égales à `defaults` sont aussi supprimées pour garder l’URL lisible.
 */
export function qpPatch(
  values: Record<string, string | null | undefined>,
  defaults: Record<string, string> = {}
): Record<string, string | null> {
  const out: Record<string, string | null> = {};
  for (const [k, v] of Object.entries(values)) {
    if (v == null || v === '' || v === defaults[k]) {
      out[k] = null;
    } else {
      out[k] = v;
    }
  }
  return out;
}

/** Évite les allers-retours URL ↔ signaux lors de la lecture des query params. */
export class FilterQueryNav {
  private fromUrl = false;
  private debounceId?: ReturnType<typeof setTimeout>;

  runSync(fn: () => void): void {
    this.fromUrl = true;
    try {
      fn();
    } finally {
      queueMicrotask(() => {
        this.fromUrl = false;
      });
    }
  }

  push(
    router: Router,
    route: ActivatedRoute,
    values: Record<string, string | null | undefined>,
    defaults: Record<string, string> = {},
    debounceMs = 0
  ): void {
    if (this.fromUrl) return;

    const navigate = () => {
      void router.navigate([], {
        relativeTo: route,
        queryParams: qpPatch(values, defaults),
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
    };

    if (debounceMs > 0) {
      clearTimeout(this.debounceId);
      this.debounceId = setTimeout(navigate, debounceMs);
    } else {
      navigate();
    }
  }

  destroy(): void {
    clearTimeout(this.debounceId);
  }
}
