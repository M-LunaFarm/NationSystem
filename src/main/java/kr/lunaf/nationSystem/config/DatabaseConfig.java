package kr.lunaf.nationSystem.config;

public record DatabaseConfig(
    String type,
    String host,
    int port,
    String name,
    String user,
    String password,
    int poolSize,
    boolean useSsl
) {
}
