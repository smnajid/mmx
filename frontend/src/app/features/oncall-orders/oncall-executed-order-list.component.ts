import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ExecutedOrderListComponent } from '../executed-orders/executed-order-list.component';

/** OnCall workspace routed shell for executed-not-accounted (see `ExecutedOrderListComponent`). */
@Component({
  selector: 'mmx-oncall-executed-order-list',
  standalone: true,
  imports: [ExecutedOrderListComponent],
  template: ` <mmx-executed-order-list [fixedWorkspace]="'oncall'" /> `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnCallExecutedOrderListComponent {}
