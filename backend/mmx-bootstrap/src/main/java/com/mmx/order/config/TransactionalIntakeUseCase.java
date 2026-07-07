package com.mmx.order.config;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.application.port.in.IntakeUseCase;
import com.mmx.order.application.service.IntakeService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class TransactionalIntakeUseCase implements IntakeUseCase {

    private final IntakeService delegate;

    public TransactionalIntakeUseCase(IntakeService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public Result receive(ReceiveOrderCommand command) {
        return delegate.receive(command);
    }
}
