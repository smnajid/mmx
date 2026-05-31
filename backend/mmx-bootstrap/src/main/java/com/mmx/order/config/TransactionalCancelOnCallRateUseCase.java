package com.mmx.order.config;

import com.mmx.order.application.command.CancelOnCallRateCommand;
import com.mmx.order.application.port.in.CancelOnCallRateUseCase;
import com.mmx.order.application.service.CancelOnCallRateService;
import com.mmx.order.domain.model.OnCallRateSegment;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class TransactionalCancelOnCallRateUseCase implements CancelOnCallRateUseCase {

    private final CancelOnCallRateService delegate;

    public TransactionalCancelOnCallRateUseCase(CancelOnCallRateService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public OnCallRateSegment cancel(CancelOnCallRateCommand command) {
        return delegate.cancel(command);
    }
}
