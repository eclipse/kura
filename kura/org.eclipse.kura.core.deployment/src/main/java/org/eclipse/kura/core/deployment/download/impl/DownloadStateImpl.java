/*******************************************************************************
 * Copyright (c) 2019 Eurotech and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech
 *******************************************************************************/

package org.eclipse.kura.core.deployment.download.impl;

import java.util.Optional;

import org.eclipse.kura.download.DownloadState;
import org.eclipse.kura.download.DownloadStatus;

public class DownloadStateImpl implements DownloadState {

    private Optional<Long> transferSize = Optional.empty();
    private Optional<Throwable> exception = Optional.empty();
    private long downloadedBytes;
    private DownloadStatus status = DownloadStatus.IN_PROGRESS;

    DownloadStateImpl() {
    }

    DownloadStateImpl(final DownloadStateImpl other) {
        this.transferSize = other.transferSize;
        this.exception = other.exception;
        this.downloadedBytes = other.downloadedBytes;
        this.status = other.status;
    }

    @Override
    public Optional<Long> getTotalSize() {
        return transferSize;
    }

    @Override
    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    @Override
    public long getDownloadPercent() {
        if (transferSize.isPresent()) {
            return downloadedBytes * 100 / transferSize.get();
        } else if (status == DownloadStatus.COMPLETED) {
            return 100;
        }

        return 0;
    }

    @Override
    public DownloadStatus getStatus() {
        return status;
    }

    @Override
    public Optional<Throwable> getException() {
        return exception;
    }

    void setTotalSize(Optional<Long> transferSize) {
        this.transferSize = transferSize;
    }

    void incrementDownloadedBytes(long increment) {
        this.downloadedBytes += increment;
    }

    void setDownloadedBytes(long downloadedBytes) {
        this.downloadedBytes = downloadedBytes;
    }

    void setStatus(final DownloadStatus status) {
        this.status = status;
    }

    void setException(Optional<Throwable> exception) {
        this.exception = exception;
    }

}
