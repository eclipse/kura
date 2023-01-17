package org.eclipse.kura.core.db;

import static java.util.Objects.isNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.kura.KuraStoreException;
import org.eclipse.kura.db.H2DbService;
import org.eclipse.kura.db.H2DbService.ConnectionCallable;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.wire.WireRecord;
import org.eclipse.kura.wire.store.provider.WireRecordStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class H2DbWireRecordStoreImpl implements WireRecordStore {

    private static final Logger logger = LoggerFactory.getLogger(H2DbWireRecordStoreImpl.class);

    private static final String COLUMN_NAME = "COLUMN_NAME";

    private static final String DATA_TYPE = "DATA_TYPE";

    private static final String SQL_ADD_COLUMN = "ALTER TABLE {0} ADD COLUMN {1} {2};";

    private static final String SQL_CREATE_TABLE = "CREATE TABLE IF NOT EXISTS {0} (ID BIGINT GENERATED BY DEFAULT "
            + "AS IDENTITY(START WITH 1 INCREMENT BY 1) PRIMARY KEY, TIMESTAMP BIGINT);";

    private static final String SQL_CREATE_TABLE_INDEX = "CREATE INDEX IF NOT EXISTS {0} ON {1} {2};";

    private static final String SQL_ROW_COUNT_TABLE = "SELECT COUNT(*) FROM {0};";

    private static final String SQL_DELETE_RANGE_TABLE = "DELETE FROM {0} WHERE ID IN (SELECT ID FROM {0} ORDER BY ID ASC LIMIT {1});";

    private static final String SQL_DROP_COLUMN = "ALTER TABLE {0} DROP COLUMN {1};";

    private static final String SQL_INSERT_RECORD = "INSERT INTO {0} ({1}) VALUES ({2});";

    private static final String SQL_TRUNCATE_TABLE = "TRUNCATE TABLE {0};";

    private final String tableName;
    private final String sanitizedTableName;
    private final H2DbService h2DbService;

    public H2DbWireRecordStoreImpl(final H2DbService h2DbService, final String tableName) throws KuraStoreException {
        this.tableName = tableName;
        this.sanitizedTableName = sanitizeSqlTableAndColumnName(tableName);
        this.h2DbService = h2DbService;

        withConnection(c -> {
            this.createTable(c);
            return null;
        });
    }

    @Override
    public synchronized void truncate(final int noOfRecordsToKeep) throws KuraStoreException {

        withConnection(c -> {
            if (noOfRecordsToKeep == 0) {
                logger.info("Truncating table {}...", sanitizedTableName);
                execute(c, MessageFormat.format(SQL_TRUNCATE_TABLE, sanitizedTableName));
            } else {
                final int tableSize = getTableSize(c);
                final int deleteCount = Math.max(0, tableSize - noOfRecordsToKeep);

                if (deleteCount == 0) {
                    return null;
                }

                logger.info("Partially emptying table {}", sanitizedTableName);
                execute(c, MessageFormat.format(SQL_DELETE_RANGE_TABLE, sanitizedTableName, deleteCount));
            }

            return null;
        });

    }

    @Override
    public synchronized int getSize() throws KuraStoreException {
        return withConnection(this::getTableSize);
    }

    @Override
    public void insertRecords(final List<WireRecord> records) throws KuraStoreException {
        withConnection(c -> {
            for (final WireRecord r : records) {
                try {
                    tryStoreRecord(c, r);
                } catch (final SQLException e) {
                    logger.info("Insertion failed, reconciling table and columns");
                    createTable(c);
                    createColumns(c, r);
                    tryStoreRecord(c, r);
                }
            }

            return null;
        });
    }

    @Override
    public void close() {
        // nothing to shutdown
    }

    private void createTable(final Connection c) throws SQLException {
        execute(c, MessageFormat.format(SQL_CREATE_TABLE, sanitizedTableName));
        execute(c,
                MessageFormat.format(SQL_CREATE_TABLE_INDEX, sanitizeSqlTableAndColumnName(tableName + "_TIMESTAMP"),
                        sanitizedTableName,
                        "(TIMESTAMP DESC)"));
    }

    private void createColumns(final Connection c, final WireRecord wireRecord) throws SQLException {

        final Map<String, Integer> columns = new HashMap<>();

        final String catalog = c.getCatalog();
        final DatabaseMetaData dbMetaData = c.getMetaData();
        try (final ResultSet rsColumns = dbMetaData.getColumns(catalog, null, tableName, null)) {
            // map the columns
            while (rsColumns.next()) {
                final String colName = rsColumns.getString(COLUMN_NAME);
                final String sqlColName = sanitizeSqlTableAndColumnName(colName);
                final int colType = rsColumns.getInt(DATA_TYPE);
                columns.put(sqlColName, colType);
            }
        }

        for (Entry<String, TypedValue<?>> entry : wireRecord.getProperties().entrySet()) {
            final String sqlColName = sanitizeSqlTableAndColumnName(entry.getKey());
            final Integer sqlColType = columns.get(sqlColName);
            final JdbcType jdbcType = JdbcType.forValue(entry.getValue().getValue());
            if (isNull(sqlColType)) {
                // add column
                execute(c,
                        MessageFormat.format(SQL_ADD_COLUMN, sanitizedTableName, sqlColName, jdbcType.stringType));
            } else if (sqlColType != jdbcType.sqlType) {
                // drop old column and add new one
                execute(c, MessageFormat.format(SQL_DROP_COLUMN, sanitizedTableName, sqlColName));
                execute(c,
                        MessageFormat.format(SQL_ADD_COLUMN, sanitizedTableName, sqlColName, jdbcType.stringType));
            }
        }
    }

    private static class JdbcType {
        private final int sqlType;
        private final String stringType;

        JdbcType(int jdbcType, String stringType) {
            this.sqlType = jdbcType;
            this.stringType = stringType;
        }

        static JdbcType forValue(final Object value) {
            if (value instanceof String) {
                return new JdbcType(Types.VARCHAR, "VARCHAR(102400)");
            } else if (value instanceof Integer) {
                return new JdbcType(Types.INTEGER, "INTEGER");
            } else if (value instanceof Long) {
                return new JdbcType(Types.BIGINT, "BIGINT");
            } else if (value instanceof Boolean) {
                return new JdbcType(Types.BOOLEAN, "BOOLEAN");
            } else if (value instanceof Double) {
                return new JdbcType(Types.DOUBLE, "DOUBLE");
            } else if (value instanceof Float) {
                return new JdbcType(Types.FLOAT, "FLOAT");
            } else if (value instanceof byte[]) {
                return new JdbcType(Types.BLOB, "BLOB");
            }

            throw new IllegalStateException("Unsupported value type " + value.getClass());
        }
    }

    private void tryStoreRecord(Connection connection,
            final WireRecord wireRecord)
            throws SQLException {

        final String insertQuery = buildInsertQuerySql(wireRecord.getProperties());

        final long timestamp = System.currentTimeMillis();

        logger.debug("Storing data into table {}...", sanitizedTableName);

        try (final PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
            stmt.setLong(1, timestamp);

            int i = 2;

            for (Entry<String, TypedValue<?>> entry : wireRecord.getProperties().entrySet()) {

                setParameterValue(stmt, i, entry.getValue().getValue());

                i++;
            }

            stmt.execute();
            connection.commit();
            logger.debug("Stored typed value");
        }

    }

    private String buildInsertQuerySql(final Map<String, TypedValue<?>> properties) {
        final StringBuilder sbCols = new StringBuilder();
        final StringBuilder sbVals = new StringBuilder();

        sbCols.append("TIMESTAMP");
        sbVals.append("?");

        for (final Entry<String, TypedValue<?>> entry : properties.entrySet()) {
            final String sqlColName = sanitizeSqlTableAndColumnName(entry.getKey());
            sbCols.append(", ").append(sqlColName);
            sbVals.append(", ?");
        }

        return MessageFormat.format(SQL_INSERT_RECORD,
                sanitizedTableName, sbCols.toString(), sbVals.toString());
    }

    private void setParameterValue(final PreparedStatement stmt, final int index, final Object value)
            throws SQLException {
        if (value instanceof String) {
            stmt.setString(index, (String) value);
        } else if (value instanceof Integer) {
            stmt.setInt(index, (int) value);
        } else if (value instanceof Double) {
            stmt.setDouble(index, (double) value);
        } else if (value instanceof Boolean) {
            stmt.setBoolean(index, (boolean) value);
        } else if (value instanceof Float) {
            stmt.setFloat(index, (float) value);
        } else if (value instanceof Long) {
            stmt.setLong(index, (long) value);
        } else if (value instanceof byte[]) {
            byte[] byteArrayValue = (byte[]) value;
            InputStream is = new ByteArrayInputStream(byteArrayValue);
            stmt.setBlob(index, is, byteArrayValue.length);
        } else {
            logger.warn("Unsupported value type {}", value.getClass());
        }
    }

    private int getTableSize(final Connection c) throws SQLException {
        try (final Statement stmt = c.createStatement();
                final ResultSet rset = stmt
                        .executeQuery(MessageFormat.format(SQL_ROW_COUNT_TABLE, sanitizedTableName))) {
            rset.next();
            return rset.getInt(1);
        }
    }

    private void execute(final Connection c, final String sql, final Object... params)
            throws SQLException {
        try (final PreparedStatement stmt = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                setParameterValue(stmt, 1 + i, params[i]);
            }
            stmt.execute();
            c.commit();
        }
    }

    private <T> T withConnection(final ConnectionCallable<T> callable) throws KuraStoreException {
        try {
            return h2DbService.withConnection(callable);
        } catch (Exception e) {
            throw new KuraStoreException(e, null);
        }
    }

    private String sanitizeSqlTableAndColumnName(final String string) {
        final String sanitizedName = string.replace("\"", "\"\"");
        return "\"" + sanitizedName + "\"";
    }

}
