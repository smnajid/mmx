package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.InvalidInstitutionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("fast")
class CounterpartyAccountsTest {

    @Test
    void blankAccountIsRejected() {
        assertThatThrownBy(() -> CounterpartyAccounts.of("   ", null))
                .isInstanceOf(InvalidInstitutionException.class)
                .hasMessageContaining("termCounterpartyAccount");
        assertThatThrownBy(() -> CounterpartyAccounts.of(null, ""))
                .isInstanceOf(InvalidInstitutionException.class)
                .hasMessageContaining("onCallCounterpartyAccount");
    }

    @Test
    void accountLongerThan34CharactersIsRejected() {
        assertThatThrownBy(() -> CounterpartyAccounts.of("X".repeat(35), null))
                .isInstanceOf(InvalidInstitutionException.class)
                .hasMessageContaining("34");
    }

    @Test
    void accountOf34CharactersIsAccepted() {
        assertThat(CounterpartyAccounts.of("X".repeat(34), null).term()).contains("X".repeat(34));
    }

    @Test
    void valuesAreTrimmed() {
        CounterpartyAccounts accounts = CounterpartyAccounts.of("  LOC-BNP-T ", " LOC-BNP-OC");

        assertThat(accounts.term()).contains("LOC-BNP-T");
        assertThat(accounts.onCall()).contains("LOC-BNP-OC");
    }

    @Test
    void termAndOnCallAreIndependentAndEachOptional() {
        CounterpartyAccounts termOnly = CounterpartyAccounts.of("T-1", null);
        CounterpartyAccounts onCallOnly = CounterpartyAccounts.of(null, "OC-1");

        assertThat(termOnly.accountFor(OrderType.TERM)).contains("T-1");
        assertThat(termOnly.accountFor(OrderType.ON_CALL)).isEmpty();
        assertThat(onCallOnly.accountFor(OrderType.TERM)).isEmpty();
        assertThat(onCallOnly.accountFor(OrderType.ON_CALL)).contains("OC-1");
        assertThat(CounterpartyAccounts.none().term()).isEmpty();
        assertThat(CounterpartyAccounts.none().onCall()).isEmpty();
    }

    @Test
    void equalValuesAreEqual() {
        assertThat(CounterpartyAccounts.of(" T ", null)).isEqualTo(CounterpartyAccounts.of("T", null));
    }
}
