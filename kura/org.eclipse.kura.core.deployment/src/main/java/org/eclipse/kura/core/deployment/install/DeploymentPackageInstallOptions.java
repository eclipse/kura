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

package org.eclipse.kura.core.deployment.install;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.KuraInvalidMessageException;
import org.eclipse.kura.cloudconnection.publisher.CloudNotificationPublisher;
import org.eclipse.kura.core.deployment.DeploymentPackageOptions;
import org.eclipse.kura.core.deployment.download.DownloadFileUtilities;
import org.eclipse.kura.core.deployment.hook.DeploymentHookManager;
import org.eclipse.kura.core.deployment.request.PersistedRequestServiceImpl;
import org.eclipse.kura.deployment.hook.DeploymentHook;
import org.eclipse.kura.deployment.hook.RequestContext;
import org.eclipse.kura.message.KuraPayload;
import org.eclipse.kura.message.KuraRequestPayload;

public class DeploymentPackageInstallOptions extends DeploymentPackageOptions {

    public static final String METRIC_DP_INSTALL_SYSTEM_UPDATE = "dp.install.system.update";
    public static final String METRIC_INSTALL_VERIFIER_URI = "dp.install.verifier.uri";
    public static final String METRIC_REQUEST_TYPE = "request.type";
    public static final String METRIC_HOOK_PROPERTIES = "hook.properties";

    private Boolean systemUpdate = false;
    private String verifierURI = null;
    private String requestType;
    private Map<String, Object> hookProperties = new HashMap<>();
    private DeploymentHook deploymentHook;
    private String downloadDirectory = "/tmp";
    private RequestContext hookRequestContext;

    // Metrics in RESOURCE_INSTALL
    public DeploymentPackageInstallOptions(String dpName, String dpVersion) {
        super(dpName, dpVersion);
    }

    public DeploymentPackageInstallOptions(KuraPayload request, DeploymentHookManager hookManager,
            String downloadDirectory, PersistedRequestServiceImpl persistedRequestService,
            final String notificationPublisherPid, final CloudNotificationPublisher notificationPublisher)
            throws KuraException {
        super(null, null);

        setDownloadDirectory(downloadDirectory);
        setNotificationPublisherPid(notificationPublisherPid);
        setNotificationPublisher(notificationPublisher);

        super.setDpName((String) request.getMetric(METRIC_DP_NAME));
        if (super.getDpName() == null) {
            throw new KuraInvalidMessageException("Missing deployment package name!");
        }

        super.setDpVersion((String) request.getMetric(METRIC_DP_VERSION));
        if (super.getDpVersion() == null) {
            throw new KuraInvalidMessageException("Missing deployment package version!");
        }

        Long jobId = (Long) request.getMetric(METRIC_JOB_ID);
        if (jobId != null) {
            super.setJobId(jobId);
        }
        if (super.getJobId() == null) {
            throw new KuraInvalidMessageException("Missing jobId!");
        }

        setSystemUpdate((Boolean) request.getMetric(METRIC_DP_INSTALL_SYSTEM_UPDATE));
        if (getSystemUpdate() == null) {
            throw new KuraInvalidMessageException("Missing System Update!");
        }

        try {
            Object metric = request.getMetric(METRIC_DP_REBOOT);
            if (metric != null) {
                super.setReboot((Boolean) metric);
            }
            metric = request.getMetric(METRIC_DP_REBOOT_DELAY);
            if (metric != null) {
                super.setRebootDelay((Integer) metric);
            }

            metric = request.getMetric(KuraRequestPayload.REQUESTER_CLIENT_ID);
            if (metric != null) {
                super.setRequestClientId((String) metric);
            }

            metric = request.getMetric(METRIC_INSTALL_VERIFIER_URI);
            if (metric != null) {
                setVerifierURI((String) metric);
            }

            parseHookRelatedOptions(request, hookManager, persistedRequestService);

        } catch (Exception ex) {
            throw new KuraException(KuraErrorCode.INTERNAL_ERROR, ex);
        }
    }

    public void setSystemUpdate(Boolean systemUpdate) {
        this.systemUpdate = systemUpdate;
    }

    public Boolean getSystemUpdate() {
        return this.systemUpdate;
    }

    public void setVerifierURI(String verifierURI) {
        this.verifierURI = verifierURI;
    }

    public void setDownloadDirectory(String downloadDirectory) {
        this.downloadDirectory = downloadDirectory;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public void setHookProperties(Map<String, Object> hookProperties) {
        this.hookProperties = hookProperties;
    }

    public void setHookRequestContext(RequestContext hookRequestContext) {
        this.hookRequestContext = hookRequestContext;
    }

    public void setDeploymentHook(DeploymentHook hook) {
        this.deploymentHook = hook;
    }

    public String getVerifierURL() {
        return this.verifierURI;
    }

    public String getRequestType() {
        return requestType;
    }

    public Map<String, Object> getHookProperties() {
        return hookProperties;
    }

    public DeploymentHook getDeploymentHook() {
        return deploymentHook;
    }

    public String getDownloadDirectory() {
        return downloadDirectory;
    }

    public RequestContext getHookRequestContext() {
        return hookRequestContext;
    }

    protected void parseHookRelatedOptions(KuraPayload request, DeploymentHookManager hookManager,
            PersistedRequestServiceImpl persistedRequestServiceImpl) throws IOException {
        Object metric = request.getMetric(METRIC_REQUEST_TYPE);
        if (metric != null) {
            setRequestType((String) metric);
            setDeploymentHook(hookManager.getHook(requestType));
            setHookRequestContext(new RequestContext(DownloadFileUtilities.getDpDownloadFile(this).getAbsolutePath(),
                    requestType, persistedRequestServiceImpl.getRequest(this)));
        } else {
            setDeploymentHook(null);
        }

        metric = request.getMetric(METRIC_HOOK_PROPERTIES);
        if (metric != null) {
            final Properties hookProperties = new Properties();
            hookProperties.load(new StringReader((String) metric));
            setHookProperties(hookProperties.entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue())));
        }
    }

}
