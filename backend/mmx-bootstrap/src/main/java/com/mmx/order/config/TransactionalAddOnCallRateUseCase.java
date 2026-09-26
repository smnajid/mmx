package com.mmx.order.config;

import com.mmx.order.application.command.AddOnCallRateCommand;
import com.mmx.order.application.port.in.AddOnCallRateUseCase;
import com.mmx.order.application.service.AddOnCallRateService;
import com.mmx.order.domain.model.OnCallRateSegment;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class TransactionalAddOnCallRateUseCase implements AddOnCallRateUseCase {

    private final AddOnCallRateService delegate;

    public TransactionalAddOnCallRateUseCase(AddOnCallRateService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public OnCallRateSegment add(AddOnCallRateCommand command) {
        return delegate.add(command);
    }
}
