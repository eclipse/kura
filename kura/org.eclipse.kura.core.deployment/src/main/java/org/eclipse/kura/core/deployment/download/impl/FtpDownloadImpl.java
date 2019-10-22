/*******************************************************************************
 * Copyright (c) 2019 Eurotech and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.core.deployment.download.impl;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.eclipse.kura.download.Credentials;
import org.eclipse.kura.download.DownloadParameters;
import org.eclipse.kura.download.UsernamePassword;
import org.eclipse.kura.ssl.SslManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FtpDownloadImpl extends GenericDownloadImpl {

    private static final Logger logger = LoggerFactory.getLogger(FtpDownloadImpl.class);

    final SslManagerService sslManagerService;

    public FtpDownloadImpl(final DownloadParameters request, final SslManagerService sslManagerService,
            final ExecutorService executor) {
        super(request, executor);
        this.sslManagerService = sslManagerService;
    }

    @Override
    public void run() throws IOException, InterruptedException {

        FTPClient client = null;

        try {

            client = openClient();

            final File path = new File(requireNonNull(request.getUri().getPath(), "File path must be specified"));

            final String dir = path.getParent();
            final String name = path.getName();

            if (dir == null || name == null) {
                throw new IllegalArgumentException("Invalid file path");
            }

            if (!client.changeWorkingDirectory(dir)) {
                throw new IOException("failed to change working directory");
            }

            final FTPFile[] files = runDataOperation(client, c -> c.listFiles(name));

            final Optional<Long> contentLength;

            final File dest = request.getDestination();

            final Optional<Long> restartOffset = getRestartOffset(dest);

            if (files == null || files.length != 1 || files[0] == null) {
                logger.warn("failed to retrieve remote file size");
                contentLength = Optional.empty();
            } else {
                contentLength = Optional.of(files[0].getSize() - restartOffset.orElse(0L));
            }

            try (final InputStream in = openInputStream(client, name, restartOffset)) {
                transferFrom(in, contentLength, restartOffset.isPresent());
            }

            if (!client.completePendingCommand()) {
                logger.warn("failed to complete pending operation");
            }

        } finally {
            if (client != null) {
                client.disconnect();
            }
        }
    }

    private FTPClient openClient() throws IOException {

        FTPClient client = null;

        final URI uri = request.getUri();

        if ("ftps".equalsIgnoreCase(uri.getScheme())) {
            client = createFTPSClient(true);
        } else if ("ftpes".equalsIgnoreCase(uri.getScheme())) {
            client = createFTPSClient(false);
        } else if ("ftp".equalsIgnoreCase(uri.getScheme())) {
            client = new FTPClient();
        } else {
            throw new IllegalArgumentException("Unsupported uri scheme");
        }

        final int timeout = (int) (long) request.getTimeoutMs().orElse(60000L);

        client.setConnectTimeout(timeout);
        client.setDefaultTimeout(timeout);
        client.setDataTimeout(timeout);

        final String host = uri.getHost();
        final int port = uri.getPort();

        if (port == -1) {
            client.connect(host);
        } else {
            client.connect(host, port);
        }

        authenticate(client);

        client.enterLocalPassiveMode();

        if (!client.setFileType(FTP.BINARY_FILE_TYPE)) {
            throw new IOException("failed to set file type");
        }

        return client;
    }

    private void authenticate(FTPClient client) throws IOException {
        final Credentials credentials = request.getCredentials()
                .orElse(new UsernamePassword("anonymous".toCharArray(), "pass".toCharArray()));

        if (!(credentials instanceof UsernamePassword)) {
            throw new IllegalArgumentException("Unsupported credenital type" + credentials.getClass().getSimpleName());
        }

        final UsernamePassword usernamePassword = (UsernamePassword) credentials;

        if (!client.login(new String(usernamePassword.getUsername()), new String(usernamePassword.getPassword()))) {
            throw new IOException("login failed");
        }

        usernamePassword.erase();

    }

    private InputStream openInputStream(final FTPClient client, final String name, final Optional<Long> restartOffset)
            throws IOException {

        return runDataOperation(client, c -> {
            restartOffset.ifPresent(client::setRestartOffset);
            final InputStream in = client.retrieveFileStream(name);

            if (in == null) {
                throw new IOException("failed to retrieve file");
            }

            return in;
        });
    }

    private <U> U runDataOperation(final FTPClient client, final DataOperation<U> op) throws IOException {

        try {
            client.setUseEPSVwithIPv4(true);
            return op.run(client);
        } catch (final Exception e) {
            client.setUseEPSVwithIPv4(false);
            return op.run(client);
        }

    }

    private FTPSClient createFTPSClient(final boolean isImplicit) throws IOException {
        try {
            return new FTPSClient(isImplicit, sslManagerService.getSSLContext());
        } catch (final Exception e) {
            throw new IOException("failed to initialize FTPSClient", e);
        }
    }

    private Optional<Long> getRestartOffset(final File dest) {

        if (dest.exists() && request.shouldResume()) {
            return Optional.of(dest.length());
        } else {
            return Optional.empty();
        }
    }

    @FunctionalInterface
    private interface DataOperation<U> {

        public U run(final FTPClient client) throws IOException;
    }
}
