import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { DeskReturnService } from './core/trader/desk-return.service';
import { TraderContextService } from './core/trader/trader-context.service';

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
  protected readonly trader = inject(TraderContextService);
  protected readonly deskReturn = inject(DeskReturnService);
  readonly router = inject(Router);

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
    return !this.router.url.startsWith('/settings');
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

  protected onTraderBlur(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.trader.setTraderId(value);
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
