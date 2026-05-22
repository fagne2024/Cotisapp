import { Router } from '@angular/router';

/** Préfixe de route selon le shell (GIE ou superadmin). */
export function buildOrgRoute(
  router: Router,
  orgId: number,
  segments: string[]
): (string | number)[] {
  const url = router.url;
  if (url.includes('/superadmin/org/')) {
    return ['/superadmin', 'org', orgId, ...segments];
  }
  return ['/organisations', orgId, ...segments];
}
