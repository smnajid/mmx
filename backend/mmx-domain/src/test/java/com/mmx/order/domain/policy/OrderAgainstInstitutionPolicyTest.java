package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.InstitutionClosedToNewBusinessException;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.MissingCounterpartyAccountException;
import com.mmx.order.domain.model.CounterpartyAccounts;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("fast")
class OrderAgainstInstitutionPolicyTest {

    private final OrderAgainstInstitutionPolicy policy = new OrderAgainstInstitutionPolicy();

    private static Institution hsbc(boolean active, CounterpartyAccounts accounts) {
        Institution institution =
                Institution.createNative("HSBC-01", "HSBC", new LegalEntityCode("LOC"), accounts);
        if (!active) {
            institution.deactivate();
        }
        return institution;
    }

    private static final CounterpartyAccounts BOTH = CounterpartyAccounts.of("LOC-HSBC-T", "LOC-HSBC-OC");

    @Test
    void validateCatalogNotEmpty_rejectsWhenEmpty() {
        assertThatThrownBy(() -> policy.validateCatalogNotEmpty(false))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("No institutions onboarded");
    }

    @Test
    void validateExecute_rejectsUnknownCode() {
        assertThatThrownBy(
                        () -> policy.validateExecute(
                                "NOPE-01", Optional.empty(), OrderOperation.SUBSCRIPTION, OrderType.TERM))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void validateExecute_rejectsSubscriptionOnClosedInstitution() {
        assertThatThrownBy(
                        () -> policy.validateExecute(
                                "HSBC-01",
                                Optional.of(hsbc(false, BOTH)),
                                OrderOperation.SUBSCRIPTION,
                                OrderType.TERM))
                .isInstanceOf(InstitutionClosedToNewBusinessException.class);
    }

    @Test
    void validateExecute_allowsRedemptionOnClosedInstitution() {
        assertThat(policy.validateExecute(
                        "HSBC-01", Optional.of(hsbc(false, BOTH)), OrderOperation.REDEMPTION, OrderType.ON_CALL))
                .isEqualTo("LOC-HSBC-OC");
    }

    @Test
    void validateExecute_rejectsMissingAccountForOrderType() {
        assertThatThrownBy(
                        () -> policy.validateExecute(
                                "HSBC-01",
                                Optional.of(hsbc(true, CounterpartyAccounts.of(null, "LOC-HSBC-OC"))),
                                OrderOperation.SUBSCRIPTION,
                                OrderType.TERM))
                .isInstanceOf(MissingCounterpartyAccountException.class);
    }

    @Test
    void validateExecute_acceptsOpenInstitutionAndReturnsAccountSnapshot() {
        assertThat(policy.validateExecute(
                        "HSBC-01", Optional.of(hsbc(true, BOTH)), OrderOperation.SUBSCRIPTION, OrderType.TERM))
                .isEqualTo("LOC-HSBC-T");
    }

    // --- refusal: the one rule every routing / accept / options call site applies ---

    @Test
    void refusal_isEmpty_forAnOpenInstitutionWithTheAccount() {
        assertThat(OrderAgainstInstitutionPolicy.refusal(hsbc(true, BOTH), OrderOperation.SUBSCRIPTION, OrderType.TERM))
                .isEmpty();
    }

    @Test
    void refusal_reportsClosedToNewBusiness_onlyForExposureAddingOperations() {
        Institution closed = hsbc(false, BOTH);

        assertThat(OrderAgainstInstitutionPolicy.refusal(closed, OrderOperation.INCREASE, OrderType.ON_CALL))
                .hasValueSatisfying(reason -> assertThat(reason).contains("closed to new business"));
        assertThat(OrderAgainstInstitutionPolicy.refusal(closed, OrderOperation.REDEMPTION, OrderType.ON_CALL)).isEmpty();
    }

    @Test
    void refusal_reportsTheMissingAccount_forEveryOperation() {
        Institution termOnly = hsbc(false, CounterpartyAccounts.of("LOC-HSBC-T", null));

        assertThat(OrderAgainstInstitutionPolicy.refusal(termOnly, OrderOperation.DECREASE, OrderType.ON_CALL))
                .hasValueSatisfying(reason -> assertThat(reason).contains("OnCall counterparty account"));
    }
}
