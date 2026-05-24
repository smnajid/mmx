import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ReceivedOrderListComponent } from '../received-orders/received-order-list.component';

/** Term workspace routed shell for Received queue. */
@Component({
  selector: 'mmx-term-received-order-list',
  standalone: true,
  imports: [ReceivedOrderListComponent],
  template: ` <mmx-received-order-list [fixedWorkspace]="'term'" /> `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TermReceivedOrderListComponent {}
