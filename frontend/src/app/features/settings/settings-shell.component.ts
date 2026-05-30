import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-settings-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <nav class="settings-hub-nav" aria-label="Settings sections">
      <a routerLink="/settings/currencies" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: false }">
        Currencies
      </a>
      <a routerLink="/settings/institutions" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: false }">
        Institutions
      </a>
    </nav>
    <router-outlet />
  `,
  styles: `
    .settings-hub-nav {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
      margin-bottom: 1.25rem;
      padding-bottom: 0.75rem;
      border-bottom: 1px solid var(--mmx-border);
    }

    .settings-hub-nav a {
      font-family: var(--font-mono);
      font-size: 0.72rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      text-decoration: none;
      color: var(--mmx-text-muted);
      padding: 0.45rem 0.75rem;
      border-radius: 4px;
      border: 1px solid transparent;
    }

    .settings-hub-nav a:hover {
      color: var(--mmx-text);
      border-color: var(--mmx-border);
    }

    .settings-hub-nav a.active {
      color: var(--mmx-accent);
      border-color: var(--mmx-accent);
      background: var(--mmx-accent-dim);
    }
  `,
})
export class SettingsShellComponent {
  readonly router = inject(Router);
}
