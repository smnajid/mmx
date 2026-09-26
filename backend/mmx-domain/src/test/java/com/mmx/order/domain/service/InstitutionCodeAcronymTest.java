package com.mmx.order.domain.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
@Tag("fast")

class InstitutionCodeAcronymTest {

    @Test
    void deriveAcronym_singleWordShort() {
        assertThat(InstitutionCodeAcronym.deriveAcronym("HSBC")).isEqualTo("HSBC");
        assertThat(InstitutionCodeAcronym.deriveAcronym("BankCo")).isEqualTo("BANKCO");
    }

    @Test
    void deriveAcronym_multiWordCapsAtSix() {
        assertThat(InstitutionCodeAcronym.deriveAcronym("Bank Co International")).isEqualTo("BCI");
        assertThat(InstitutionCodeAcronym.deriveAcronym("The Royal Bank of Scotland")).isEqualTo("TRBOS");
    }

    @Test
    void deriveAcronym_fallbackInst() {
        assertThat(InstitutionCodeAcronym.deriveAcronym("   ")).isEqualTo("INST");
        assertThat(InstitutionCodeAcronym.deriveAcronym("!!!")).isEqualTo("INST");
    }
}
