import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { SessionApiService } from './core/api/session-api.service';
import { DeskReturnService } from './core/trader/desk-return.service';
import { TraderContextService, type UserScope } from './core/trader/trader-context.service';

type DeskWorkspace = 'term' | 'oncall';
type DeskQueue = 'received' | 'assigned' | 'executed';

interface DeskNavContext {
  workspace: DeskWorkspace;
  queue: DeskQueue;
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly user = inject(TraderContextService);
  protected readonly deskReturn = inject(DeskReturnService);
  protected readonly sessionApi = inject(SessionApiService);
  readonly router = inject(Router);

  private scopeSwitchPending = false;

  /**
   * Queue tabs use the active workspace from the URL path, or — on order details —
   * ?ws=&queue= so Term + Assigned stay highlighted when drilling into an order.
   */
  protected queuePrefix(): string {
    const ctx = this.resolveDeskNavContext();
    const ws = ctx?.workspace ?? 'oncall';
    return '/' + ws;
  }

  /** Highlight workspace tab from path (/term/…) or order-details query (?ws=&queue=). */
  protected workspaceActive(workspace: DeskWorkspace): boolean {
    const ctx = this.resolveDeskNavContext();
    if (!ctx) {
      return false;
    }
    return ctx.workspace === workspace;
  }

  /** Highlight Received / Assigned / Executed from path or order-details query. */
  protected queueActive(segment: DeskQueue): boolean {
    const ctx = this.resolveDeskNavContext();
    if (!ctx) {
      return false;
    }
    return ctx.queue === segment;
  }

  protected showDeskNav(): boolean {
    return this.user.isTrader() && !this.router.url.startsWith('/settings');
  }

  protected showDeskEntry(): boolean {
    return this.user.isTrader();
  }

  protected deskReturnUrl(): string {
    return this.deskReturn.getReturnUrl();
  }

  protected deskLinkActive(): boolean {
    return this.deskReturn.isDeskPath(this.router.url);
  }

  protected settingsLinkActive(): boolean {
    return this.deskReturn.isSettingsPath(this.router.url);
  }

  protected activeScopeKey(): string {
    return this.user.scopeKey(this.user.activeScope());
  }

  protected scopeLabel(scope: UserScope): string {
    const role =
      scope.role === 'TRADER' ? 'Trader' : 'Client rep';
    return `${scope.legalEntityCode} · ${role}`;
  }

  protected onUserBlur(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.user.setUserId(value);
  }

  protected onScopeChange(event: Event): void {
    if (this.scopeSwitchPending) {
      return;
    }
    const select = event.target as HTMLSelectElement;
    const requested = this.parseScopeKey(select.value);
    if (!requested) {
      return;
    }
    const current = this.user.activeScope();
    if (
      current.legalEntityCode === requested.legalEntityCode &&
      current.role === requested.role
    ) {
      return;
    }

    this.scopeSwitchPending = true;
    const previousKey = this.user.scopeKey(current);
    this.sessionApi.reScope(this.user.userId(), requested).subscribe({
      next: (response) => {
        this.user.bindActiveScope({
          legalEntityCode: response.legalEntityCode,
          role: response.role,
        });
        this.scopeSwitchPending = false;
        if (this.user.isClientRepresentative()) {
          void this.router.navigateByUrl('/settings');
        }
      },
      error: () => {
        this.scopeSwitchPending = false;
        select.value = previousKey;
      },
    });
  }

  private parseScopeKey(key: string): UserScope | null {
    const [legalEntityCode, role] = key.split(':');
    if (!legalEntityCode || (role !== 'TRADER' && role !== 'CLIENT_REPRESENTATIVE')) {
      return null;
    }
    return { legalEntityCode, role };
  }

  private resolveDeskNavContext(): DeskNavContext | null {
    const url = this.router.url;
    const pathOnly = url.split('?')[0];
    const segments = pathOnly.split('/').filter((s) => s.length > 0);

    if (segments.length >= 2) {
      const workspace = segments[0];
      const queue = segments[1];
      if (
        (workspace === 'term' || workspace === 'oncall') &&
        (queue === 'received' || queue === 'assigned' || queue === 'executed')
      ) {
        return { workspace, queue };
      }
    }

    if (segments[0] === 'orders' && segments.length >= 2) {
      const tree = this.router.parseUrl(url);
      const ws = tree.queryParams['ws'];
      const queue = tree.queryParams['queue'];
      if (
        (ws === 'term' || ws === 'oncall') &&
        (queue === 'received' || queue === 'assigned' || queue === 'executed')
      ) {
        return { workspace: ws, queue };
      }
      return null;
    }

    return null;
  }
}
