package com.mmx.order.config;

import com.mmx.order.application.command.CancelOrderCommand;
import com.mmx.order.application.command.RejectOrderCommand;
import com.mmx.order.application.port.in.CancelOrderUseCase;
import com.mmx.order.application.port.in.RejectOrderUseCase;
import com.mmx.order.application.service.OrderLifecycleService;
import com.mmx.order.domain.model.MoneyMarketOrder;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wraps {@link OrderLifecycleService} so hub terminal transitions and client-side propagation share one DB
 * transaction.
 */
@Service
@Primary
public class TransactionalOrderLifecycleUseCase implements CancelOrderUseCase, RejectOrderUseCase {

    private final OrderLifecycleService delegate;

    public TransactionalOrderLifecycleUseCase(OrderLifecycleService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public MoneyMarketOrder cancel(CancelOrderCommand command) {
        return delegate.cancel(command);
    }

    @Override
    @Transactional
    public MoneyMarketOrder reject(RejectOrderCommand command) {
        return delegate.reject(command);
    }
}
