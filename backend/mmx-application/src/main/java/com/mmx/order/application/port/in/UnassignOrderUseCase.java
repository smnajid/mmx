package com.mmx.order.application.port.in;

import com.mmx.order.application.command.UnassignOrderCommand;
import com.mmx.order.domain.model.MoneyMarketOrder;

public interface UnassignOrderUseCase {

    MoneyMarketOrder unassign(UnassignOrderCommand command);
}
