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

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.HttpsURLConnection;

import org.eclipse.kura.download.Credentials;
import org.eclipse.kura.download.DownloadParameters;
import org.eclipse.kura.download.UsernamePassword;
import org.eclipse.kura.ssl.SslManagerService;

public class HttpDownloadImpl extends URLConnectionDownloadImpl {

    private final SslManagerService sslManagerService;

    public HttpDownloadImpl(final DownloadParameters request, final SslManagerService sslManagerService,
            final ExecutorService executor) {
        super(request, executor);

        this.sslManagerService = sslManagerService;
    }

    @Override
    protected Connection openConnection() throws IOException {
        HttpURLConnection.setFollowRedirects(true);

        final HttpURLConnection urlConnection = (HttpURLConnection) request.getUri().toURL().openConnection();

        final File dest = request.getDestination();

        if (dest.exists() && request.shouldResume()) {
            urlConnection.setRequestProperty("Range", "bytes=" + dest.length() + "-");
        }

        if (urlConnection instanceof HttpsURLConnection) {
            initHttpsURLConnection(urlConnection);
        }

        request.getTimeoutMs().ifPresent(t -> {
            final int timeout = (int) (long) t;
            urlConnection.setConnectTimeout(timeout);
            urlConnection.setReadTimeout(timeout);
        });
        request.getCredentials().ifPresent(c -> configureAuthentication(urlConnection, c));

        final int code = urlConnection.getResponseCode();

        if (code / 200 != 1) {
            throw new IOException("HTTP status: " + code);
        }

        return new Connection(urlConnection, code == 206);
    }

    private void initHttpsURLConnection(final HttpURLConnection urlConnection) throws IOException {
        final HttpsURLConnection httpsURLConnection = (HttpsURLConnection) urlConnection;
        try {
            httpsURLConnection.setSSLSocketFactory(sslManagerService.getSSLSocketFactory());
        } catch (final GeneralSecurityException e) {
            throw new IOException("failed to get SSLSocketFactory", e);
        }
    }

    private void configureAuthentication(final HttpURLConnection connection, final Credentials credentials) {
        if (credentials instanceof UsernamePassword) {
            final UsernamePassword usernamePassword = (UsernamePassword) credentials;

            final StringBuilder builder = new StringBuilder();
            builder.append(usernamePassword.getUsername()).append(':').append(usernamePassword.getPassword());

            final String encoded = Base64.getEncoder()
                    .encodeToString(builder.toString().getBytes(StandardCharsets.UTF_8));

            connection.addRequestProperty("Authorization", "Basic " + encoded);

            usernamePassword.erase();
        } else {
            throw new IllegalArgumentException(
                    "unsupported credentials type " + credentials.getClass().getSimpleName());
        }
    }

}
