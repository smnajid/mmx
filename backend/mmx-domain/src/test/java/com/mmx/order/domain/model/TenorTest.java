package com.mmx.order.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenorTest {

    @Test
    void fromCode_returnsMatchingTenor() {
        assertThat(Tenor.fromCode("3M")).contains(Tenor._3M);
        assertThat(Tenor.fromCode("1W")).contains(Tenor._1W);
    }

    @Test
    void fromCode_rejectsUnknown() {
        assertThat(Tenor.fromCode("9M")).isEmpty();
        assertThat(Tenor.fromCode(null)).isEmpty();
        assertThat(Tenor.fromCode("  ")).isEmpty();
    }
}
