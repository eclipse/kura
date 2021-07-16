/*******************************************************************************
 * Copyright (c) 2019, 2020 Eurotech and/or its affiliates and others
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *  Eurotech
 *******************************************************************************/
package org.eclipse.kura.cloudconnection.raw.mqtt.cloud;

import static org.eclipse.kura.cloudconnecton.raw.mqtt.util.Utils.catchAll;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.kura.KuraConnectException;
import org.eclipse.kura.KuraDisconnectException;
import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.KuraNotConnectedException;
import org.eclipse.kura.cloud.CloudConnectionEstablishedEvent;
import org.eclipse.kura.cloud.CloudConnectionLostEvent;
import org.eclipse.kura.cloudconnection.CloudConnectionManager;
import org.eclipse.kura.cloudconnection.CloudEndpoint;
import org.eclipse.kura.cloudconnection.listener.CloudConnectionListener;
import org.eclipse.kura.cloudconnection.listener.CloudDeliveryListener;
import org.eclipse.kura.cloudconnection.message.KuraMessage;
import org.eclipse.kura.cloudconnection.raw.mqtt.publisher.PublishOptions;
import org.eclipse.kura.cloudconnection.raw.mqtt.subscriber.SubscribeOptions;
import org.eclipse.kura.cloudconnection.subscriber.listener.CloudSubscriberListener;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.configuration.ConfigurationService;
import org.eclipse.kura.core.util.MqttTopicUtil;
import org.eclipse.kura.data.DataService;
import org.eclipse.kura.data.listener.DataServiceListener;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RawMqttCloudEndpoint
        implements CloudEndpoint, CloudConnectionManager, DataServiceListener, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(RawMqttCloudEndpoint.class);

    private DataService dataService;
    private EventAdmin eventAdmin;
    private ComponentContext componentContext;

    private final Set<CloudDeliveryListener> cloudDeliveryListeners = new CopyOnWriteArraySet<>();
    private final Set<CloudConnectionListener> cloudConnectionListeners = new CopyOnWriteArraySet<>();
    private final Map<SubscribeOptions, Set<CloudSubscriberListener>> subscribers = new ConcurrentHashMap<>();
    
    private static final String TOPIC_PATTERN_STRING = "\\$([^\\s/]+)";
    private static final Pattern TOPIC_PATTERN = Pattern.compile(TOPIC_PATTERN_STRING);
    
    public void setDataService(final DataService dataService) {
        this.dataService = dataService;
    }

    public void unsetDataService(final DataService dataService) {
        this.dataService = null;
    }

    public void setEventAdmin(final EventAdmin eventAdmin) {
        this.eventAdmin = eventAdmin;
    }

    public void unsetEventAdmin(final EventAdmin eventAdmin) {
        this.eventAdmin = null;
    }

    public void activated(final ComponentContext componentContext) {
        logger.info("activating...");

        this.componentContext = componentContext;
        this.dataService.addDataServiceListener(this);

        if (this.dataService.isConnected()) {
            onConnectionEstablished();
        }

        logger.info("activating...done");
    }

    public void updated() {
        logger.info("updating...");
        logger.info("updating...done");
    }

    public void deactivated() {
        logger.info("deactivating...");

        this.dataService.removeDataServiceListener(this);

        synchronized (this) {
            this.subscribers.keySet().forEach(this::unsubscribe);
        }

        logger.info("deactivating...done");
    }

    @Override
    public void connect() throws KuraConnectException {
        this.dataService.connect();
    }

    @Override
    public void disconnect() throws KuraDisconnectException {
        this.dataService.disconnect(10);
    }

    @Override
    public boolean isConnected() {
        return this.dataService.isConnected();
    }

    @Override
    public void registerCloudConnectionListener(CloudConnectionListener cloudConnectionListener) {
        this.cloudConnectionListeners.add(cloudConnectionListener);
    }

    @Override
    public void unregisterCloudConnectionListener(CloudConnectionListener cloudConnectionListener) {
        this.cloudConnectionListeners.remove(cloudConnectionListener);
    }

    @Override
    public String publish(final KuraMessage message) throws KuraException {

        return publish(new PublishOptions(message.getProperties()), message);
    }

    public String publish(final PublishOptions options, final KuraMessage message) throws KuraException {

        final byte[] body = message.getPayload().getBody();

        if (body == null) {
            throw new KuraException(KuraErrorCode.INVALID_PARAMETER, null, null, "missing message body");
        }

        final int qos = options.getQos().getValue();
        
        final String fullTopic = fillTopicPlaceholders(options.getTopic(), message);
        
        final int id = this.dataService.publish(fullTopic, body, qos, options.getRetain(),
                options.getPriority());

        if (qos == 0) {
            return null;
        } else {
            return Integer.toString(id);
        }
    }
    
    private String fillTopicPlaceholders(String semanticTopic, KuraMessage message) {
        Matcher matcher = TOPIC_PATTERN.matcher(semanticTopic);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            Map<String, Object> properties = message.getProperties();
            if (properties.containsKey(matcher.group(1))) {
                String replacement = matcher.group(0);

                Object value = properties.get(matcher.group(1));
                if (replacement != null) {
                    matcher.appendReplacement(buffer, value.toString());
                }
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    @Override
    public synchronized void registerSubscriber(final Map<String, Object> subscriptionProperties,
            final CloudSubscriberListener cloudSubscriberListener) {

        final SubscribeOptions subscribeOptions;

        try {
            subscribeOptions = new SubscribeOptions(subscriptionProperties);
        } catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }

        registerSubscriber(subscribeOptions, cloudSubscriberListener);
    }

    public synchronized void registerSubscriber(final SubscribeOptions subscribeOptions,
            final CloudSubscriberListener cloudSubscriberListener) {

        final Set<CloudSubscriberListener> listeners = this.subscribers.computeIfAbsent(subscribeOptions,
                e -> new CopyOnWriteArraySet<>());

        listeners.add(cloudSubscriberListener);

        subscribe(subscribeOptions);
    }

    @Override
    public synchronized void unregisterSubscriber(CloudSubscriberListener cloudSubscriberListener) {
        final Set<SubscribeOptions> toUnsubscribe = new HashSet<>();

        this.subscribers.entrySet().removeIf(e -> {

            final Set<CloudSubscriberListener> listeners = e.getValue();

            listeners.remove(cloudSubscriberListener);

            if (listeners.isEmpty()) {
                toUnsubscribe.add(e.getKey());
                return true;
            } else {
                return false;
            }
        });

        toUnsubscribe.forEach(this::unsubscribe);

    }

    @Override
    public void registerCloudDeliveryListener(CloudDeliveryListener cloudDeliveryListener) {
        this.cloudDeliveryListeners.add(cloudDeliveryListener);
    }

    @Override
    public void unregisterCloudDeliveryListener(CloudDeliveryListener cloudDeliveryListener) {
        this.cloudDeliveryListeners.remove(cloudDeliveryListener);
    }

    @Override
    public void onConnectionEstablished() {
        this.cloudConnectionListeners.forEach(catchAll(CloudConnectionListener::onConnectionEstablished));

        synchronized (this) {
            this.subscribers.keySet().forEach(this::subscribe);
        }

        postConnectionStateChangeEvent(true);
    }

    @Override
    public void onDisconnecting() {
        // do nothing
    }

    @Override
    public void onDisconnected() {
        this.cloudConnectionListeners.forEach(catchAll(CloudConnectionListener::onDisconnected));

        postConnectionStateChangeEvent(false);
    }

    @Override
    public void onConnectionLost(Throwable cause) {
        this.cloudConnectionListeners.forEach(catchAll(CloudConnectionListener::onConnectionLost));

        postConnectionStateChangeEvent(false);
    }

    @Override
    public void onMessageArrived(final String topic, final byte[] payload, final int qos, final boolean retained) {
        logger.info("message arrived on topic {}", topic);

        final KuraPayload kuraPayload = new KuraPayload();
        kuraPayload.setBody(payload);

        final Map<String, Object> messagePropertes = Collections.singletonMap(Constants.TOPIC_PROP_NAME, topic);

        final KuraMessage message = new KuraMessage(kuraPayload, messagePropertes);

        for (final Entry<SubscribeOptions, Set<CloudSubscriberListener>> e : this.subscribers.entrySet()) {
            if (MqttTopicUtil.isMatched(e.getKey().getTopicFilter(), topic)) {
                e.getValue().forEach(catchAll(l -> l.onMessageArrived(message)));
            }
        }
    }

    @Override
    public void onMessagePublished(final int messageId, final String topic) {
        // do nothing
    }

    @Override
    public void onMessageConfirmed(int messageId, String topic) {
        this.cloudDeliveryListeners.forEach(catchAll(l -> l.onMessageConfirmed(Integer.toString(messageId))));
    }

    private void postConnectionStateChangeEvent(final boolean isConnected) {

        final Map<String, Object> eventProperties = Collections.singletonMap("cloud.service.pid",
                (String) this.componentContext.getProperties().get(ConfigurationService.KURA_SERVICE_PID));

        final Event event = isConnected ? new CloudConnectionEstablishedEvent(eventProperties)
                : new CloudConnectionLostEvent(eventProperties);
        this.eventAdmin.postEvent(event);
    }

    private void subscribe(final SubscribeOptions options) {
        try {
            final String topicFilter = options.getTopicFilter();
            final int qos = options.getQos().getValue();

            logger.info("subscribing to {} with qos {}", topicFilter, qos);
            this.dataService.subscribe(topicFilter, qos);
        } catch (final KuraNotConnectedException e) {
            logger.debug("failed to subscribe, DataService not connected");
        } catch (final Exception e) {
            logger.warn("failed to subscribe", e);
        }
    }

    private void unsubscribe(final SubscribeOptions options) {
        try {
            final String topicFilter = options.getTopicFilter();

            logger.info("unsubscribing from {}", topicFilter);
            this.dataService.unsubscribe(topicFilter);
        } catch (final KuraNotConnectedException e) {
            logger.debug("failed to unsubscribe, DataService not connected");
        } catch (final Exception e) {
            logger.warn("failed to unsubscribe", e);
        }
    }
}
