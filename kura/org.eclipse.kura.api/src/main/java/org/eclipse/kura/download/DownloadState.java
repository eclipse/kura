/*******************************************************************************
 * Copyright (c) 2019 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 *******************************************************************************/
package org.eclipse.kura.download;

import java.util.Optional;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Represents the state of a download operation.
 * 
 * @since 2.2
 * @noimplement This interface is not intended to be implemented by clients.
 */
@ProviderType
public interface DownloadState {

    /**
     * Returns the total size of the data that will be transferred, if known.
     * 
     * @return the total data size.
     */
    public Optional<Long> getTotalSize();

    /**
     * Returns the number of transferred bytes.
     * 
     * @return the number of transferred bytes.
     */
    public long getDownloadedBytes();

    /**
     * Returns an the download progress in percentage.
     * 
     * @return the download progress in percentage.
     */
    public long getDownloadPercent();

    /**
     * Returns the download operation status.
     * 
     * @return the download operation status.
     */
    public DownloadStatus getStatus();

    /**
     * If the operation status is {@link DownloadStatus#FAILED}, returns the exception that caused the failure, if any.
     * 
     * @return the exception that caused the failure
     */
    public Optional<Throwable> getException();
}
