package com.mmx.order.domain.model;

import com.mmx.order.domain.exception.UnauthorizedUserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MMXUser identity and scoping")
class MmxUserIdentityTest {

    private static final MmxUserId USER_ID = new MmxUserId("alice");
    private static final UserScope PAR_CLIENT =
            new UserScope(new LegalEntityCode("PAR"), Role.CLIENT_REPRESENTATIVE);
    private static final UserScope SIN_CLIENT =
            new UserScope(new LegalEntityCode("SIN"), Role.CLIENT_REPRESENTATIVE);
    private static final UserScope LOC_TRADER = new UserScope(new LegalEntityCode("LOC"), Role.TRADER);

    @Test
    void mmxUser_holdsMultipleScopes() {
        MmxUser user = MmxUser.create(USER_ID, Set.of(PAR_CLIENT, SIN_CLIENT));

        assertThat(user.getScopes()).containsExactlyInAnyOrder(PAR_CLIENT, SIN_CLIENT);
    }

    @Test
    void userWithNoScopes_isUnauthorised() {
        MmxUser user = MmxUser.create(USER_ID, Set.of());

        assertThat(user.isAuthorised()).isFalse();
        assertThatThrownBy(() -> user.reScope(PAR_CLIENT))
                .isInstanceOf(UnauthorizedUserException.class);
    }

    @Test
    void reScope_validatesHeldPair() {
        MmxUser user = MmxUser.withActiveScope(USER_ID, Set.of(PAR_CLIENT, SIN_CLIENT), PAR_CLIENT);

        user.reScope(SIN_CLIENT);
        assertThat(user.getActiveScope()).isEqualTo(SIN_CLIENT);

        assertThatThrownBy(() -> user.reScope(LOC_TRADER))
                .isInstanceOf(UnauthorizedUserException.class);
        assertThat(user.getActiveScope()).isEqualTo(SIN_CLIENT);
    }
}
