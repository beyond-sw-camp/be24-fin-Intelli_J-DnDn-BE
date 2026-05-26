package org.example.dndndocumentmanagement.config;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

@Configuration
@Profile("elastic")
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    private final String uris;
    private final String username;
    private final String password;
    private final String caCertPath;
    private final Duration connectTimeout;
    private final Duration socketTimeout;

    public ElasticsearchConfig(
            @Value("${spring.elasticsearch.uris}") String uris,
            @Value("${spring.elasticsearch.username:}") String username,
            @Value("${spring.elasticsearch.password:}") String password,
            @Value("${elasticsearch.ssl.ca-cert-path:}") String caCertPath,
            @Value("${spring.elasticsearch.connection-timeout:2s}") Duration connectTimeout,
            @Value("${spring.elasticsearch.socket-timeout:3s}") Duration socketTimeout
    ) {
        this.uris = uris;
        this.username = username;
        this.password = password;
        this.caCertPath = caCertPath;
        this.connectTimeout = connectTimeout;
        this.socketTimeout = socketTimeout;
    }

    @Override
    public ClientConfiguration clientConfiguration() {
        ClientConfiguration.MaybeSecureClientConfigurationBuilder builder =
                ClientConfiguration.builder().connectedTo(hosts());
        ClientConfiguration.TerminalClientConfigurationBuilder terminalBuilder =
                usesSsl() ? sslBuilder(builder) : builder;

        terminalBuilder = terminalBuilder
                .withConnectTimeout(connectTimeout)
                .withSocketTimeout(socketTimeout);

        if (hasText(username) && hasText(password)) {
            terminalBuilder = terminalBuilder.withBasicAuth(username, password);
        }

        return terminalBuilder.build();
    }

    private ClientConfiguration.TerminalClientConfigurationBuilder sslBuilder(
            ClientConfiguration.MaybeSecureClientConfigurationBuilder builder
    ) {
        if (hasText(caCertPath)) {
            return builder.usingSsl(sslContextFromCa(Path.of(caCertPath)));
        }
        return builder.usingSsl();
    }

    private String[] hosts() {
        return Arrays.stream(uris.split(","))
                .map(String::trim)
                .filter(this::hasText)
                .map(this::toHostPort)
                .toArray(String[]::new);
    }

    private String toHostPort(String value) {
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return trimTrailingSlash(value);
        }

        URI uri = URI.create(value);
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        return uri.getHost() + ":" + port;
    }

    private boolean usesSsl() {
        return Arrays.stream(uris.split(","))
                .map(String::trim)
                .anyMatch(value -> value.startsWith("https://"));
    }

    private SSLContext sslContextFromCa(Path caPath) {
        try (InputStream inputStream = Files.newInputStream(caPath)) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Certificate certificate = certificateFactory.generateCertificate(inputStream);

            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("elasticsearch-ca", certificate);

            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load Elasticsearch CA certificate: " + caPath, e);
        }
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
