package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;

/** PostgreSQL-only partial unique index: at most one PENDING segment per curve point. */
public class V15__oncall_rate_segment_pending_index extends BaseJavaMigration {

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
                    CREATE UNIQUE INDEX uq_oncall_rate_segment_one_pending
                        ON oncall_rate_segment (institution_code, currency, notice_period)
                        WHERE status = 'PENDING_CONFIRMATION'
                    """);
        }
    }

    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }
}
