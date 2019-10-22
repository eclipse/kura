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

import java.util.concurrent.CompletableFuture;

import org.eclipse.kura.download.listener.DownloadStateChangeListener;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Represents a download operation.
 * The operation must be explicitly started calling the {@link Download#start()} method.
 * 
 * @since 2.2
 * @noimplement This interface is not intended to be implemented by clients.
 */
@ProviderType
public interface Download {

    /**
     * Registers a {@link DownloadStateChangeListener} for download progress notifications.
     * 
     * @param listener
     *            the listener to be registered
     */
    public void registerListener(final DownloadStateChangeListener listener);

    /**
     * Unregisters a {@link DownloadStateChangeListener}.
     * 
     * @param listener
     *            the listener to be unregistered.
     */
    public void unregisterListener(final DownloadStateChangeListener listener);

    /**
     * Starts the download operation.
     * 
     * @throws IllegalStateException
     *             if the operation is already started, completed or cancelled.
     */
    public void start();

    /**
     * Returns a {@link DownloadState} instance representing the current state of the download operation.
     * 
     * @return the state of the download operation.
     */
    public DownloadState getState();

    /**
     * Returns the {@link DownloadParameters} associated with this download operation.
     * 
     * @return the parameters of this operation
     */
    public DownloadParameters getParameters();

    /**
     * Returns a future representing the completion of this download operation.
     * The {@link CompletableFuture#cancel()} method can be used to cancel the download.
     * 
     * @return a future representing the completion of this download operation.
     */
    public CompletableFuture<Void> future();
}
