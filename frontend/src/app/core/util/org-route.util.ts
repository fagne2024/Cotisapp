import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Id org depuis l’URL (shell) — indispensable si SUPERADMIN ou JWT sans organisationId. */
export function organisationCouranteId(route: ActivatedRoute, auth: AuthService): number | null {
  let r: ActivatedRoute | null = route;
  while (r) {
    const raw = r.snapshot.paramMap.get('orgId');
    if (raw != null && raw !== '') {
      const n = Number(raw);
      if (!Number.isNaN(n)) {
        return n;
      }
    }
    r = r.parent;
  }
  const fromAuth = auth.currentOrgId();
  return fromAuth != null ? fromAuth : null;
}
