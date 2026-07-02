package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.entity.GlobalAccountEntity;
import com.mmx.order.adapter.out.persistence.repository.SpringDataGlobalAccountRepository;
import com.mmx.order.application.port.out.GlobalAccountDirectory;
import com.mmx.order.domain.model.GlobalAccount;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.Optional;

public class JpaGlobalAccountDirectory implements GlobalAccountDirectory {

    private final SpringDataGlobalAccountRepository repository;

    public JpaGlobalAccountDirectory(SpringDataGlobalAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<GlobalAccount> resolve(
            LegalEntityCode clientLegalEntityCode,
            LegalEntityCode hubLegalEntityCode,
            String currency) {
        return repository
                .findById(
                        new com.mmx.order.adapter.out.persistence.entity.GlobalAccountId(
                                clientLegalEntityCode.value(),
                                hubLegalEntityCode.value(),
                                currency))
                .map(this::toDomain);
    }

    private GlobalAccount toDomain(GlobalAccountEntity entity) {
        return new GlobalAccount(
                new LegalEntityCode(entity.getClientLegalEntityCode()),
                new LegalEntityCode(entity.getHubLegalEntityCode()),
                entity.getCurrency(),
                entity.getAccountRef());
    }
}
