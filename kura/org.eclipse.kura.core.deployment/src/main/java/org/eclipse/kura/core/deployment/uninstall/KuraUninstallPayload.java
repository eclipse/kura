/*******************************************************************************
 * Copyright (c) 2011, 2019 Eurotech and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech
 *******************************************************************************/

package org.eclipse.kura.core.deployment.uninstall;

import java.text.ParseException;

import org.eclipse.kura.core.deployment.KuraNotifyPayload;
import org.eclipse.kura.message.KuraPayload;

public class KuraUninstallPayload extends KuraPayload {

    public static final String METRIC_CLIENT_ID = "client.id";
    public static final String METRIC_UNINSTALL_PROGRESS = "dp.uninstall.progress";
    public static final String METRIC_UNINSTALL_STATUS = "dp.uninstall.status";
    public static final String METRIC_DP_NAME = "dp.name";
    public static final String METRIC_DP_VERSION = "dp.version";
    public static final String METRIC_JOB_ID = "job.id";
    private static final String METRIC_ERROR_MESSAGE = "dp.uninstall.error.message";

    public KuraUninstallPayload(String clientId) {
        super();
        addMetric(METRIC_CLIENT_ID, clientId);
    }

    public KuraUninstallPayload(KuraPayload kuraPayload) {
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

    public void setUninstallProgress(int installProgress) {
        addMetric(METRIC_UNINSTALL_PROGRESS, installProgress);
    }

    public int getUninstallProgress() {
        return (Integer) getMetric(METRIC_UNINSTALL_PROGRESS);
    }

    public void setUninstallStatus(String installStatus) {
        addMetric(METRIC_UNINSTALL_STATUS, installStatus);
    }

    public int getUninstallStatus() {
        return (Integer) getMetric(METRIC_UNINSTALL_STATUS);
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
}