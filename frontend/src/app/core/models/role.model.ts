export type Role = 'SUPERADMIN' | 'ADMIN_GIE' | 'MEMBRE';

export interface AuthUser {
  userId: number;
  email: string;
  nomComplet: string;
  role: Role;
  organisationId: number | null;
  organisationNom: string | null;
  membreId: number | null;
  mustChangePassword?: boolean;
  mustSetupTwoFactor?: boolean;
}

export interface CompteMembreLogin {
  membreId: number;
  organisationId: number;
  organisationNom: string;
  organisationCode: string;
  codeMembre: string;
  nomComplet: string;
}

export interface AuthResponse {
  token?: string;
  userId: number;
  email: string;
  nomComplet: string;
  role: Role;
  organisationId: number | null;
  organisationNom: string | null;
  membreId: number | null;
  mustChangePassword?: boolean;
  requiresTwoFactor?: boolean;
  twoFactorToken?: string;
  mustSetupTwoFactor?: boolean;
}
