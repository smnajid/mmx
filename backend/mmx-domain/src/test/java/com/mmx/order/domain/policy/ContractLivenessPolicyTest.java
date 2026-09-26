package com.mmx.order.domain.policy;

import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
@Tag("fast")

class ContractLivenessPolicyTest {

    private static final LocalDate VALUE_DATE = LocalDate.of(2026, 6, 1);

    private ContractLivenessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ContractLivenessPolicy();
    }

    @Nested
    class OnCallLiveness {

        @Test
        void liveWhenNoNonCancelledRedemption() {
            assertThat(policy.isOnCallLive(false)).isTrue();
        }

        @Test
        void notLiveWhenNonCancelledRedemptionExists() {
            assertThat(policy.isOnCallLive(true)).isFalse();
        }
    }

    @Nested
    class TermLiveness {

        @Test
        void liveWhenEndDateIsInFuture() {
            LocalDate today = LocalDate.of(2026, 6, 13);

            assertThat(policy.isTermLive(VALUE_DATE, Tenor._3M, today)).isTrue();
        }

        @Test
        void notLiveOnMaturityDate() {
            LocalDate today = LocalDate.of(2026, 9, 1);

            assertThat(policy.isTermLive(VALUE_DATE, Tenor._3M, today)).isFalse();
        }

        @Test
        void notLiveAfterMaturity() {
            LocalDate today = LocalDate.of(2026, 10, 1);

            assertThat(policy.isTermLive(VALUE_DATE, Tenor._3M, today)).isFalse();
        }
    }

    @Nested
    class TenorEndDate {

        @Test
        void maps1W() {
            assertThat(policy.termEndDate(VALUE_DATE, Tenor._1W)).isEqualTo(LocalDate.of(2026, 6, 8));
        }

        @Test
        void maps2W() {
            assertThat(policy.termEndDate(VALUE_DATE, Tenor._2W)).isEqualTo(LocalDate.of(2026, 6, 15));
        }

        @Test
        void maps1M() {
            assertThat(policy.termEndDate(VALUE_DATE, Tenor._1M)).isEqualTo(LocalDate.of(2026, 7, 1));
        }

        @Test
        void maps3M() {
            assertThat(policy.termEndDate(VALUE_DATE, Tenor._3M)).isEqualTo(LocalDate.of(2026, 9, 1));
        }

        @Test
        void maps6M() {
            assertThat(policy.termEndDate(VALUE_DATE, Tenor._6M)).isEqualTo(LocalDate.of(2026, 12, 1));
        }

        @Test
        void maps1Y() {
            assertThat(policy.termEndDate(VALUE_DATE, Tenor._1Y)).isEqualTo(LocalDate.of(2027, 6, 1));
        }
    }
}
