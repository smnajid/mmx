package com.mmx.order.application.port.in;

import com.mmx.order.application.command.UpdateOrderCommand;
import com.mmx.order.domain.model.MoneyMarketOrder;

public interface UpdateAssignedOrderUseCase {

    MoneyMarketOrder update(UpdateOrderCommand command);
}
