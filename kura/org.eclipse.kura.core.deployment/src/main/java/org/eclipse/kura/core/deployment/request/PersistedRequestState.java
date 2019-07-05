/*******************************************************************************
 * Copyright (c) 2018 Eurotech and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.core.deployment.request;

import static java.util.Objects.requireNonNull;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.eclipse.kura.cloudconnection.message.KuraMessage;
import org.eclipse.kura.core.deployment.InstallStatus;
import org.eclipse.kura.core.deployment.install.KuraInstallPayload;
import org.eclipse.kura.deployment.hook.RequestResult;

public class PersistedRequestState {

    private static final String NOTIFICATION_PUBLISHER_PID_PROP_NAME = "notification.publisher.pid";
    private static final String NEXT_EVENT_INDEX_PROP_NAME = "next.event.index";
    private static final String JOB_ID_PROP_NAME = "job.id";
    private static final String DP_NAME_PROP_NAME = "dp.name";
    private static final String CLIENT_ID_PROP_NAME = "client.id";
    private static final String REQUESTOR_CLIENT_ID_PROP_NAME = "requestor.client.id";

    private final String notificationPublisherPid;
    private final long jobId;
    private final String dpName;
    private final String clientId;
    private final String requestorClientId;

    private int nextEventIndex;

    public PersistedRequestState(final String notificationPublisherPid, final int nextEventIndex, final long jobId,
            final String dpName, final String clientId, final String requestorClientId) {
        this.notificationPublisherPid = requireNonNull(notificationPublisherPid);
        this.nextEventIndex = nextEventIndex;
        this.jobId = jobId;
        this.dpName = requireNonNull(dpName);
        this.clientId = requireNonNull(clientId);
        this.requestorClientId = requireNonNull(requestorClientId);
    }

    public String getNotificationPublisherPid() {
        return notificationPublisherPid;
    }

    public int getNextEventIndex() {
        return nextEventIndex;
    }

    public long getJobId() {
        return jobId;
    }

    public String getDpName() {
        return dpName;
    }

    public String getClientId() {
        return clientId;
    }

    public String getRequesterClientId() {
        return requestorClientId;
    }

    public void setNextEventIndex(final int nextEventIndex) {
        this.nextEventIndex = nextEventIndex;
    }

    public void increaseNextEventIndex() {
        this.nextEventIndex++;
    }

    public static PersistedRequestState fromProperties(final Properties properties) {
        final String notificationPublisherPid = requireNonNull(
                properties.getProperty(NOTIFICATION_PUBLISHER_PID_PROP_NAME));
        final int nextEventIndex = Integer.parseInt(properties.getProperty(NEXT_EVENT_INDEX_PROP_NAME));
        final long jobId = Long.parseLong(properties.getProperty(JOB_ID_PROP_NAME));
        final String dpName = requireNonNull(properties.getProperty(DP_NAME_PROP_NAME));
        final String clientId = requireNonNull(properties.getProperty(CLIENT_ID_PROP_NAME));
        final String requesterClientId = requireNonNull(properties.getProperty(REQUESTOR_CLIENT_ID_PROP_NAME));

        return new PersistedRequestState(notificationPublisherPid, nextEventIndex, jobId, dpName, clientId,
                requesterClientId);
    }

    public Properties toProperties() {
        final Properties properties = new Properties();

        properties.setProperty(NOTIFICATION_PUBLISHER_PID_PROP_NAME, notificationPublisherPid);
        properties.setProperty(NEXT_EVENT_INDEX_PROP_NAME, Integer.toString(nextEventIndex));
        properties.setProperty(JOB_ID_PROP_NAME, Long.toString(jobId));
        properties.setProperty(DP_NAME_PROP_NAME, dpName);
        properties.setProperty(CLIENT_ID_PROP_NAME, clientId);
        properties.setProperty(REQUESTOR_CLIENT_ID_PROP_NAME, requestorClientId);

        return properties;
    }

    private Map<String, Object> getMessageProperties() {
        final Map<String, Object> properties = new HashMap<>();

        properties.put("appId", "DEPLOY-V2");
        properties.put("messageType", "install");
        properties.put("requestorClientId", requestorClientId);

        return properties;
    }

    public KuraMessage createCompletionMessage(final RequestResult result) {
        final KuraInstallPayload payload = new KuraInstallPayload(clientId);

        payload.setTimestamp(new Date());
        payload.setDpName(dpName);
        payload.setJobId(jobId);
        payload.setInstallProgress(100);
        payload.setInstallStatus(
                (result == RequestResult.SUCCESS ? InstallStatus.COMPLETED : InstallStatus.FAILED).getStatusString());

        return new KuraMessage(payload, getMessageProperties());
    }

    public KuraMessage createNotifyMessage(final String message) {
        final KuraInstallPayload payload = new KuraInstallPayload(clientId);

        payload.setTimestamp(new Date());
        payload.setDpName(dpName);
        payload.setJobId(jobId);
        payload.setInstallProgress(0);
        payload.setNotificationMessage(message);
        payload.setInstallStatus(InstallStatus.IN_PROGRESS.getStatusString());

        return new KuraMessage(payload, getMessageProperties());
    }

}
