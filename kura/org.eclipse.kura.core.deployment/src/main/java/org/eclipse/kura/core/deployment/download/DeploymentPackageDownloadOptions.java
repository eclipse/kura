/*******************************************************************************
 * Copyright (c) 2011, 2018 Eurotech and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech
 *******************************************************************************/

package org.eclipse.kura.core.deployment.download;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.KuraInvalidMessageException;
import org.eclipse.kura.cloudconnection.publisher.CloudNotificationPublisher;
import org.eclipse.kura.core.deployment.hook.DeploymentHookManager;
import org.eclipse.kura.core.deployment.install.DeploymentPackageInstallOptions;
import org.eclipse.kura.core.deployment.request.PersistedRequestServiceImpl;
import org.eclipse.kura.message.KuraPayload;
import org.eclipse.kura.message.KuraRequestPayload;

public class DeploymentPackageDownloadOptions extends DeploymentPackageInstallOptions {

    // Metrics in RESOURCE_DOWNLOAD
    public static final String METRIC_DP_DOWNLOAD_URI = "dp.uri";
    public static final String METRIC_DP_DOWNLOAD_PROTOCOL = "dp.download.protocol";
    public static final String METRIC_DP_DOWNLOAD_BLOCK_SIZE = "dp.download.block.size";
    public static final String METRIC_DP_DOWNLOAD_BLOCK_DELAY = "dp.download.block.delay";
    public static final String METRIC_DP_DOWNLOAD_TIMEOUT = "dp.download.timeout";
    public static final String METRIC_DP_DOWNLOAD_RESUME = "dp.download.resume";
    public static final String METRIC_DP_DOWNLOAD_USER = "dp.download.username";
    public static final String METRIC_DP_DOWNLOAD_PASSWORD = "dp.download.password";
    public static final String METRIC_DP_DOWNLOAD_NOTIFY_BLOCK_SIZE = "dp.download.notify.block.size";
    public static final String METRIC_DP_DOWNLOAD_FORCE_DOWNLOAD = "dp.download.force";
    public static final String METRIC_DP_DOWNLOAD_HASH = "dp.download.hash";
    public static final String METRIC_DP_INSTALL = "dp.install";

    private String deployUri;
    private String downloadProtocol;
    private int blockSize;
    private int notifyBlockSize;
    private int blockDelay = 0;
    private int timeout = 4000;

    private String username = null;
    private String password = null;
    private boolean forceDownload = false;

    private String hash;

    public DeploymentPackageDownloadOptions(String deployUri, String dpName, String dpVersion) {
        super(dpName, dpVersion);
        setDeployUri(deployUri);
    }

