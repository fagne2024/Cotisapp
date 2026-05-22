import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface TwoFactorStatus {
  enabled: boolean;
  pendingSetup: boolean;
}

export interface TwoFactorSetup {
  secret: string;
  otpAuthUrl: string;
  qrCodeDataUrl: string;
  issuer: string;
}

@Injectable({ providedIn: 'root' })
export class TwoFactorService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/me/2fa`;

  status() {
    return this.http.get<TwoFactorStatus>(`${this.base}/status`);
  }

  setup() {
    return this.http.post<TwoFactorSetup>(`${this.base}/setup`, {});
  }

  confirm(code: string) {
    return this.http.post<TwoFactorStatus>(`${this.base}/confirm`, { code });
  }

  annulerSetup() {
    return this.http.delete<void>(`${this.base}/setup`);
  }

  disable(motDePasse: string, code: string) {
    return this.http.post<TwoFactorStatus>(`${this.base}/disable`, { motDePasse, code });
  }
}
