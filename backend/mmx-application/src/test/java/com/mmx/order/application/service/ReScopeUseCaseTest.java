package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ReScopeUseCase;
import com.mmx.order.application.port.in.ScopeContext;
import com.mmx.order.application.port.out.ActiveScopeStore;
import com.mmx.order.application.port.out.MmxUserRepository;
import com.mmx.order.domain.exception.UnauthorizedUserException;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MmxUser;
import com.mmx.order.domain.model.MmxUserId;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.UserScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReScopeUseCaseTest {

    private static final MmxUserId USER_ID = new MmxUserId("alice");
    private static final UserScope PAR_CLIENT =
            new UserScope(new LegalEntityCode("PAR"), Role.CLIENT_REPRESENTATIVE);
    private static final UserScope SIN_CLIENT =
            new UserScope(new LegalEntityCode("SIN"), Role.CLIENT_REPRESENTATIVE);
    private static final UserScope LOC_TRADER = new UserScope(new LegalEntityCode("LOC"), Role.TRADER);

    @Mock
    MmxUserRepository mmxUserRepository;

    @Mock
    ActiveScopeStore activeScopeStore;

    ReScopeUseCase subject;

    @BeforeEach
    void setUp() {
        subject = new ReScopeService(mmxUserRepository, activeScopeStore);
    }

    @Test
    void reScope_toHeldScope_succeedsAndRebinds() {
        MmxUser user = MmxUser.withActiveScope(USER_ID, Set.of(PAR_CLIENT, SIN_CLIENT), PAR_CLIENT);
        when(mmxUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(mmxUserRepository.save(any(MmxUser.class))).then(returnsFirstArg());

        ScopeContext result = subject.reScope(USER_ID, SIN_CLIENT.legalEntityCode(), SIN_CLIENT.role());

        assertThat(result).isEqualTo(ScopeContext.from(SIN_CLIENT));
        assertThat(user.getActiveScope()).isEqualTo(SIN_CLIENT);

        ArgumentCaptor<MmxUser> captor = ArgumentCaptor.forClass(MmxUser.class);
        verify(mmxUserRepository).save(captor.capture());
        assertThat(captor.getValue().getActiveScope()).isEqualTo(SIN_CLIENT);
        verify(activeScopeStore).setActiveScope(USER_ID, SIN_CLIENT);
    }

    @Test
    void reScope_toUnheldScope_rejectedAndActiveScopeUnchanged() {
        MmxUser user = MmxUser.withActiveScope(USER_ID, Set.of(PAR_CLIENT, SIN_CLIENT), PAR_CLIENT);
        when(mmxUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> subject.reScope(USER_ID, LOC_TRADER.legalEntityCode(), LOC_TRADER.role()))
                .isInstanceOf(UnauthorizedUserException.class);

        assertThat(user.getActiveScope()).isEqualTo(PAR_CLIENT);
        verify(mmxUserRepository, never()).save(any());
        verify(activeScopeStore, never()).setActiveScope(any(), any());
    }
}
