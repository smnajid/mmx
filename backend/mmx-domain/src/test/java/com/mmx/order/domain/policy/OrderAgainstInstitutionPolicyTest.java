package com.mmx.order.domain.policy;

import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.model.Institution;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderAgainstInstitutionPolicyTest {

    private final OrderAgainstInstitutionPolicy policy = new OrderAgainstInstitutionPolicy();

    @Test
    void validateCatalogNotEmpty_rejectsWhenEmpty() {
        assertThatThrownBy(() -> policy.validateCatalogNotEmpty(false))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("No institutions onboarded");
    }

    @Test
    void validateExecute_rejectsUnknownCode() {
        assertThatThrownBy(() -> policy.validateExecute("NOPE-01", Optional.empty()))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void validateExecute_rejectsInactive() {
        Institution inactive = new Institution("HSBC-01", "HSBC", false);
        assertThatThrownBy(() -> policy.validateExecute("HSBC-01", Optional.of(inactive)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void validateExecute_acceptsActive() {
        Institution active = new Institution("HSBC-01", "HSBC", true);
        assertThatCode(() -> policy.validateExecute("HSBC-01", Optional.of(active)))
                .doesNotThrowAnyException();
    }
}
