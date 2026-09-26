package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.MissingCounterpartyAccountException;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.OrderType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("fast")
class CounterpartyAccountPolicyTest {

    private static Institution withAccounts(String term, String onCall) {
        return Institution.createNative(
                "BNP-01", "BNP", new LegalEntityCode("LOC"), CounterpartyAccounts.of(term, onCall));
    }

    @Test
    void termOrderReturnsTermAccountAsSnapshot() {
        assertThat(CounterpartyAccountPolicy.requireAccountFor(withAccounts("LOC-BNP-T", "LOC-BNP-OC"), OrderType.TERM))
                .isEqualTo("LOC-BNP-T");
    }

    @Test
    void onCallOrderReturnsOnCallAccountAsSnapshot() {
        assertThat(CounterpartyAccountPolicy.requireAccountFor(withAccounts("LOC-BNP-T", "LOC-BNP-OC"), OrderType.ON_CALL))
                .isEqualTo("LOC-BNP-OC");
    }

    @Test
    void termOrderWithoutTermAccountIsRefused() {
        assertThatThrownBy(() -> CounterpartyAccountPolicy.requireAccountFor(withAccounts(null, "LOC-BNP-OC"), OrderType.TERM))
                .isInstanceOf(MissingCounterpartyAccountException.class)
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("Term counterparty account")
                .hasMessageContaining("BNP-01");
    }

    @Test
    void onCallOrderWithoutOnCallAccountIsRefused() {
        assertThatThrownBy(() -> CounterpartyAccountPolicy.requireAccountFor(withAccounts("LOC-BNP-T", null), OrderType.ON_CALL))
                .isInstanceOf(MissingCounterpartyAccountException.class)
                .hasMessageContaining("OnCall counterparty account");
    }
}
