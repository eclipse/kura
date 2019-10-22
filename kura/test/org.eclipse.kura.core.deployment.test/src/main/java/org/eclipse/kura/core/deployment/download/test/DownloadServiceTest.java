/*******************************************************************************
 * Copyright (c) 2019 Eurotech and/or its affiliates and others
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.eclipse.kura.core.deployment.download.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.download.Download;
import org.eclipse.kura.download.DownloadParameters;
import org.eclipse.kura.download.DownloadService;
import org.eclipse.kura.download.DownloadState;
import org.eclipse.kura.download.DownloadStatus;
import org.eclipse.kura.download.Hash;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(DownloadServiceTest.class);
    private static final String TEST_MESSAGE = "test string";
    private static final Pattern RANGE_PATTERN = Pattern.compile("[ ]*bytes=(\\d+)-[ ]*");

    private static final String GET_RESOURCE = "http://localhost:8181/test/get";
    private static final String REDIRECT_RESOURCE = "http://localhost:8181/test/redirect";
    private static final String NONEXISTENT_RESOURCE = "http://localhost:1234/test";
    private static final String FAILURE_STATUS = "http://localhost:1234/nonexisting";
    private static final String DELAY = "http://localhost:1234/test/delay";

    private static HttpService httpService;
    private static DownloadService downloadService;
    private static final CountDownLatch dependencies = new CountDownLatch(2);
    private static File tempDir;
    private static int tempFileIndex;

    public void setHttpService(final HttpService httpService) {
        logger.info("http service bound");
        DownloadServiceTest.httpService = httpService;
        dependencies.countDown();
    }

    public void setDownloadService(final DownloadService downloadService) {
        logger.info("download service bound");
        DownloadServiceTest.downloadService = downloadService;
        dependencies.countDown();
    }

    @BeforeClass
    public static void init() throws InterruptedException, NamespaceException, ServletException, IOException {
        if (!dependencies.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("dependencies not available");
        }

        tempDir = Files.createTempDirectory("download-test").toFile();
        tempDir.deleteOnExit();

        httpService.registerServlet("/test", new TestServlet(), null, httpService.createDefaultHttpContext());
    }

    @AfterClass
    public static void cleanup() {
        if (httpService != null) {
            httpService.unregister("/test");
        }
    }

    @Test(expected = ExecutionException.class)
    public void shouldFailOnNonExistingURL()
            throws URISyntaxException, KuraException, InterruptedException, ExecutionException, TimeoutException {

        final DownloadParameters params = DownloadParameters.builder() //
                .withUri(new URI(NONEXISTENT_RESOURCE)) //
                .withDestination(new File(tempDir, "foo")) //
                .withTimeoutMs(Optional.of(15000L)) //
                .build();

        final Download download = downloadService.createDownload(params);

        download.start();

        download.future().get(30, TimeUnit.SECONDS);
    }

    @Test(expected = ExecutionException.class)
    public void shouldFailOnBadStatus()
            throws URISyntaxException, KuraException, InterruptedException, ExecutionException, TimeoutException {

        final DownloadParameters params = DownloadParameters.builder() //
                .withUri(new URI(FAILURE_STATUS)) //
                .withDestination(new File(tempDir, "foo")) //
                .withTimeoutMs(Optional.of(15000L)) //
                .build();

        final Download download = downloadService.createDownload(params);

        download.start();

        download.future().get(30, TimeUnit.SECONDS);
    }

    @Test
    public void shouldGetSimpleFile() throws URISyntaxException, KuraException, InterruptedException,
            ExecutionException, TimeoutException, IOException {

        final File dest = createTempFile();

        final DownloadParameters params = DownloadParameters.builder() //
                .withUri(new URI(GET_RESOURCE)) //
                .withDestination(dest) //
                .withTimeoutMs(Optional.of(15000L)) //
                .build();

        final Download download = downloadService.createDownload(params);

        download.start();

        download.future().get(30, TimeUnit.SECONDS);

        assertContentEquals(dest, TEST_MESSAGE);

    }

    @Test
    public void shouldFollowRedirect() throws URISyntaxException, KuraException, InterruptedException,
            ExecutionException, TimeoutException, IOException {

        final File dest = createTempFile();

        final DownloadParameters params = DownloadParameters.builder() //
                .withUri(new URI(REDIRECT_RESOURCE)) //
                .withDestination(dest) //
                .withTimeoutMs(Optional.of(15000L)) //
                .build();

        final Download download = downloadService.createDownload(params);

        download.start();

        download.future().get(30, TimeUnit.SECONDS);

        assertContentEquals(dest, TEST_MESSAGE);
    }

    @Test
    public void shouldSupportMD5Checksum() throws URISyntaxException, KuraException, InterruptedException,
            ExecutionException, TimeoutException, IOException {

        final File dest = createTempFile();

        final DownloadParameters params = DownloadParameters.builder() //
                .withUri(new URI(GET_RESOURCE)) //
                .withDestination(dest) //
                .withChecksum(Optional.of(new Hash("MD5", "6F8DB599DE986FAB7A21625B7916589C"))) //
                .withTimeoutMs(Optional.of(15000L)) //
                .build();

        final Download download = downloadService.createDownload(params);

        download.start();

        download.future().get(30, TimeUnit.SECONDS);

        assertContentEquals(dest, TEST_MESSAGE);
    }

    @Test(expected = ExecutionException.class)
    public void shouldFailIfWrongChecksum()
            throws URISyntaxException, KuraException, InterruptedException, ExecutionException, TimeoutException {

        final File dest = createTempFile();

        final DownloadParameters params = DownloadParameters.builder() //
                .withUri(new URI(GET_RESOURCE)) //
                .withDestination(dest) //
                .withChecksum(Optional.of(new Hash("MD5", "aabbccdd"))) //
                .withTimeoutMs(Optional.of(15000L)) //
                .build();

        final Download download = downloadService.createDownload(params);

        download.start();

        download.future().get(30, TimeUnit.SECONDS);
    }

    @Test
    public void shouldSupportResume() throws IOException, URISyntaxException, KuraException, InterruptedException,
            ExecutionException, TimeoutException {
        final File dest = createTempFile();
        write(dest, "foo");

        final DownloadParameters params = DownloadParameters.builder() //
                .withUri(new URI(GET_RESOURCE)) //
                .withDestination(dest) //
                .withResume(true) //
                .withForceDownload(false) //
                .withTimeoutMs(Optional.of(15000L)) //
                .build();

        final Download download = downloadService.createDownload(params);

        download.start();

        download.future().get(30, TimeUnit.SECONDS);

        assertContentEquals(dest, "foot string");
    }

    @Test
    public void shouldSupportForceDownload() throws IOException, URISyntaxException, KuraException,
            InterruptedException, ExecutionException, TimeoutException {
        final File dest = createTempFile();
        write(dest, "foo");

        final DownloadParameters params = DownloadParameters.builder() //
                .withUri(new URI(GET_RESOURCE)) //
                .withDestination(dest) //
                .withResume(true) //
                .withForceDownload(true) //
                .withTimeoutMs(Optional.of(15000L)) //
                .build();

        final Download download = downloadService.createDownload(params);

        download.start();

        download.future().get(30, TimeUnit.SECONDS);

        assertContentEquals(dest, TEST_MESSAGE);
    }

    @Test
    public void shouldSupportStartStopNotifications() throws IOException, URISyntaxException, KuraException,
            InterruptedException, ExecutionException, TimeoutException {
        final File dest = createTempFile();

        final DownloadParameters params = DownloadParameters.builder() //
                .withUri(new URI(GET_RESOURCE)) //
                .withDestination(dest) //
                .withNotificationBlockSize(Optional.of(4096L)) //
                .withTimeoutMs(Optional.of(15000L)) //
                .build();

        final Download download = downloadService.createDownload(params);

        final List<DownloadState> states = new ArrayList<>();

        download.registerListener((r, s) -> states.add(s));
        download.start();

        download.future().get(30, TimeUnit.SECONDS);

        assertContentEquals(dest, TEST_MESSAGE);
        assertEquals(2, states.size());

        final Iterator<DownloadState> iter = states.iterator();

        final DownloadState first = iter.next();

        assertEquals(0, first.getDownloadedBytes());
        assertEquals(Optional.of(11L), first.getTotalSize());
        assertEquals(0, first.getDownloadPercent());
        assertEquals(DownloadStatus.IN_PROGRESS, first.getStatus());
        assertEquals(Optional.empty(), first.getException());

        final DownloadState second = iter.next();

        assertEquals(11, second.getDownloadedBytes());
        assertEquals(Optional.of(11L), second.getTotalSize());
        assertEquals(100, second.getDownloadPercent());
        assertEquals(DownloadStatus.COMPLETED, second.getStatus());
        assertEquals(Optional.empty(), second.getException());
    }

    @Test
    public void shouldSupportProgressNotification() throws IOException, URISyntaxException, KuraException,
            InterruptedException, ExecutionException, TimeoutException {
        final File dest = createTempFile();

        final DownloadParameters params = DownloadParameters.builder() //
                .withUri(new URI(GET_RESOURCE)) //
                .withDestination(dest) //
                .withNotificationBlockSize(Optional.of(8L)) //
                .withTimeoutMs(Optional.of(15000L)) //
                .build();

        final Download download = downloadService.createDownload(params);

        final List<DownloadState> states = new ArrayList<>();

        download.registerListener((r, s) -> states.add(s));
        download.start();

        download.future().get(30, TimeUnit.SECONDS);

        assertContentEquals(dest, TEST_MESSAGE);

        final Iterator<DownloadState> iter = states.iterator();

        final DownloadState first = iter.next();

        assertEquals(0, first.getDownloadedBytes());
        assertEquals(Optional.of(11L), first.getTotalSize());
        assertEquals(0, first.getDownloadPercent());
        assertEquals(DownloadStatus.IN_PROGRESS, first.getStatus());
        assertEquals(Optional.empty(), first.getException());

        final DownloadState second = iter.next();

        assertEquals(8, second.getDownloadedBytes());
        assertEquals(Optional.of(11L), second.getTotalSize());
        assertEquals(72, second.getDownloadPercent());
        assertEquals(DownloadStatus.IN_PROGRESS, second.getStatus());
        assertEquals(Optional.empty(), second.getException());

        final DownloadState third = iter.next();

        assertEquals(11, third.getDownloadedBytes());
        assertEquals(Optional.of(11L), third.getTotalSize());
        assertEquals(100, third.getDownloadPercent());
        assertEquals(DownloadStatus.COMPLETED, third.getStatus());
        assertEquals(Optional.empty(), third.getException());

        assertEquals(3, states.size());
    }

    @Test
    public void shouldSuportCancellation()
            throws URISyntaxException, KuraException, InterruptedException, ExecutionException, TimeoutException {

        final DownloadParameters params = DownloadParameters.builder() //
                .withUri(new URI(DELAY)) //
                .withDestination(new File(tempDir, "foo")) //
                .withTimeoutMs(Optional.of(15000L)) //
                .build();

        final Download download = downloadService.createDownload(params);

        download.start();

        final CompletableFuture<Void> future = download.future();

        future.cancel(true);

        try {
            download.future().get(30, TimeUnit.SECONDS);
        } catch (final ExecutionException e) {
            return;
        } catch (final CancellationException e) {
            return;
        }

        fail("expected exception");
    }

    private static File createTempFile() {
        final File result = new File(tempDir, "temp" + tempFileIndex++);
        result.deleteOnExit();
        return result;
    }

    private static void assertContentEquals(final File file, final String expected) throws IOException {
        final String line;

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            line = reader.readLine();
        }

        assertEquals(expected, line);
    }

    private static void write(final File file, final String str) throws IOException {
        try (final FileWriter writer = new FileWriter(file)) {
            writer.write(str);
        }
    }

    private static final class TestServlet extends HttpServlet {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
                throws ServletException, IOException {
            final String path = req.getRequestURI().substring(req.getContextPath().length());

            if ("/test/get".contentEquals(path)) {
                handleGet(req, resp);
            } else if ("/test/redirect".contentEquals(path)) {
                handleRedirect(resp);
            } else if ("/test/delay".contentEquals(path)) {
                handleDelay(resp);
            } else {
                handleNonExisting(resp);
            }
        }

        private void handleGet(final HttpServletRequest req, final HttpServletResponse resp) {

            try {
                final int range = getRange(req);

                final String data = TEST_MESSAGE.substring(range);
                resp.setContentType("text/plain");
                resp.setContentLength(data.length());
                resp.setStatus(range == 0 ? 200 : 206);
                try (final Writer writer = new OutputStreamWriter(resp.getOutputStream())) {
                    writer.write(data);
                }
            } catch (final Exception e) {
                logger.warn("failed to handle get", e);
            }
        }

        private void handleRedirect(final HttpServletResponse resp) {
            try {
                resp.sendRedirect("/test/get");
            } catch (final Exception e) {
                logger.warn("failed to handle redirect", e);
            }
        }

        private void handleDelay(final HttpServletResponse resp) {
            try {
                Thread.sleep(60000);
                resp.setStatus(200);
            } catch (final Exception e) {
                logger.warn("failed to handle delay", e);
            }
        }

        private void handleNonExisting(final HttpServletResponse resp) {
            try {
                resp.sendError(404);
            } catch (final Exception e) {
                logger.warn("failed to handle non existing path", e);
            }
        }

        private static int getRange(final HttpServletRequest req) {

            try {
                final Matcher matcher = RANGE_PATTERN.matcher((String) req.getHeader("Range"));

                matcher.matches();

                return Integer.parseInt(matcher.group(1));
            } catch (final Exception e) {
                return 0;
            }

        }
    }
}
