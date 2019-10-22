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

import java.util.concurrent.ExecutorService;

import org.eclipse.kura.KuraException;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Represents the parameters describing a download request.
 * 
 * @since 2.2
 * @noimplement This interface is not intended to be implemented by clients.
 */
@ProviderType
public interface DownloadService {

    /**
     * Creates a new download operation based on the provided parameters running on a new single threaded executor.
     * The returned operation will not be started until the {@link Download#start()} method is called.
     * 
     * @param params
     *            the download parameters.
     * @return the newly created download operation.
     * @throws KuraException
     *             if the download operation cannot be created.
     */
    public Download createDownload(DownloadParameters params) throws KuraException;

    /**
     * Creates a new download operation based on the provided parameters running on the specified executor.
     * The returned operation will not be started until the {@link Download#start()} method is called.
     * 
     * @param params
     *            the download parameters.
     * @param executor
     *            the executor to be used for performing the request
     * @return the newly created download operation.
     * @throws KuraException
     *             if the download operation cannot be created.
     */
    public Download createDownload(DownloadParameters params, ExecutorService executor) throws KuraException;

}
