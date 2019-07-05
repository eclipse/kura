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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Objects;
import java.util.Properties;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.cloudconnection.listener.CloudDeliveryListener;
import org.eclipse.kura.cloudconnection.message.KuraMessage;
import org.eclipse.kura.cloudconnection.publisher.CloudNotificationPublisher;
import org.eclipse.kura.deployment.hook.CompletionEvent;
import org.eclipse.kura.deployment.hook.Event;
import org.eclipse.kura.deployment.hook.NotificationEvent;
import org.eclipse.kura.deployment.hook.Request;
import org.eclipse.kura.deployment.hook.RequestEventStream;
import org.eclipse.kura.deployment.hook.RequestEventStreamListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestImpl implements Request, CloudDeliveryListener, RequestEventStreamListener {

    private static final Logger logger = LoggerFactory.getLogger(RequestImpl.class);

    private final File persistenceFile;

    private PersistedRequestState state;
    private boolean isPersistent;

    private RequestEventStream requestEventStream;
    private CloudNotificationPublisher notificationPublisher;

    private String confirmMessageId;

    public RequestImpl(final File root, final PersistedRequestState state, final boolean isPersistent) {
        this.state = state;
        this.persistenceFile = new File(root, state.getJobId() + ".properties");
        this.isPersistent = isPersistent;
    }

    public static RequestImpl fromFile(final File root, final String id) throws KuraException {
        final File persistenceFile = new File(root, id + ".properties");

        final Properties properties = new Properties();

        try (final FileInputStream in = new FileInputStream(persistenceFile)) {
            properties.load(in);
            return new RequestImpl(root, PersistedRequestState.fromProperties(properties), true);
        }

        catch (final Exception e) {
            throw new KuraException(KuraErrorCode.STORE_ERROR, e);
        }

    }

    @Override
    public synchronized void setEventStream(final RequestEventStream requestEventStream) {
        this.requestEventStream = requestEventStream;
        if (requestEventStream != null) {
            this.requestEventStream.registerListener(this);
            tryPublish();
        }
    }

    @Override
    public String getId() {
        return Long.toString(state.getJobId());
    }

    @Override
    public synchronized void makePersistent() throws KuraException {
        if (!isPersistent) {
            isPersistent = true;
            savePersistedState();
        }
    }

    @Override
    public synchronized void onMessageConfirmed(final String messageId) {
        if (Objects.equals(messageId, this.confirmMessageId)) {
            state.increaseNextEventIndex();
            savePersistedState();
            tryPublish();
        }
    }

    @Override
    public synchronized void onChange() {
        tryPublish();
    }

    public synchronized boolean isPersistent() {
        return isPersistent;
    }

    public synchronized void setNotificationPublisher(final CloudNotificationPublisher notificationPublisher) {
        this.notificationPublisher = notificationPublisher;
        tryPublish();
    }

    public PersistedRequestState getState() {
        return state;
    }

    private void tryPublish() {

        if (notificationPublisher == null || requestEventStream == null) {
            return;
        }

        Event event;
        final int startEventIndex = state.getNextEventIndex();

        while ((event = requestEventStream.getEvent(state.getNextEventIndex())) != null) {

            try {
                final String messageId = this.notificationPublisher.publish(buildMessage(event));

                if (messageId != null) {
                    confirmMessageId = messageId;
                    return;
                } else {
                    state.increaseNextEventIndex();
                }

            } catch (final Exception e) {
                logger.warn("failed to publish message", e);
            }
        }

        if (state.getNextEventIndex() != startEventIndex) {
            savePersistedState();
        }
    }

    private KuraMessage buildMessage(final Event event) {

        if (event instanceof CompletionEvent) {
            return state.createCompletionMessage(((CompletionEvent) event).getResult());
        } else if (event instanceof NotificationEvent) {
            return state.createNotifyMessage(((NotificationEvent) event).getData());
        } else {
            throw new IllegalStateException("unsupported event type");
        }
    }

    private void savePersistedState() {
        if (!isPersistent) {
            return;
        }

        try {
            final Properties properties = state.toProperties();

            try (final FileOutputStream out = new FileOutputStream(persistenceFile)) {
                properties.store(out, null);
            }

        } catch (final Exception e) {
            logger.warn("failed to store persisted state", e);
        }

    }

}
