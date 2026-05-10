import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ExecutedOrderListComponent } from '../executed-orders/executed-order-list.component';

/** Term workspace routed shell for executed-not-accounted (see `ExecutedOrderListComponent`). */
@Component({
  selector: 'mmx-term-executed-order-list',
  standalone: true,
  imports: [ExecutedOrderListComponent],
  template: ` <mmx-executed-order-list [fixedWorkspace]="'term'" /> `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TermExecutedOrderListComponent {}
