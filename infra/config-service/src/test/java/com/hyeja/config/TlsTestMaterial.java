package com.hyeja.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Generates disposable certificates with the JDK; no committed test private keys. */
final class TlsTestMaterial {
    static final String PASSWORD = "test-only-tls-password-1234";
    final Path server;
    final Path trust;
    final Path certificate;
    TlsTestMaterial(Path directory) throws Exception {
        server = directory.resolve("server.p12");
        trust = directory.resolve("trust.p12");
        certificate = directory.resolve("server.crt");
        command("-genkeypair", "-alias", "config-service", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,dns:config-service", "-validity", "2",
                "-keystore", server.toString(), "-storetype", "PKCS12");
        command("-exportcert", "-alias", "config-service", "-rfc", "-keystore", server.toString(),
                "-file", certificate.toString());
        command("-importcert", "-noprompt", "-alias", "config-service", "-file", certificate.toString(),
                "-keystore", trust.toString(), "-storetype", "PKCS12");
    }
    private void command(String... arguments) throws Exception {
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool").toString();
        var command = new ArrayList<>(List.of(executable));
        command.addAll(List.of(arguments));
        command.addAll(List.of("-storepass:env", "HYEJA_TEST_TLS_PASSWORD"));
        var builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("HYEJA_TEST_TLS_PASSWORD", PASSWORD);
        var process = builder.start();
        String diagnostic = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IllegalStateException("Test certificate generation failed: " + diagnostic);
    }
}
