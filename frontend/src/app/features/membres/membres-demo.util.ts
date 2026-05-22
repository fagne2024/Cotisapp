import { MembreDto } from '../../core/services/membre.service';

/** Trois fiches factices (codes GDR alignés sur `membres-poste.util`) en attendant les API. */
const DEMO_MEMBRES: MembreDto[] = [
  {
    id: 1,
    codeMembre: 'GDR-003',
    nom: 'Diallo',
    prenom: 'Fatoumata',
    nomComplet: 'Fatoumata Diallo',
    actif: true,
    dateCreation: '2024-01-15T10:00:00.000Z',
    telephone: '+223 70 12 34 56',
  },
  {
    id: 2,
    codeMembre: 'GDR-007',
    nom: 'Sow',
    prenom: 'Amadou',
    nomComplet: 'Amadou Sow',
    actif: true,
    dateCreation: '2024-02-01T10:00:00.000Z',
    telephone: '+223 76 00 11 22',
  },
  {
    id: 3,
    codeMembre: 'GDR-012',
    nom: 'Traoré',
    prenom: 'Moussa',
    nomComplet: 'Moussa Traoré',
    actif: true,
    dateCreation: '2024-03-10T10:00:00.000Z',
    telephone: '+223 65 44 33 22',
  },
];

export function listeMembresDemo(): MembreDto[] {
  return DEMO_MEMBRES.map((m) => ({ ...m }));
}

/** Fiche détail si GET membre échoue : 3 profils connus ou placeholder minimal pour tout autre id. */
export function membreDemoSiApiAbsente(membreId: number): MembreDto {
  const found = DEMO_MEMBRES.find((m) => m.id === membreId);
  if (found) return { ...found };
  const code = `GDR-${String(membreId).padStart(3, '0')}`;
  return {
    id: membreId,
    codeMembre: code,
    nom: 'Démo',
    prenom: 'Membre',
    nomComplet: `Membre démo #${membreId}`,
    actif: true,
    dateCreation: '2024-06-01T08:00:00.000Z',
    telephone: null,
  };
}
