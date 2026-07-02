package com.mmx.order.adapter.in.rest;

import com.mmx.order.application.port.in.ManageGlobalAccountsUseCase;
import com.mmx.order.application.port.out.ScopeContextProvider;
import com.mmx.order.domain.model.GlobalAccount;
import com.mmx.order.domain.model.LegalEntityCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/global-accounts")
public class GlobalAccountsController {

    private final ManageGlobalAccountsUseCase manageGlobalAccountsUseCase;
    private final ScopeContextProvider scopeContextProvider;

    public GlobalAccountsController(
            ManageGlobalAccountsUseCase manageGlobalAccountsUseCase,
            ScopeContextProvider scopeContextProvider) {
        this.manageGlobalAccountsUseCase = manageGlobalAccountsUseCase;
        this.scopeContextProvider = scopeContextProvider;
    }

    @GetMapping
    public ResponseEntity<List<GlobalAccountResponse>> list() {
        List<GlobalAccountResponse> body =
                manageGlobalAccountsUseCase.listForHub(scopeContextProvider.requireActiveScope()).stream()
                        .map(GlobalAccountResponse::from)
                        .toList();
        return ResponseEntity.ok(body);
    }

    @PutMapping
    public ResponseEntity<GlobalAccountResponse> upsert(@RequestBody UpsertGlobalAccountRequest request) {
        GlobalAccount saved =
                manageGlobalAccountsUseCase.upsert(
                        new ManageGlobalAccountsUseCase.UpsertCommand(
                                scopeContextProvider.requireActiveScope(),
                                new LegalEntityCode(request.clientLegalEntityCode()),
                                request.currency(),
                                request.accountRef()));
        return ResponseEntity.status(HttpStatus.OK).body(GlobalAccountResponse.from(saved));
    }

    public record UpsertGlobalAccountRequest(
            String clientLegalEntityCode, String currency, String accountRef) {}

    public record GlobalAccountResponse(
            String clientLegalEntityCode,
            String hubLegalEntityCode,
            String currency,
            String accountRef) {

        static GlobalAccountResponse from(GlobalAccount account) {
            return new GlobalAccountResponse(
                    account.clientLegalEntityCode().value(),
                    account.hubLegalEntityCode().value(),
                    account.currency(),
                    account.accountRef());
        }
    }
}
