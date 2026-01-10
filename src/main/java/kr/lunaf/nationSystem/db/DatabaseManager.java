package kr.lunaf.nationSystem.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import kr.lunaf.nationSystem.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

public class DatabaseManager {
    private final HikariDataSource dataSource;

    public DatabaseManager(DatabaseConfig config) {
        HikariConfig hikari = new HikariConfig();
        String type = normalizeType(config.type());
        String jdbcUrl = buildJdbcUrl(type, config);
        String driverClass = driverClassName(type);
        if (!tryLoad(driverClass)) {
            String fallback = type.equals("mysql") ? "mariadb" : "mysql";
            String fallbackDriver = driverClassName(fallback);
            if (tryLoad(fallbackDriver)) {
                type = fallback;
                jdbcUrl = buildJdbcUrl(type, config);
                driverClass = fallbackDriver;
            } else {
                throw new RuntimeException("Database driver not found: " + driverClass);
            }
        }
        hikari.setJdbcUrl(jdbcUrl);
        hikari.setDriverClassName(driverClass);
        hikari.setUsername(config.user());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.poolSize());
        hikari.setAutoCommit(false);
        hikari.addDataSourceProperty("useSSL", String.valueOf(config.useSsl()));
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        this.dataSource = new HikariDataSource(hikari);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public <T> T withTransaction(Transaction<T> work) {
        try (Connection connection = getConnection()) {
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (Exception e) {
                connection.rollback();
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        dataSource.close();
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "mariadb";
        }
        String normalized = type.toLowerCase(Locale.ROOT);
        if (normalized.contains("mysql")) {
            return "mysql";
        }
        return "mariadb";
    }

    private String buildJdbcUrl(String type, DatabaseConfig config) {
        String protocol = type.equals("mysql") ? "mysql" : "mariadb";
        return "jdbc:" + protocol + "://" + config.host() + ":" + config.port() + "/" + config.name();
    }

    private String driverClassName(String type) {
        return type.equals("mysql") ? "com.mysql.cj.jdbc.Driver" : "org.mariadb.jdbc.Driver";
    }

    private boolean tryLoad(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
