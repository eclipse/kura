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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.kura.cloudconnection.publisher.CloudNotificationPublisher;
import org.eclipse.kura.core.deployment.DeploymentPackageOptions;
import org.eclipse.kura.deployment.hook.PersistedRequestService;
import org.eclipse.kura.deployment.hook.Request;
import org.eclipse.kura.deployment.hook.RequestEventStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistedRequestServiceImpl implements PersistedRequestService {

    private static final Logger logger = LoggerFactory.getLogger(PersistedRequestServiceImpl.class);
    private static final String PERSISTENCE_ROOT_PROP_NAME = "persisted.request.root";

    private final Map<String, RequestImpl> requests = new HashMap<>();
    private final Map<String, CloudNotificationPublisher> trackedPublishers = new HashMap<>();

    private File persistedRequestRoot;

    public void activate(final Map<String, Object> properties) {
        this.persistedRequestRoot = new File((String) properties.get(PERSISTENCE_ROOT_PROP_NAME));

        seedPersistedRequests();
    }

    public synchronized void addNotificationPublisher(final String pid, final CloudNotificationPublisher publisher) {
        this.trackedPublishers.put(pid, publisher);
        for (final RequestImpl req : requests.values()) {
            if (req.getState().getNotificationPublisherPid().contentEquals(pid)) {
                req.setNotificationPublisher(publisher);
            }
        }
    }

    public synchronized void removeNotificationPublisher(final String pid) {
        this.trackedPublishers.remove(pid);
    }

    private void seedPersistedRequests() {
        if (!this.persistedRequestRoot.isDirectory() && !this.persistedRequestRoot.mkdirs()) {
            throw new IllegalStateException("Failed to create persistence root dir");
        }

        for (final String element : this.persistedRequestRoot.list((dir, name) -> name.endsWith(".properties"))) {
            final String id = element.substring(0, element.length() - ".properties".length());
            try {
                requests.put(id, RequestImpl.fromFile(persistedRequestRoot, id));
            } catch (final Exception e) {
                logger.warn("failed to parse persisted request {}", id, e);
            }
        }
    }

    public synchronized Request getRequest(final DeploymentPackageOptions options) {

        final String id = Long.toString(options.getJobId());

        if (requests.containsKey(id)) {
            return requests.get(id);
        }

        final PersistedRequestState state = new PersistedRequestState(options.getNotificationPublisherPid(), 0,
                options.getJobId(), options.getDpName(), options.getClientId(), options.getRequestClientId());
        final RequestImpl request = new RequestImpl(persistedRequestRoot, state, false);
        requests.put(id, request);
        request.setNotificationPublisher(options.getNotificationPublisher());

        return request;
    }

    @Override
    public synchronized Optional<Request> getRequest(String id) {
        final RequestImpl request = requests.get(id);

        if (request == null || !request.isPersistent()) {
            return Optional.empty();
        }

        return Optional.of(request);
    }

    @Override
    public synchronized List<Request> getRequests() {

        return requests.values().stream().filter(RequestImpl::isPersistent).collect(Collectors.toList());
    }

    private void setEventStream(final String id, final RequestEventStream stream) {
        final RequestImpl request = requests.get(id);

        if (request != null) {
            request.setEventStream(stream);
        } else {
            logger.warn("request with id {} not found", id);
        }
    }

    @Override
    public void registerEventStream(final RequestEventStream stream) {
        setEventStream(stream.getId(), stream);
    }

    @Override
    public void unregisterEventStream(final RequestEventStream stream) {
        setEventStream(stream.getId(), null);
    }

}
