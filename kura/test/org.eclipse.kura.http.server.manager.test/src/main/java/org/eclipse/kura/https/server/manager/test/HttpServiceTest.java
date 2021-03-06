/*******************************************************************************
 * Copyright (c) 2021 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Eurotech
 *******************************************************************************/
package org.eclipse.kura.https.server.manager.test;

import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.bouncycastle.asn1.x500.X500Name;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.configuration.ConfigurationService;
import org.eclipse.kura.core.testutil.event.EventAdminUtil;
import org.eclipse.kura.core.testutil.http.TestServer;
import org.eclipse.kura.core.testutil.pki.TestCA;
import org.eclipse.kura.core.testutil.pki.TestCA.CRLCreationOptions;
import org.eclipse.kura.core.testutil.pki.TestCA.CertificateCreationOptions;
import org.eclipse.kura.core.testutil.pki.TestCA.TestCAException;
import org.eclipse.kura.crypto.CryptoService;
import org.eclipse.kura.security.keystore.KeystoreChangedEvent;
import org.eclipse.kura.security.keystore.KeystoreService;
import org.eclipse.kura.util.wire.test.WireTestUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServiceTest {

    private static final String TEST_KEYSTORE_PID = "testKeystore";
    private static final Logger logger = LoggerFactory.getLogger(HttpServiceTest.class);
    private static final String HTTP_SERVER_MANAGER_PID = "org.eclipse.kura.http.server.manager.HttpService";
    private static final String HTTPS_KEYSTORE_SERVICE_PID = "HttpsKeystore";

    private static CompletableFuture<ConfigurationService> configurationService = new CompletableFuture<>();
    private static CompletableFuture<CryptoService> cryptoService = new CompletableFuture<>();
    private static CompletableFuture<KeystoreService> keystoreService = new CompletableFuture<>();

    private static File serverKeystore;
    private static File clientKeystore;
    private static TestCA clientCA;
    private static X509Certificate clientCertificate;

    public void setConfigurationService(final ConfigurationService configurationService) {
        HttpServiceTest.configurationService.complete(configurationService);
    }

    public void setCryptoService(final CryptoService cryptoService) {
        HttpServiceTest.cryptoService.complete(cryptoService);
    }

    public void setKeystoreService(final KeystoreService keystoreService) {
        HttpServiceTest.keystoreService.complete(keystoreService);
    }

    @BeforeClass
    public static void setUp() throws TestCAException, IOException {

        final TestCA serverCA = new TestCA(
                CertificateCreationOptions.builder(new X500Name("cn=Server CA, dn=foo.org")).build());

        final KeyPair serverKeyPair = TestCA.generateKeyPair();

        final X509Certificate serverCertificate = serverCA.createAndSignCertificate(
                CertificateCreationOptions.builder(new X500Name("cn=Server Cert, dn=foo.org")).build(), serverKeyPair);

        clientCA = new TestCA(CertificateCreationOptions.builder(new X500Name("cn=Client CA, dn=bar.org")).build());

        serverKeystore = TestCA.writeKeystore(
                new KeyStore.PrivateKeyEntry(serverKeyPair.getPrivate(),
                        new Certificate[] { serverCertificate, serverCA.getCertificate() }),
                new KeyStore.TrustedCertificateEntry(clientCA.getCertificate()));

        final KeyPair clientKeyPair = TestCA.generateKeyPair();

        clientCertificate = clientCA.createAndSignCertificate(
                CertificateCreationOptions.builder(new X500Name("cn=admin, dn=bar.org")).build(), clientKeyPair);

        clientKeystore = TestCA.writeKeystore(new KeyStore.PrivateKeyEntry(clientKeyPair.getPrivate(),
                new Certificate[] { clientCertificate, clientCA.getCertificate() }));
    }

    @Test
    public void shouldNotOpenAnyPortWithDefaultConfig() throws KuraException, InvalidSyntaxException,
            InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
        final ConfigurationService configService = configurationService.get(5, TimeUnit.MINUTES);

        updateComponentConfiguration(configService, HTTP_SERVER_MANAGER_PID,
                HttpServiceOptions.defaultConfiguration().toProperties()).get(30, TimeUnit.SECONDS);

        assertAlwaysTrue(() -> getHttpStatusCode("http://localhost:80/") instanceof Failure);
        assertAlwaysTrue(() -> getHttpStatusCode("http://localhost:8080/") instanceof Failure);
        assertAlwaysTrue(() -> getHttpStatusCode("https://localhost:4442/") instanceof Failure);
        assertAlwaysTrue(() -> getHttpStatusCode("https://localhost:4443/") instanceof Failure);

    }

    @Test
    public void shouldOpenHttpPorts() throws KuraException, InvalidSyntaxException, InterruptedException,
            ExecutionException, TimeoutException, MalformedURLException {
        final ConfigurationService configService = configurationService.get(5, TimeUnit.MINUTES);

        updateComponentConfiguration(configService, HTTP_SERVER_MANAGER_PID,
                HttpServiceOptions.defaultConfiguration().withHttpPorts(8080).toProperties()).get(30, TimeUnit.SECONDS);

        assertTrueAtLeastOnce(() -> getHttpStatusCode("http://localhost:8080/").equals(new StatusCode(404)));

        assertAlwaysTrue(() -> getHttpStatusCode("http://localhost:80/") instanceof Failure);
        assertAlwaysTrue(() -> getHttpStatusCode("https://localhost:4442/") instanceof Failure);
        assertAlwaysTrue(() -> getHttpStatusCode("https://localhost:4443/") instanceof Failure);

    }

    @Test
    public void shouldOpenMultipleHttpPorts() throws KuraException, InvalidSyntaxException, InterruptedException,
            ExecutionException, TimeoutException, MalformedURLException {
        final ConfigurationService configService = configurationService.get(5, TimeUnit.MINUTES);

        updateComponentConfiguration(configService, HTTP_SERVER_MANAGER_PID,
                HttpServiceOptions.defaultConfiguration().withHttpPorts(8080, 8081, 8082).toProperties()).get(30,
                        TimeUnit.SECONDS);

        assertTrueAtLeastOnce(() -> getHttpStatusCode("http://localhost:8080/").equals(new StatusCode(404)));
        assertTrueAtLeastOnce(() -> getHttpStatusCode("http://localhost:8081/").equals(new StatusCode(404)));
        assertTrueAtLeastOnce(() -> getHttpStatusCode("http://localhost:8082/").equals(new StatusCode(404)));

    }

    @Test
    public void shouldNotSupportHttpsWithoutKeystore() throws KuraException, InvalidSyntaxException,
            InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
        final ConfigurationService configService = configurationService.get(5, TimeUnit.MINUTES);

        updateComponentConfiguration(configService, HTTP_SERVER_MANAGER_PID, HttpServiceOptions.defaultConfiguration()
                .withHttpsPorts(4442).withHttpsClientAuthPorts(4443).toProperties()).get(30, TimeUnit.SECONDS);

        assertAlwaysTrue(() -> getHttpStatusCode("https://localhost:443/") instanceof Failure);
        assertAlwaysTrue(() -> getHttpStatusCode("https://localhost:4443/") instanceof Failure);

    }

    @Test
    public void shouldSupportHttps() throws Exception {

        final ConfigurationService configSvc = configurationService.get(5, TimeUnit.MINUTES);
        final CryptoService cryptoSvc = cryptoService.get(5, TimeUnit.MINUTES);

        try (final TestKeystore testKeystore = new TestKeystore(configSvc, cryptoSvc, TEST_KEYSTORE_PID,
                HttpsKeystoreServiceOptions.defaultConfiguration())) {
            updateComponentConfiguration(configSvc, HTTP_SERVER_MANAGER_PID,
                    HttpServiceOptions.defaultConfiguration().withHttpsPorts(4442)
                            .withKeystoreServiceTarget(testKeystore.getTargetFilter()).toProperties()).get(30,
                                    TimeUnit.SECONDS);

            assertTrueAtLeastOnce(() -> getHttpStatusCode("https://localhost:4442/", Optional.empty(),
                    Optional.of(buildClientTrustManagers())).equals(new StatusCode(404)));
            assertAlwaysTrue(() -> getHttpStatusCode("http://localhost:8080/") instanceof Failure);
            assertAlwaysTrue(() -> getHttpStatusCode("https://localhost:4443/") instanceof Failure);
            assertAlwaysTrue(() -> getHttpStatusCode("http://localhost:80/") instanceof Failure);
        }
    }

    @Test
    public void shouldSupportHttpsClientAuth() throws Exception {

        final ConfigurationService configSvc = configurationService.get(5, TimeUnit.MINUTES);
        final CryptoService cryptoSvc = cryptoService.get(5, TimeUnit.MINUTES);

        try (final TestKeystore testKeystore = new TestKeystore(configSvc, cryptoSvc, TEST_KEYSTORE_PID,
                HttpsKeystoreServiceOptions.defaultConfiguration())) {

            updateComponentConfiguration(configSvc, HTTP_SERVER_MANAGER_PID,
                    HttpServiceOptions.defaultConfiguration().withHttpsClientAuthPorts(4443)
                            .withKeystoreServiceTarget(testKeystore.getTargetFilter()).toProperties()).get(30,
                                    TimeUnit.SECONDS);

            final KeyManager[] keyManagers = buildClientKeyManagers();

            assertTrueAtLeastOnce(() -> getHttpStatusCode("https://localhost:4443/", Optional.of(keyManagers),
                    Optional.of(buildClientTrustManagers())).equals(new StatusCode(404)));
            assertAlwaysTrue(() -> getHttpStatusCode("http://localhost:8080/") instanceof Failure);
            assertAlwaysTrue(() -> getHttpStatusCode("https://localhost:4442/") instanceof Failure);
            assertAlwaysTrue(() -> getHttpStatusCode("http://localhost:80/") instanceof Failure);

        }

        configSvc.deleteFactoryConfiguration(HTTPS_KEYSTORE_SERVICE_PID, false);
    }

    @Test
    public void shouldSupportRevocationCheckWithNoSources() throws Exception {

        final ConfigurationService configSvc = configurationService.get(5, TimeUnit.MINUTES);
        final CryptoService cryptoSvc = cryptoService.get(5, TimeUnit.MINUTES);

        try (final TestKeystore testKeystore = new TestKeystore(configSvc, cryptoSvc, TEST_KEYSTORE_PID,
                HttpsKeystoreServiceOptions.defaultConfiguration())) {

            updateComponentConfiguration(configSvc, HTTP_SERVER_MANAGER_PID,
                    HttpServiceOptions.defaultConfiguration().withHttpsClientAuthPorts(4443)
                            .withRevocationCheckEnabled(true).withKeystoreServiceTarget(testKeystore.getTargetFilter())
                            .toProperties()).get(30, TimeUnit.SECONDS);

            final KeyManager[] keyManagers = buildClientKeyManagers();

            assertAlwaysTrue(() -> getHttpStatusCode("https://localhost:4443/", Optional.of(keyManagers),
                    Optional.of(buildClientTrustManagers())) instanceof Failure);
            assertAlwaysTrue(() -> getHttpStatusCode("http://localhost:8080/") instanceof Failure);
            assertAlwaysTrue(() -> getHttpStatusCode("https://localhost:4442/") instanceof Failure);
            assertAlwaysTrue(() -> getHttpStatusCode("http://localhost:80/") instanceof Failure);

        }

        configSvc.deleteFactoryConfiguration(HTTPS_KEYSTORE_SERVICE_PID, false);
    }

    @Test
    public void shouldSupportRevocation() throws Exception {
        final BundleContext bundleContext = FrameworkUtil.getBundle(HttpServiceTest.class).getBundleContext();

        final TestServer server = new TestServer(8087, Optional.empty());

        final ConfigurationService configSvc = configurationService.get(5, TimeUnit.MINUTES);
        final CryptoService cryptoSvc = cryptoService.get(5, TimeUnit.MINUTES);

        try (final TestKeystore testKeystore = new TestKeystore(configSvc, cryptoSvc, TEST_KEYSTORE_PID,
                HttpsKeystoreServiceOptions.defaultConfiguration().withCrlManagerEnabled(true)
                        .withCrlUrls(new String[] { "http://localhost:8087/crl.pem" }))) {

            updateComponentConfiguration(configSvc, HTTP_SERVER_MANAGER_PID,
                    HttpServiceOptions.defaultConfiguration().withHttpsClientAuthPorts(4443)
                            .withRevocationCheckEnabled(true).withKeystoreServiceTarget(testKeystore.getTargetFilter())
                            .toProperties()).get(30, TimeUnit.SECONDS);

            CompletableFuture<KeystoreChangedEvent> nextEvent = EventAdminUtil.nextEvent(
                    new String[] { KeystoreChangedEvent.EVENT_TOPIC }, KeystoreChangedEvent.class, bundleContext);

            server.setResource("/crl.pem", encodeCrl(clientCA.generateCRL(CRLCreationOptions.builder().build())));
            nextEvent.get(10, TimeUnit.SECONDS);

            final KeyManager[] keyManagers = buildClientKeyManagers();

            Thread.sleep(10000);

            assertTrueAtLeastOnce(() -> getHttpStatusCode("https://localhost:4443/", Optional.of(keyManagers),
                    Optional.of(buildClientTrustManagers())).equals(new StatusCode(404)));

            nextEvent = EventAdminUtil.nextEvent(new String[] { KeystoreChangedEvent.EVENT_TOPIC },
                    KeystoreChangedEvent.class, bundleContext);

            clientCA.revokeCertificate(clientCertificate);
            server.setResource("/crl.pem", encodeCrl(clientCA.generateCRL(CRLCreationOptions.builder().build())));

            nextEvent.get(10, TimeUnit.SECONDS);

            Thread.sleep(10000);

            assertAlwaysTrue(() -> getHttpStatusCode("https://localhost:4443/", Optional.of(keyManagers),
                    Optional.of(buildClientTrustManagers())) instanceof Failure);

        } finally {
            server.close();
        }
    }

    @Test
    public void shouldRejectClientConnectionWithNoCert() throws Exception {

        final ConfigurationService configSvc = configurationService.get(5, TimeUnit.MINUTES);
        final CryptoService cryptoSvc = cryptoService.get(5, TimeUnit.MINUTES);

        try (final TestKeystore testKeystore = new TestKeystore(configSvc, cryptoSvc, TEST_KEYSTORE_PID,
                HttpsKeystoreServiceOptions.defaultConfiguration())) {

            updateComponentConfiguration(configSvc, HTTP_SERVER_MANAGER_PID,
                    HttpServiceOptions.defaultConfiguration().withHttpsClientAuthPorts(4443)
                            .withKeystoreServiceTarget(testKeystore.getTargetFilter()).toProperties()).get(30,
                                    TimeUnit.SECONDS);

            assertTrueAtLeastOnce(() -> getHttpStatusCode("https://localhost:4443/", Optional.empty(),
                    Optional.of(buildClientTrustManagers())) instanceof Failure);

        }
    }

    @Test
    public void shouldSupportAllAuthMethods() throws Exception {

        final ConfigurationService configSvc = configurationService.get(5, TimeUnit.MINUTES);
        final CryptoService cryptoSvc = cryptoService.get(5, TimeUnit.MINUTES);

        try (final TestKeystore testKeystore = new TestKeystore(configSvc, cryptoSvc, TEST_KEYSTORE_PID,
                HttpsKeystoreServiceOptions.defaultConfiguration())) {

            updateComponentConfiguration(configSvc, HTTP_SERVER_MANAGER_PID,
                    HttpServiceOptions.defaultConfiguration().withHttpPorts(8080).withHttpsPorts(4442)
                            .withHttpsClientAuthPorts(4443).withKeystoreServiceTarget(testKeystore.getTargetFilter())
                            .toProperties()).get(30, TimeUnit.SECONDS);

            final KeyManager[] keyManagers = buildClientKeyManagers();

            assertTrueAtLeastOnce(() -> getHttpStatusCode("https://localhost:4443/", Optional.of(keyManagers),
                    Optional.of(buildClientTrustManagers())).equals(new StatusCode(404)));
            assertTrueAtLeastOnce(() -> getHttpStatusCode("http://localhost:8080/").equals(new StatusCode(404)));
            assertTrueAtLeastOnce(() -> getHttpStatusCode("https://localhost:4442/", Optional.empty(),
                    Optional.of(buildClientTrustManagers())).equals(new StatusCode(404)));
        }

    }

    private static byte[] encodeCrl(final X509CRL crl) throws IOException {
        final byte[] crlData;

        try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            TestCA.encodeToPEM(crl, out);
            crlData = out.toByteArray();
        }

        return crlData;
    }

    private static class TestKeystore implements AutoCloseable {

        private final ConfigurationService configSvc;
        private final String pid;

        public TestKeystore(final ConfigurationService configSvc, final CryptoService cryptoSvc, final String pid,
                final HttpsKeystoreServiceOptions options)
                throws InterruptedException, ExecutionException, TimeoutException, IOException, KuraException {
            this.configSvc = configSvc;
            this.pid = pid;

            WireTestUtil
                    .createFactoryConfiguration(configSvc, ConfigurableComponent.class, pid,
                            "org.eclipse.kura.core.keystore.FilesystemKeystoreServiceImpl",
                            options.withKeystorePath(serverKeystore.getAbsolutePath())
                                    .withKeystorePassword("changeit", cryptoSvc).toProperties())
                    .get(30, TimeUnit.SECONDS);
        }

        String getTargetFilter() {
            return "(kura.service.pid=" + pid + ")";
        }

        @Override
        public void close() throws Exception {
            WireTestUtil.deleteFactoryConfiguration(configSvc, pid).get(30, TimeUnit.SECONDS);
        }

    }

    private static class HttpsKeystoreServiceOptions {

        private String keystorePath = "";
        private String keystorePassword = "";
        private boolean crlManagerEnabled = false;
        private Optional<String[]> crlUrls = Optional.empty();

        private HttpsKeystoreServiceOptions() {
        }

        static HttpsKeystoreServiceOptions defaultConfiguration() {
            return new HttpsKeystoreServiceOptions();
        }

        HttpsKeystoreServiceOptions withKeystorePath(final String keystorePath) {
            this.keystorePath = keystorePath;
            return this;
        }

        HttpsKeystoreServiceOptions withKeystorePassword(final String keystorePassword,
                final CryptoService cryptoService) throws KuraException {
            this.keystorePassword = new String(cryptoService.encryptAes(keystorePassword.toCharArray()));
            return this;
        }

        HttpsKeystoreServiceOptions withCrlManagerEnabled(boolean crlManagerEnabled) {
            this.crlManagerEnabled = crlManagerEnabled;
            return this;
        }

        HttpsKeystoreServiceOptions withCrlUrls(String[] crlUrls) {
            this.crlUrls = Optional.of(crlUrls);
            return this;
        }

        Map<String, Object> toProperties() {
            final Map<String, Object> result = new HashMap<>();

            result.put("keystore.path", this.keystorePath);
            result.put("keystore.password", this.keystorePassword);
            result.put("crl.check.interval", 1L);
            result.put("crl.check.interval.time.unit", TimeUnit.SECONDS.name());
            result.put("crl.update.interval", 1L);
            result.put("crl.update.interval.time.unit", TimeUnit.SECONDS.name());
            result.put("crl.management.enabled", crlManagerEnabled);
            this.crlUrls.ifPresent(u -> result.put("crl.urls", u));

            return result;
        }

    }

    private static class HttpServiceOptions {

        private Integer[] httpPorts = new Integer[] {};
        private Integer[] httpsPorts = new Integer[] {};
        private Integer[] httpsClientAuthPorts = new Integer[] {};
        private Optional<String> keystoreServiceTarget = Optional.empty();
        private Optional<Boolean> revocationCheckEnabled = Optional.empty();

        private HttpServiceOptions() {
        }

        static HttpServiceOptions defaultConfiguration() {
            return new HttpServiceOptions();
        }

        HttpServiceOptions withHttpPorts(final Integer... httpPorts) {
            this.httpPorts = httpPorts;
            return this;
        }

        HttpServiceOptions withHttpsPorts(final Integer... httpsPorts) {
            this.httpsPorts = httpsPorts;
            return this;
        }

        HttpServiceOptions withHttpsClientAuthPorts(final Integer... httpsClientAuthPorts) {
            this.httpsClientAuthPorts = httpsClientAuthPorts;
            return this;
        }

        HttpServiceOptions withKeystoreServiceTarget(final String target) {
            this.keystoreServiceTarget = Optional.of(target);
            return this;
        }

        HttpServiceOptions withRevocationCheckEnabled(final boolean enabled) {
            this.revocationCheckEnabled = Optional.of(enabled);
            return this;
        }

        Map<String, Object> toProperties() {
            final Map<String, Object> result = new HashMap<>();

            result.put("http.ports", this.httpPorts);
            result.put("https.ports", this.httpsPorts);
            result.put("https.client.auth.ports", this.httpsClientAuthPorts);
            this.revocationCheckEnabled.ifPresent(v -> result.put("https.revocation.check.enabled", v));
            result.put("KeystoreService.target", this.keystoreServiceTarget.orElse("(kura.service.pid=changeit)"));

            return result;
        }

    }

    private interface ConnectionResult {
    }

    private static class StatusCode implements ConnectionResult {

        private final int value;

        StatusCode(final int value) {
            super();
            this.value = value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.value);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            StatusCode other = (StatusCode) obj;
            return this.value == other.value;
        }

        @Override
        public String toString() {
            return "StatusCode [value=" + this.value + "]";
        }

    }

    private static class Failure implements ConnectionResult {

        private final Throwable cause;

        Failure(Throwable cause) {
            this.cause = cause;
        }

        @Override
        public String toString() {
            return "Failure [cause=" + this.cause + "]";
        }

    }

    private static ConnectionResult getHttpStatusCode(final String url) {
        return getHttpStatusCode(url, Optional.empty(), Optional.empty());
    }

    private static ConnectionResult getHttpStatusCode(final String url, final Optional<KeyManager[]> keyManagers,
            final Optional<TrustManager[]> trustManagers) {
        try {
            final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

            logger.info("trying {}", url);

            if (connection instanceof HttpsURLConnection) {
                final HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;

                final SSLContext context = SSLContext.getInstance("TLS");

                context.init(keyManagers.orElse(null), trustManagers.orElse(null), null);

                HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
                HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());

                httpsConnection.setHostnameVerifier((h, s) -> true);
            }

            return new StatusCode(connection.getResponseCode());
        } catch (final Exception e) {
            return new Failure(e);
        }
    }

    private static void assertAlwaysTrue(final Supplier<Boolean> check) throws InterruptedException {
        for (int i = 0; i < 3; i++) {

            if (!check.get()) {
                fail("should have been true");
            }

            Thread.sleep(1000);
        }
    }

    private static void assertTrueAtLeastOnce(final Supplier<Boolean> check) throws InterruptedException {
        for (int i = 0; i < 3; i++) {

            if (check.get()) {
                return;
            }

            Thread.sleep(1000);
        }

        fail("should have been true at least once");
    }

    static CompletableFuture<Void> updateComponentConfiguration(final ConfigurationService configurationService,
            final String pid, final Map<String, Object> properties) throws KuraException, InvalidSyntaxException {

        final CompletableFuture<Void> result = new CompletableFuture<>();
        final BundleContext context = FrameworkUtil.getBundle(WireTestUtil.class).getBundleContext();

        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        final ServiceTracker<?, ?> tracker = new ServiceTracker<>(context,
                FrameworkUtil.createFilter("(kura.service.pid=" + pid + ")"),
                new ServiceTrackerCustomizer<Object, Object>() {

                    Optional<ScheduledFuture<?>> task = Optional.empty();

                    @Override
                    public Object addingService(ServiceReference<Object> reference) {

                        task = Optional.of(executor.schedule(() -> {
                            try {
                                configurationService.updateConfiguration(pid, properties);
                            } catch (KuraException e) {
                                throw new RuntimeException(e);
                            }
                        }, 5, TimeUnit.SECONDS));

                        return context.getService(reference);
                    }

                    @Override
                    public void modifiedService(ServiceReference<Object> reference, Object service) {
                        result.complete(null);
                    }

                    @Override
                    public void removedService(ServiceReference<Object> reference, Object service) {
                        context.ungetService(reference);
                        final Optional<ScheduledFuture<?>> currentTask = task;
                        if (currentTask.isPresent()) {
                            currentTask.get().cancel(false);
                            task = Optional.empty();
                        }
                    }
                });

        tracker.open();

        return result.whenComplete((ok, ex) -> {
            tracker.close();
            executor.shutdown();
        });
    }

    private static KeyManager[] buildClientKeyManagers() throws KeyStoreException, NoSuchAlgorithmException,
            CertificateException, IOException, UnrecoverableKeyException, KeyManagementException {
        final KeyStore keystore = KeyStore.getInstance("JKS");
        try (final FileInputStream in = new FileInputStream(clientKeystore)) {
            keystore.load(in, "changeit".toCharArray());
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        keyManagerFactory.init(keystore, "changeit".toCharArray());

        return keyManagerFactory.getKeyManagers();
    }

    private static TrustManager[] buildClientTrustManagers() {
        final TrustManager trustManager = new X509TrustManager() {

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                // accept
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                // accept
            }
        };

        return new TrustManager[] { trustManager };
    }

}
