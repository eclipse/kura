/*******************************************************************************
 * Copyright (c) 2019 Eurotech and/or its affiliates and others
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.eclipse.kura.download;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Enum representing the different status of the download process
 *
 * {@link DownloadStatus.DOWNLOAD_STATUS.PROGRESS} Download in
 * progress {@link DownloadStatus.DOWNLOAD_STATUS.COMPLETE} Download
 * completed {@link DownloadStatus.DOWNLOAD_STATUS.FAILED} Download
 * failed
 * 
 * @since 2.2
 * @noimplement This interface is not intended to be implemented by clients.
 */
@ProviderType
public enum DownloadStatus {
    IN_PROGRESS("IN_PROGRESS"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED");

    private final String status;

    DownloadStatus(String status) {
        this.status = status;
    }

    public String getStatusString() {
        return this.status;
    }
}