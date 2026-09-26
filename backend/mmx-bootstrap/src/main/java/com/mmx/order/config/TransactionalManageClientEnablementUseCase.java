package com.mmx.order.config;

import com.mmx.order.application.port.in.ManageClientEnablementUseCase;
import com.mmx.order.application.service.ManageClientEnablementService;
import com.mmx.order.domain.model.Institution;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** A per-currency client-enablement replacement is atomic. */
@Service
@Primary
public class TransactionalManageClientEnablementUseCase implements ManageClientEnablementUseCase {

    private final ManageClientEnablementService delegate;

    public TransactionalManageClientEnablementUseCase(ManageClientEnablementService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public Institution update(UpdateCommand command) {
        return delegate.update(command);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CurrencyEnablement> enablementsOf(String institutionCode) {
        return delegate.enablementsOf(institutionCode);
    }
}
