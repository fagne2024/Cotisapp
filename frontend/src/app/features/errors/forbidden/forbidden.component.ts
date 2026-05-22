import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="error-container">
      <div class="error-content">
        <h1 class="error-code">403</h1>
        <p class="error-message">Accès refusé — vous n'avez pas les droits pour cette page.</p>
        <a routerLink="/login" class="error-link">↩ Retour à la connexion</a>
      </div>
    </div>
  `,
  styleUrl: './forbidden.component.scss',
})
export class ForbiddenComponent {}
