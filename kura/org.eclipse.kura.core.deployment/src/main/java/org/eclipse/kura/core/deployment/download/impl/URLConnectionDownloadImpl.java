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

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import org.eclipse.kura.download.DownloadParameters;

public class URLConnectionDownloadImpl extends GenericDownloadImpl {

    public URLConnectionDownloadImpl(final DownloadParameters request, final ExecutorService executor) {
        super(request, executor);
    }

    protected Connection openConnection() throws IOException {
        final URLConnection urlConnection = request.getUri().toURL().openConnection();

        final Optional<Long> timeout = request.getTimeoutMs();

        if (timeout.isPresent()) {
            final int t = (int) (long) timeout.get();

            urlConnection.setConnectTimeout(t);
            urlConnection.setReadTimeout(t);
        }

        urlConnection.connect();

        return new Connection(urlConnection, false);
    }

    protected void run() throws IOException, InterruptedException {

        final Connection conn = openConnection();

        final Optional<Long> contentLength = conn.urlConnection.getContentLengthLong() < 0 ? Optional.empty()
                : Optional.of(conn.urlConnection.getContentLengthLong());

        try (final InputStream in = conn.urlConnection.getInputStream()) {
            transferFrom(in, contentLength, conn.isPartialContent);
        }

    }

    protected class Connection {

        protected final URLConnection urlConnection;
        protected final boolean isPartialContent;

        public Connection(URLConnection connection, boolean isPartialContent) {
            this.urlConnection = connection;
            this.isPartialContent = isPartialContent;
        }
    }
}