    public DeploymentPackageDownloadOptions(KuraPayload request, DeploymentHookManager hookManager,
            String downloadDirectory, PersistedRequestServiceImpl persistedRequestService,
            final String notificationPublisherPid, final CloudNotificationPublisher notificationPublisher)
            throws KuraException {
        super((String) null, (String) null);

        setDownloadDirectory(downloadDirectory);
        setNotificationPublisherPid(notificationPublisherPid);
        setNotificationPublisher(notificationPublisher);

        setDeployUri((String) request.getMetric(METRIC_DP_DOWNLOAD_URI));
        if (getDeployUri() == null) {
            throw new KuraInvalidMessageException("Missing deployment package URL!");
        }

        super.setDpName((String) request.getMetric(METRIC_DP_NAME));
        if (super.getDpName() == null) {
            throw new KuraInvalidMessageException("Missing deployment package name!");
        }

        super.setDpVersion((String) request.getMetric(METRIC_DP_VERSION));
        if (super.getDpVersion() == null) {
            throw new KuraInvalidMessageException("Missing deployment package version!");
        }

        setDownloadProtocol((String) request.getMetric(METRIC_DP_DOWNLOAD_PROTOCOL));
        if (getDownloadProtocol() == null) {
            throw new KuraInvalidMessageException("Missing download protocol!");
        }

        Long jobId = (Long) request.getMetric(METRIC_JOB_ID);
        if (jobId != null) {
            super.setJobId(jobId);
        }
        if (super.getJobId() == null) {
            throw new KuraInvalidMessageException("Missing jobId!");
        }

        super.setSystemUpdate((Boolean) request.getMetric(METRIC_DP_INSTALL_SYSTEM_UPDATE));
        if (super.getSystemUpdate() == null) {
            throw new KuraInvalidMessageException("Missing SystemUpdate!");
        }

        try {
            Object metric = request.getMetric(METRIC_DP_DOWNLOAD_BLOCK_SIZE);
            if (metric != null) {
                this.blockSize = (Integer) metric;
            }
            metric = request.getMetric(METRIC_DP_DOWNLOAD_BLOCK_DELAY);
            if (metric != null) {
                this.blockDelay = (Integer) metric;
            }
            metric = request.getMetric(METRIC_DP_DOWNLOAD_TIMEOUT);
            if (metric != null) {
                this.timeout = (Integer) metric;
            }
            metric = request.getMetric(METRIC_DP_DOWNLOAD_RESUME);
            if (metric != null) {
                super.setResume((Boolean) metric);
            }
            metric = request.getMetric(METRIC_DP_DOWNLOAD_USER);
            if (metric != null) {
                this.username = (String) metric;
            }
            metric = request.getMetric(METRIC_DP_DOWNLOAD_PASSWORD);
            if (metric != null) {
                this.password = (String) metric;
            }
            metric = request.getMetric(METRIC_DP_DOWNLOAD_HASH);
            if (metric != null) {
                this.hash = (String) metric;
            }
            metric = request.getMetric(METRIC_DP_INSTALL);
            if (metric != null) {
                super.setInstall((Boolean) metric);
            }
            metric = request.getMetric(METRIC_DP_REBOOT);
            if (metric != null) {
                super.setReboot((Boolean) metric);
            }
            metric = request.getMetric(METRIC_DP_REBOOT_DELAY);
            if (metric != null) {
                super.setRebootDelay((Integer) metric);
            }
            metric = request.getMetric(METRIC_DP_DOWNLOAD_FORCE_DOWNLOAD);
            if (metric != null) {
                this.forceDownload = (Boolean) metric;
            }

            metric = request.getMetric(METRIC_DP_DOWNLOAD_NOTIFY_BLOCK_SIZE);
            if (metric != null) {
                this.notifyBlockSize = (Integer) metric;
            }

            metric = request.getMetric(KuraRequestPayload.REQUESTER_CLIENT_ID);
            if (metric != null) {
                super.setRequestClientId((String) metric);
            }

            metric = request.getMetric(METRIC_INSTALL_VERIFIER_URI);
            if (metric != null) {
                super.setVerifierURI((String) metric);
            }

            parseHookRelatedOptions(request, hookManager, persistedRequestService);

        } catch (Exception ex) {
            throw new KuraException(KuraErrorCode.INTERNAL_ERROR, ex);
        }
    }

    public int getNotifyBlockSize() {
        return this.notifyBlockSize;
    }

    public void setNotifyBlockSize(int notifyBlockSize) {
        this.notifyBlockSize = notifyBlockSize;
    }

    public String getDeployUri() {
        return this.deployUri;
    }

    public void setDeployUri(String deployUri) {
        this.deployUri = deployUri;
    }

    public String getDownloadProtocol() {
        return this.downloadProtocol;
    }

    public void setDownloadProtocol(String downloadProtocol) {
        this.downloadProtocol = downloadProtocol;
    }

    public int getBlockSize() {
        return this.blockSize;
    }

    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
    }

    public int getBlockDelay() {
        return this.blockDelay;
    }

    public void setBlockDelay(int blockDelay) {
        this.blockDelay = blockDelay;
    }

    public int getTimeout() {
        return this.timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public boolean isDownloadForced() {
        return this.forceDownload;
    }

    public void setDownloadForced(boolean forceDownload) {
        this.forceDownload = forceDownload;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getHash() {
        return this.hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
