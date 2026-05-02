import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TraderContextService } from './core/trader/trader-context.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly trader = inject(TraderContextService);

  protected onTraderBlur(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.trader.setTraderId(value);
  }
}
