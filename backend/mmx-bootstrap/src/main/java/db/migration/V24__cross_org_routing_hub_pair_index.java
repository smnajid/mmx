package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;

/** Spec-mandated composite partial unique index on (originating_legal_entity_code, routing_id) for routed hub-side orders. */
public class V24__cross_org_routing_hub_pair_index extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        DatabaseMetaData meta = connection.getMetaData();
        if (!"PostgreSQL".equals(meta.getDatabaseProductName())) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            st.execute("DROP INDEX IF EXISTS uq_money_market_order_routing_id_hub");
            st.execute(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_money_market_order_routing_hub_pair
                        ON money_market_order (originating_legal_entity_code, routing_id)
                        WHERE originating_legal_entity_code IS NOT NULL
                    """);
        }
    }

    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }
}
