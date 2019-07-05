/*******************************************************************************
 * Copyright (c) 2011, 2017 Eurotech and/or its affiliates
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

import java.text.ParseException;

import org.eclipse.kura.core.deployment.download.impl.KuraNotifyPayload;
import org.eclipse.kura.message.KuraPayload;

public class KuraInstallPayload extends KuraPayload {

    public static final String METRIC_CLIENT_ID = "client.id";
    public static final String METRIC_INSTALL_PROGRESS = "dp.install.progress";
    public static final String METRIC_INSTALL_STATUS = "dp.install.status";
    public static final String METRIC_DP_NAME = "dp.name";
    public static final String METRIC_DP_VERSION = "dp.version";
    public static final String METRIC_JOB_ID = "job.id";
    public static final String METRIC_NOTIFICATION = "dp.notification";

    private static final String METRIC_ERROR_MESSAGE = "dp.install.error.message";

    public KuraInstallPayload(String clientId) {
        super();
        addMetric(METRIC_CLIENT_ID, clientId);
    }

    public KuraInstallPayload(KuraPayload kuraPayload) {
        for (String name : kuraPayload.metricNames()) {
            Object value = kuraPayload.getMetric(name);
            addMetric(name, value);
        }
        setBody(kuraPayload.getBody());
        setPosition(kuraPayload.getPosition());
        setTimestamp(kuraPayload.getTimestamp());
    }

    public void setClientId(String requesterClientId) {
        addMetric(METRIC_CLIENT_ID, requesterClientId);
    }

    public String getClientId() {
        return (String) getMetric(METRIC_CLIENT_ID);
    }

    public void setInstallProgress(int installProgress) {
        addMetric(METRIC_INSTALL_PROGRESS, installProgress);
    }

    public int getInstallProgress() {
        return (Integer) getMetric(METRIC_INSTALL_PROGRESS);
    }

    public void setInstallStatus(String installStatus) {
        addMetric(METRIC_INSTALL_STATUS, installStatus);
    }

    public String getInstallStatus() {
        return (String) getMetric(METRIC_INSTALL_STATUS);
    }

    public void setDpName(String dpName) {
        addMetric(METRIC_DP_NAME, dpName);
    }

    public void setDpVersion(String dpVersion) {
        addMetric(METRIC_DP_VERSION, dpVersion);
    }

    public static KuraNotifyPayload buildFromKuraPayload(KuraPayload payload) throws ParseException {
        if (payload.getMetric(METRIC_CLIENT_ID) == null) {
            throw new ParseException("Not a valid notify payload", 0);
        }

        return new KuraNotifyPayload(payload);
    }

    public void setJobId(long jobId) {
        addMetric(METRIC_JOB_ID, jobId);
    }

    public Long getJobId() {
        return (Long) getMetric(METRIC_JOB_ID);
    }

    public void setErrorMessage(String errorMessage) {
        addMetric(METRIC_ERROR_MESSAGE, errorMessage);
    }

    public String getErrorMessage() {
        return (String) getMetric(METRIC_ERROR_MESSAGE);
    }

    public void setNotificationMessage(final String notificationMessage) {
        addMetric(METRIC_NOTIFICATION, notificationMessage);
    }

    public String getNotificationMessage() {
        return (String) getMetric(METRIC_NOTIFICATION);
    }

}