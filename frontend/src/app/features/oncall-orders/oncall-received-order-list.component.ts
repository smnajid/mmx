import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ReceivedOrderListComponent } from '../received-orders/received-order-list.component';

/** OnCall workspace routed shell for Received queue. */
@Component({
  selector: 'mmx-oncall-received-order-list',
  standalone: true,
  imports: [ReceivedOrderListComponent],
  template: ` <mmx-received-order-list [fixedWorkspace]="'oncall'" /> `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnCallReceivedOrderListComponent {}
