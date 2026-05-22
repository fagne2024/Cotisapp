import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-changer-mot-de-passe-initial',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './changer-mot-de-passe-initial.component.html',
  styleUrl: './changer-mot-de-passe-initial.component.scss',
})
export class ChangerMotDePasseInitialComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  readonly loading = signal(false);
  readonly error = signal('');
  readonly showPassword = signal(false);
  readonly showConfirm = signal(false);

  readonly form = this.fb.nonNullable.group(
    {
      nouveauMotDePasse: ['', [Validators.required, Validators.minLength(8)]],
      confirmationMotDePasse: ['', Validators.required],
    },
    {
      validators: (g) => {
        const a = g.get('nouveauMotDePasse')?.value;
        const b = g.get('confirmationMotDePasse')?.value;
        return a && b && a !== b ? { mismatch: true } : null;
      },
    }
  );

  togglePassword(): void {
    this.showPassword.update((v) => !v);
  }

  toggleConfirm(): void {
    this.showConfirm.update((v) => !v);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { nouveauMotDePasse, confirmationMotDePasse } = this.form.getRawValue();
    if (nouveauMotDePasse === 'Passer123') {
      this.error.set('Choisissez un mot de passe différent du mot de passe temporaire.');
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.auth.changerMotDePasseInitial(nouveauMotDePasse, confirmationMotDePasse).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.auth.redirectAfterLogin(res);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Impossible de mettre à jour le mot de passe.');
      },
    });
  }

}
