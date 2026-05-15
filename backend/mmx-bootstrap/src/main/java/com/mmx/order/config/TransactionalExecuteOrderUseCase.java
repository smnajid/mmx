package com.mmx.order.config;

import com.mmx.order.application.command.ExecuteOrderCommand;
import com.mmx.order.application.port.in.ExecuteOrderUseCase;
import com.mmx.order.application.service.ExecuteOrderService;
import com.mmx.order.domain.model.MoneyMarketOrder;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wraps {@link ExecuteOrderService} so execute + transactional outbox scheduling share one DB transaction.
 */
@Service
@Primary
public class TransactionalExecuteOrderUseCase implements ExecuteOrderUseCase {

    private final ExecuteOrderService delegate;

    public TransactionalExecuteOrderUseCase(ExecuteOrderService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public MoneyMarketOrder execute(ExecuteOrderCommand command) {
        return delegate.execute(command);
    }
}
