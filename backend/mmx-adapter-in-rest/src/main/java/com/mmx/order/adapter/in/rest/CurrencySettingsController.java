package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.generated.settings.api.CurrencySettingsApi;
import com.mmx.order.adapter.in.rest.generated.settings.model.ManagedCurrencyResponse;
import com.mmx.order.adapter.in.rest.generated.settings.model.OnboardCurrencyRequest;
import com.mmx.order.adapter.in.rest.generated.settings.model.UpdateCurrencyRulesRequest;
import com.mmx.order.adapter.in.rest.mapper.CurrencySettingsRestMapper;
import com.mmx.order.application.port.in.ManageCurrencySettingsUseCase;
import com.mmx.order.domain.model.ManagedCurrency;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CurrencySettingsController implements CurrencySettingsApi {

    private final ManageCurrencySettingsUseCase manageCurrencySettingsUseCase;
    private final CurrencySettingsRestMapper mapper;

    public CurrencySettingsController(
            ManageCurrencySettingsUseCase manageCurrencySettingsUseCase, CurrencySettingsRestMapper mapper) {
        this.manageCurrencySettingsUseCase = manageCurrencySettingsUseCase;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<ManagedCurrencyResponse>> listManagedCurrencies(String xTraderId) {
        List<ManagedCurrencyResponse> body =
                manageCurrencySettingsUseCase.listAll().stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<ManagedCurrencyResponse> getManagedCurrency(String xTraderId, String code) {
        return ResponseEntity.ok(mapper.toResponse(manageCurrencySettingsUseCase.getByCode(code)));
    }

    @Override
    public ResponseEntity<ManagedCurrencyResponse> onboardManagedCurrency(
            String xTraderId, OnboardCurrencyRequest onboardCurrencyRequest) {
        ManagedCurrency created =
                manageCurrencySettingsUseCase.onboard(mapper.toOnboardCommand(onboardCurrencyRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
    }

    @Override
    public ResponseEntity<ManagedCurrencyResponse> updateManagedCurrencyRules(
            String xTraderId, String code, UpdateCurrencyRulesRequest updateCurrencyRulesRequest) {
        ManagedCurrency updated =
                manageCurrencySettingsUseCase.updateRules(code, mapper.toUpdateCommand(updateCurrencyRulesRequest));
        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<ManagedCurrencyResponse> disableManagedCurrency(String xTraderId, String code) {
        return ResponseEntity.ok(mapper.toResponse(manageCurrencySettingsUseCase.disable(code)));
    }

    @Override
    public ResponseEntity<ManagedCurrencyResponse> enableManagedCurrency(String xTraderId, String code) {
        return ResponseEntity.ok(mapper.toResponse(manageCurrencySettingsUseCase.enable(code)));
    }
}
