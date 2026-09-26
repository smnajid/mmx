package com.mmx.order.config;

import com.mmx.order.application.port.in.ConfirmOnCallRateUseCase;
import com.mmx.order.application.service.ConfirmOnCallRateService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Primary
public class TransactionalConfirmOnCallRateUseCase implements ConfirmOnCallRateUseCase {

    private final ConfirmOnCallRateService delegate;

    public TransactionalConfirmOnCallRateUseCase(ConfirmOnCallRateService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public Outcome confirm(UUID segmentId) {
        return delegate.confirm(segmentId);
    }
}
