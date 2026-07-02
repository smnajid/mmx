package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.entity.GlobalAccountEntity;
import com.mmx.order.adapter.out.persistence.entity.GlobalAccountId;
import com.mmx.order.adapter.out.persistence.repository.SpringDataGlobalAccountRepository;
import com.mmx.order.application.port.out.GlobalAccountRepository;
import com.mmx.order.domain.model.GlobalAccount;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.List;
import java.util.Optional;

public class JpaGlobalAccountRepository implements GlobalAccountRepository {

    private final SpringDataGlobalAccountRepository springDataRepository;

    public JpaGlobalAccountRepository(SpringDataGlobalAccountRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public List<GlobalAccount> findAllByHub(LegalEntityCode hubLegalEntityCode) {
        return springDataRepository.findAll().stream()
                .filter(e -> hubLegalEntityCode.value().equals(e.getHubLegalEntityCode()))
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<GlobalAccount> findByKey(
            LegalEntityCode clientLegalEntityCode, LegalEntityCode hubLegalEntityCode, String currency) {
        return springDataRepository
                .findById(
                        new GlobalAccountId(
                                clientLegalEntityCode.value(), hubLegalEntityCode.value(), currency))
                .map(this::toDomain);
    }

    @Override
    public GlobalAccount save(GlobalAccount account) {
        GlobalAccountEntity entity =
                new GlobalAccountEntity(
                        account.clientLegalEntityCode().value(),
                        account.hubLegalEntityCode().value(),
                        account.currency(),
                        account.accountRef());
        return toDomain(springDataRepository.save(entity));
    }

    private GlobalAccount toDomain(GlobalAccountEntity entity) {
        return new GlobalAccount(
                new LegalEntityCode(entity.getClientLegalEntityCode()),
                new LegalEntityCode(entity.getHubLegalEntityCode()),
                entity.getCurrency(),
                entity.getAccountRef());
    }
}
