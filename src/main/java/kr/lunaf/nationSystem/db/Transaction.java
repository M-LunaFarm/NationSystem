package kr.lunaf.nationSystem.db;

import java.sql.Connection;

@FunctionalInterface
public interface Transaction<T> {
    T run(Connection connection) throws Exception;
}
