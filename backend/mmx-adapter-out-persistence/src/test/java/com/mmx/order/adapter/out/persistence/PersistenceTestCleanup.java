package com.mmx.order.adapter.out.persistence;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Deletes institutions and every row that references them, in FK-safe order. The test database is shared by
 * all classes in the module and surefire's class order differs between machines, so each class must clear
 * every dependent table, not only the ones it writes itself.
 */
public final class PersistenceTestCleanup {

    private PersistenceTestCleanup() {}

    public static void clearInstitutionsAndDependents(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM client_institution_enablement");
        jdbc.update("DELETE FROM delegated_institution_grant");
        jdbc.update("DELETE FROM oncall_rate_segment");
        jdbc.update("DELETE FROM term_rate");
        // Onboarded institutions first: they may reference a hub institution stored in the same table.
        jdbc.update("DELETE FROM institution WHERE hub_institution_code IS NOT NULL");
        jdbc.update("DELETE FROM institution");
    }
}
