package com.mmx.order.config;

import com.mmx.order.application.port.in.UpdateCounterpartyAccountsUseCase;
import com.mmx.order.application.service.UpdateCounterpartyAccountsService;
import com.mmx.order.domain.model.Institution;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** An account change commits with its institution export row. */
@Service
@Primary
public class TransactionalUpdateCounterpartyAccountsUseCase implements UpdateCounterpartyAccountsUseCase {

    private final UpdateCounterpartyAccountsService delegate;

    public TransactionalUpdateCounterpartyAccountsUseCase(UpdateCounterpartyAccountsService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public Institution update(UpdateCommand command) {
        return delegate.update(command);
    }
}
