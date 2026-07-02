package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.GlobalAccount;
import com.mmx.order.domain.model.LegalEntityCode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalAccountDirectoryTest {

    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");
    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");

    @Test
    void resolve_returns_configured_account() {
        FakeDirectory directory = new FakeDirectory();
        directory.put(new GlobalAccount(PAR, LOC, "EUR", "PAR-EUR-001"));

        Optional<GlobalAccount> resolved = directory.resolve(PAR, LOC, "EUR");

        assertThat(resolved).contains(new GlobalAccount(PAR, LOC, "EUR", "PAR-EUR-001"));
    }

    @Test
    void missing_tuple_signals_unresolved() {
        FakeDirectory directory = new FakeDirectory();

        assertThat(directory.resolve(PAR, LOC, "EUR")).isEmpty();
    }

    private static final class FakeDirectory implements GlobalAccountDirectory {

        private final Map<Key, GlobalAccount> accounts = new HashMap<>();

        void put(GlobalAccount account) {
            accounts.put(
                    new Key(
                            account.clientLegalEntityCode(),
                            account.hubLegalEntityCode(),
                            account.currency()),
                    account);
        }

        @Override
        public Optional<GlobalAccount> resolve(
                LegalEntityCode clientLegalEntityCode,
                LegalEntityCode hubLegalEntityCode,
                String currency) {
            return Optional.ofNullable(
                    accounts.get(new Key(clientLegalEntityCode, hubLegalEntityCode, currency)));
        }

        private record Key(LegalEntityCode client, LegalEntityCode hub, String currency) {}
    }
}
