package com.mmx.order.support;

import com.mmx.order.application.port.out.MmxUserRepository;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MmxUser;
import com.mmx.order.domain.model.MmxUserId;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.UserScope;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Seeds MMXUser records with LOC Trader scope for REST integration tests. */
@Component
@Profile("rest-test")
public class RestTestUserBootstrap implements ApplicationRunner {

    public static final String TRADER_IT_1 = "trader-it-1";
    public static final String TRADER_IT_2 = "trader-it-2";
    public static final String TRADER_IT_ORDER_CREATION = "trader-it-order-creation";
    public static final String TRADER_E2E_1 = "trader-e2e-1";
    public static final String TRADER_HANDOFF_IT_1 = "trader-handoff-it-1";
    public static final String TRADER_TERM_RATE_IT = "trader-it-term-rates";

    private static final UserScope LOC_TRADER =
            new UserScope(new LegalEntityCode(RestTestTenancy.DEFAULT_LEGAL_ENTITY), Role.TRADER);

    private final MmxUserRepository mmxUserRepository;

    public RestTestUserBootstrap(MmxUserRepository mmxUserRepository) {
        this.mmxUserRepository = mmxUserRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedTrader(TRADER_IT_1);
        seedTrader(TRADER_IT_2);
        seedTrader(TRADER_IT_ORDER_CREATION);
        seedTrader(TRADER_E2E_1);
        seedTrader(TRADER_HANDOFF_IT_1);
        seedTrader(TRADER_TERM_RATE_IT);
    }

    private void seedTrader(String userId) {
        mmxUserRepository.save(MmxUser.create(new MmxUserId(userId), Set.of(LOC_TRADER)));
    }
}
