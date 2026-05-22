import { Component, inject, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { JournalNavigationService } from './core/services/journal-navigation.service';
import { ModalConfirmComponent } from './shared/components/modal-confirm/modal-confirm.component';
import { NotificationPopupComponent } from './shared/components/notification-popup/notification-popup.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NotificationPopupComponent, ModalConfirmComponent],
  template: '<router-outlet /><app-notification-popup /><app-modal-confirm />',
})
export class AppComponent implements OnInit {
  private readonly journalNav = inject(JournalNavigationService);

  ngOnInit(): void {
    this.journalNav.demarrer();
  }
}
