package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.InstitutionClosedToNewBusinessException;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.OrderOperation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("fast")
class NewBusinessPolicyTest {

    private static final Institution OPEN = new Institution("HSBC-01", "HSBC", true);
    private static final Institution CLOSED = new Institution("HSBC-01", "HSBC", false);

    @Test
    void subscriptionAndIncreaseAddExposure() {
        assertThat(NewBusinessPolicy.addsExposure(OrderOperation.SUBSCRIPTION)).isTrue();
        assertThat(NewBusinessPolicy.addsExposure(OrderOperation.INCREASE)).isTrue();
    }

    @Test
    void decreaseAndRedemptionDoNotAddExposure() {
        assertThat(NewBusinessPolicy.addsExposure(OrderOperation.DECREASE)).isFalse();
        assertThat(NewBusinessPolicy.addsExposure(OrderOperation.REDEMPTION)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = OrderOperation.class, names = {"SUBSCRIPTION", "INCREASE"})
    void exposureAddingOperationOnClosedInstitutionIsRefused(OrderOperation operation) {
        assertThatThrownBy(() -> NewBusinessPolicy.requireOpenForNewBusiness(CLOSED, operation))
                .isInstanceOf(InstitutionClosedToNewBusinessException.class)
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("closed to new business")
                .hasMessageContaining("HSBC-01");
    }

    @ParameterizedTest
    @EnumSource(value = OrderOperation.class, names = {"DECREASE", "REDEMPTION"})
    void reducingOperationOnClosedInstitutionIsAllowed(OrderOperation operation) {
        assertThatCode(() -> NewBusinessPolicy.requireOpenForNewBusiness(CLOSED, operation))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(OrderOperation.class)
    void everyOperationOnOpenInstitutionIsAllowed(OrderOperation operation) {
        assertThatCode(() -> NewBusinessPolicy.requireOpenForNewBusiness(OPEN, operation))
                .doesNotThrowAnyException();
    }
}
