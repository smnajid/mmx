package com.mmx.order.application.service;

import com.mmx.order.application.exception.CurrencyNotFoundException;
import com.mmx.order.application.port.in.ManageCurrencySettingsUseCase;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.domain.exception.DuplicateManagedCurrencyException;
import com.mmx.order.domain.model.ManagedCurrency;

import java.util.List;

public final class ManageCurrencySettingsService implements ManageCurrencySettingsUseCase {

    private final ManagedCurrencyRepository repository;

    public ManageCurrencySettingsService(ManagedCurrencyRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ManagedCurrency> listAll() {
        return repository.findAll();
    }

    @Override
    public ManagedCurrency getByCode(String code) {
        String normalized = ManagedCurrency.validateCode(code);
        return repository
                .findByCode(normalized)
                .orElseThrow(() -> new CurrencyNotFoundException(normalized));
    }

    @Override
    public ManagedCurrency onboard(OnboardCommand command) {
        String code = ManagedCurrency.validateCode(command.code());
        if (repository.existsByCode(code)) {
            throw new DuplicateManagedCurrencyException(code);
        }
        ManagedCurrency created =
                new ManagedCurrency(
                        code,
                        true,
                        command.minSubscriptionAmount(),
                        command.minIncreaseDecreaseAmount(),
                        command.enabledTenors(),
                        command.enabledNoticePeriods());
        return repository.save(created);
    }

    @Override
    public ManagedCurrency updateRules(String code, UpdateRulesCommand command) {
        ManagedCurrency existing = getByCode(code);
        ManagedCurrency updated =
                existing.withRules(
                        command.minSubscriptionAmount(),
                        command.minIncreaseDecreaseAmount(),
                        command.enabledTenors(),
                        command.enabledNoticePeriods());
        return repository.save(updated);
    }

    @Override
    public ManagedCurrency disable(String code) {
        ManagedCurrency existing = getByCode(code);
        return repository.save(existing.withActive(false));
    }

    @Override
    public ManagedCurrency enable(String code) {
        ManagedCurrency existing = getByCode(code);
        return repository.save(existing.withActive(true));
    }
}
