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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.download.Download;
import org.eclipse.kura.download.DownloadParameters;
import org.eclipse.kura.download.DownloadService;
import org.eclipse.kura.ssl.SslManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadServiceImpl implements DownloadService {

    private static final Logger logger = LoggerFactory.getLogger(DownloadServiceImpl.class);

    private SslManagerService sslManagerService;

    public void setSslManagerService(final SslManagerService sslManagerService) {
        this.sslManagerService = sslManagerService;
    }

    public void unsetSslManagerService(final SslManagerService sslManagerService) {
        this.sslManagerService = null;
    }

    @Override
    public Download createDownload(DownloadParameters request) throws KuraException {
        return createDownload(request, Executors.newSingleThreadExecutor());
    }

    @Override
    public Download createDownload(final DownloadParameters request, final ExecutorService executor)
            throws KuraException {
        final String scheme = request.getUri().getScheme();

        try {

            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                return new HttpDownloadImpl(request, sslManagerService, executor);
            } else if ("ftp".equalsIgnoreCase(scheme) || "ftps".equalsIgnoreCase(scheme)
                    || "ftpes".equalsIgnoreCase(scheme)) {
                sslManagerService.getSSLSocketFactory();
                return new FtpDownloadImpl(request, sslManagerService, executor);
            } else {
                logger.warn(
                        "no specific implementation available for protocol {}, falling back to generic implementation, authentication and download resume will not be supported",
                        scheme);
                return new URLConnectionDownloadImpl(request, executor);
            }

        } catch (final IOException e) {
            throw new KuraException(KuraErrorCode.SECURITY_EXCEPTION, e);
        } catch (final Exception e) {
            throw new KuraException(KuraErrorCode.INVALID_PARAMETER, e);
        }

    }

}
