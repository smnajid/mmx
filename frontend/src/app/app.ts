import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { TraderContextService } from './core/trader/trader-context.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly trader = inject(TraderContextService);
  readonly router = inject(Router);

  /** Prefix for Received / Assigned / Executed links; defaults to On-call when path is ambiguous (e.g. order details). */
  protected queuePrefix(): string {
    const path = this.router.url.split('?')[0];
    return path.startsWith('/term') ? '/term' : '/oncall';
  }

  /** Highlight workspace tab for any sub-route (received / assigned / executed). */
  protected workspaceActive(workspace: 'oncall' | 'term'): boolean {
    const path = this.router.url.split('?')[0];
    return workspace === 'term' ? path.startsWith('/term') : path.startsWith('/oncall');
  }

  /** Highlight queue tab when that segment is the leaf path (not order details). */
  protected queueActive(segment: 'received' | 'assigned' | 'executed'): boolean {
    const path = this.router.url.split('?')[0];
    return path.endsWith('/' + segment);
  }

  protected onTraderBlur(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.trader.setTraderId(value);
  }
}
