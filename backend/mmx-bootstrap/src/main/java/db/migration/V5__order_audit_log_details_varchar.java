package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;

/**
 * Hibernate binds optional audit {@code details} as varchar; PostgreSQL rejects varchar parameters into
 * JSONB. Normalize the column to a string type on PostgreSQL; use a simple ALTER for H2 (integration tests).
 */
public class V5__order_audit_log_details_varchar extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        DatabaseMetaData meta = connection.getMetaData();
        String product = meta.getDatabaseProductName();
        try (Statement st = connection.createStatement()) {
            if ("PostgreSQL".equals(product)) {
                st.execute(
                        "ALTER TABLE order_audit_log ALTER COLUMN details TYPE VARCHAR(10000) USING (details::text)");
            } else {
                st.execute("ALTER TABLE order_audit_log ALTER COLUMN details VARCHAR(10000)");
            }
        }
    }

    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }
}
