package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ManageInstitutionSettingsUseCase;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.domain.exception.InstitutionSuffixOverflowException;
import com.mmx.order.domain.exception.InvalidInstitutionException;
import com.mmx.order.domain.model.Institution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@Tag("fast")

@ExtendWith(MockitoExtension.class)
class ManageInstitutionSettingsServiceTest {

    @Mock
    InstitutionRepository repository;

    ManageInstitutionSettingsService subject;

    @BeforeEach
    void setUp() {
        subject = new ManageInstitutionSettingsService(repository);
    }

    @Test
    void onboard_firstHsbc_assignsHsbc01() {
        when(repository.maxSuffixForAcronym("HSBC")).thenReturn(0);
        when(repository.save(any(Institution.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Institution created = subject.onboard(new ManageInstitutionSettingsUseCase.OnboardCommand("HSBC"));

        assertThat(created.getInstitutionCode()).isEqualTo("HSBC-01");
        assertThat(created.getDisplayName()).isEqualTo("HSBC");
        assertThat(created.isActive()).isTrue();
    }

    @Test
    void onboard_secondHsbc_assignsHsbc02() {
        when(repository.maxSuffixForAcronym("HSBC")).thenReturn(1);
        when(repository.save(any(Institution.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Institution created = subject.onboard(new ManageInstitutionSettingsUseCase.OnboardCommand("HSBC"));

        assertThat(created.getInstitutionCode()).isEqualTo("HSBC-02");
    }

    @Test
    void onboard_multiWord_assignsBci01() {
        when(repository.maxSuffixForAcronym("BCI")).thenReturn(0);
        when(repository.save(any(Institution.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Institution created =
                subject.onboard(new ManageInstitutionSettingsUseCase.OnboardCommand("Bank Co International"));

        assertThat(created.getInstitutionCode()).isEqualTo("BCI-01");
    }

    @Test
    void onboard_blankName_rejected() {
        assertThatThrownBy(() -> subject.onboard(new ManageInstitutionSettingsUseCase.OnboardCommand("  ")))
                .isInstanceOf(InvalidInstitutionException.class);
    }

    @Test
    void onboard_suffixOverflow_rejected() {
        when(repository.maxSuffixForAcronym("INST")).thenReturn(99);

        assertThatThrownBy(() -> subject.onboard(new ManageInstitutionSettingsUseCase.OnboardCommand("!!!")))
                .isInstanceOf(InstitutionSuffixOverflowException.class);
    }

    @Test
    void deactivate_persistsInactive() {
        Institution active = new Institution("HSBC-01", "HSBC", true);
        when(repository.findByInstitutionCode("HSBC-01")).thenReturn(Optional.of(active));
        ArgumentCaptor<Institution> captor = ArgumentCaptor.forClass(Institution.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        Institution result = subject.deactivate("HSBC-01");

        assertThat(result.isActive()).isFalse();
        verify(repository).save(any(Institution.class));
    }
}
