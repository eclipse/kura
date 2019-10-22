/*******************************************************************************
 * Copyright (c) 2019 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 *******************************************************************************/
package org.eclipse.kura.download.listener;

import org.eclipse.kura.download.DownloadParameters;
import org.eclipse.kura.download.DownloadState;
import org.osgi.annotation.versioning.ConsumerType;

/**
 * Listener interface that can be used for receiving download state change events.
 * 
 * @since 2.2
 */
@ConsumerType
public interface DownloadStateChangeListener {

    /**
     * Called when the download state is update.
     * 
     * @param request
     *            the parameters of the download operation.
     * @param downloadState
     *            the download state.
     */
    public void onDownloadStateChange(DownloadParameters request, final DownloadState downloadState);

}
