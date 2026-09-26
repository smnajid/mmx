package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;

/** PostgreSQL-only partial unique indexes for routed-order correlation. */
public class V23__order_routing_partial_indexes extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        DatabaseMetaData meta = connection.getMetaData();
        if (!"PostgreSQL".equals(meta.getDatabaseProductName())) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            st.execute(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_money_market_order_routing_id_client
                        ON money_market_order (routing_id)
                        WHERE originating_legal_entity_code IS NULL AND routing_id IS NOT NULL
                    """);
            st.execute(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_money_market_order_routing_id_hub
                        ON money_market_order (routing_id)
                        WHERE originating_legal_entity_code IS NOT NULL AND routing_id IS NOT NULL
                    """);
        }
    }

    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }
}
