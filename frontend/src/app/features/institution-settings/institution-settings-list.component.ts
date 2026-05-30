import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  Institution,
  InstitutionSettingsApiService,
} from '../../core/api/institution-settings-api.service';
import { DeskReturnService } from '../../core/trader/desk-return.service';
import { TraderContextService } from '../../core/trader/trader-context.service';

@Component({
  selector: 'app-institution-settings-list',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="settings-panel">
      <nav class="settings-toolbar">
        <a [routerLink]="deskReturn.getReturnUrl()" class="settings-back">← Back to desk</a>
      </nav>

      <header class="settings-header">
        <h1>Institutions</h1>
        <a routerLink="/settings/institutions/new" class="btn-primary">Onboard institution</a>
      </header>

      @if (error()) {
        <p class="settings-error" role="alert">{{ error() }}</p>
      }

      @if (loading()) {
        <p class="settings-state">Loading…</p>
      } @else if (institutions().length === 0) {
        <p class="settings-state">
          No institutions onboarded. Execute is blocked until you add at least one.
        </p>
      } @else {
        <div class="settings-table-wrap">
          <table class="mmx-table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Display name</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (i of institutions(); track i.institutionCode) {
                <tr>
                  <td class="mono">{{ i.institutionCode }}</td>
                  <td>{{ i.displayName }}</td>
                  <td>
                    <span
                      class="status-badge"
                      [class.status-badge--active]="i.active"
                      [class.status-badge--inactive]="!i.active"
                    >
                      {{ i.active ? 'Active' : 'Inactive' }}
                    </span>
                  </td>
                  <td>
                    <a [routerLink]="['/settings/institutions', i.institutionCode]">View</a>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
})
export class InstitutionSettingsListComponent implements OnInit {
  protected readonly deskReturn = inject(DeskReturnService);

  private readonly api = inject(InstitutionSettingsApiService);
  private readonly trader = inject(TraderContextService);

  readonly institutions = signal<Institution[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.list(this.trader.traderId()).subscribe({
      next: (list) => {
        this.institutions.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.message ?? 'Failed to load institutions');
        this.loading.set(false);
      },
    });
  }
}
