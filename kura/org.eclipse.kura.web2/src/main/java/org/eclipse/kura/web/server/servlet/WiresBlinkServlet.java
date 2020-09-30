/*******************************************************************************
 * Copyright (c) 2016, 2020 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Eurotech
 *  Amit Kumar Mondal
 *
 *******************************************************************************/
package org.eclipse.kura.web.server.servlet;

import static org.eclipse.kura.util.base.StringUtil.isNullOrEmpty;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.kura.wire.graph.Constants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.wireadmin.Wire;
import org.osgi.service.wireadmin.WireAdminEvent;
import org.osgi.service.wireadmin.WireAdminListener;
import org.osgi.service.wireadmin.WireConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class EventHandlerServlet is responsible for interacting between Event
 * Admin and Javascript through Server Sent Events (SSE). This is mainly required
 * for Kura Wires to delegate the emit events.
 */
public final class WiresBlinkServlet extends HttpServlet implements WireAdminListener {

    /** The Logger Instance. */
    private static final Logger logger = LoggerFactory.getLogger(WiresBlinkServlet.class);

    /** Session Timeout in Seconds - 5 minutes */
    private static final int SESSION_DURATION_MS = 5 * 60 * 1000;

    private static final int MIN_EVENT_DELAY_MS = 400;

    private static final Dictionary<String, Object> WIRE_EVENT_LISTENER_PROPERTIES = new Hashtable<>();

    static {
        WIRE_EVENT_LISTENER_PROPERTIES.put(WireConstants.WIREADMIN_EVENTS, WireAdminEvent.WIRE_TRACE);
    }

    /**
     * Maximum size of the queue to maintain the events. The queue does not care if any
     * event maintained by the queue is rejected. As long as a consumer thread can consume
     * events from the queue, the system behavior is considered as expected.
     */
    private static final int MAX_SIZE_OF_QUEUE = 10;

    /** Serial Version */
    private static final long serialVersionUID = -8962416452919656283L;

    /** Bundle Context */
    private static final BundleContext bundleContext = FrameworkUtil.getBundle(WiresBlinkServlet.class)
            .getBundleContext();

    /** Used to track the new sessions */
    private static Map<String, RequestContext> requests = new ConcurrentHashMap<>();

    private static ServiceRegistration<WireAdminListener> registration;

    /** {@inheritDoc} */
    @Override
    public void destroy() {
        super.destroy();
        requests.clear();
    }

    /**
     * Performs a GET request for Server Sent Event Value.
     *
     * @param request
     *            the request
     * @param response
     *            the response
     * @throws ServletException
     *             the servlet exception
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    @Override
    public void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        // track the new session
        final String requestId = request.getParameter("session");

        if (isNullOrEmpty(requestId)) {
            try {
                response.sendError(400);
            } catch (final Exception e) {
                logger.warn("Failed to send status");
            }
            return;
        }

        // destroy the session if exists. It actually destroys the current session which initiated
        // the destruction request
        final String sessionToDestroy = request.getParameter("logout");

        if (!isNullOrEmpty(sessionToDestroy)) {
            removeContext(requestId);
            return;
        }

        // set the response headers for SSE
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-control", "no-cache, max-age=0");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Access-Control-Allow-Origin", "*"); // required for IE9
        response.setHeader("Content-Encoding", "identity"); // allow compressed data

        final RequestContext context;

        synchronized (this) {
            final OutputStream outputStream;

            try {
                outputStream = response.getOutputStream();
            } catch (final Exception e) {
                logger.warn("failed to open response stream");
                return;
            }

            context = new RequestContext(requestId, outputStream);
            addContext(context);
        }

        context.run();
    }

    private synchronized void addContext(final RequestContext context) {
        final String requestId = context.requestId;
        removeContext(requestId);
        requests.put(requestId, context);
        if (registration == null) {
            registration = bundleContext.registerService(WireAdminListener.class, this, WIRE_EVENT_LISTENER_PROPERTIES);
            logger.info("registered");
        }
    }

    private synchronized void removeContext(final String requestId) {
        final RequestContext context = requests.remove(requestId);
        if (context != null) {
            context.close();
        }
        if (requests.isEmpty() && registration != null) {
            registration.unregister();
            logger.info("unregistered");
            registration = null;
        }
    }

    private final class RequestContext {

        private final String requestId;
        private final OutputStream outputStream;
        private final PrintStream printStream;
        private final Map<WireEvent, Long> lastSentTimestamp = new HashMap<>();
        private final LinkedBlockingQueue<Wire> events = new LinkedBlockingQueue<>(MAX_SIZE_OF_QUEUE);

        private boolean run;

        RequestContext(final String requestId, final OutputStream outputStream) {
            this.requestId = requestId;
            this.outputStream = outputStream;
            this.printStream = new PrintStream(outputStream);
            this.run = true;
        }

        private boolean shouldDispatch(final WireEvent event) {
            final long previous = this.lastSentTimestamp.getOrDefault(event, 0L);

            return System.currentTimeMillis() - previous > MIN_EVENT_DELAY_MS;
        }

        private boolean processEvent(final long startTime) {
            final Wire wire;

            if (!(System.currentTimeMillis() - startTime < SESSION_DURATION_MS && this.run)) {
                return false;
            }

            try {
                wire = this.events.poll(1, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }

            if (wire == null) {
                return true;
            }

            final WireEvent wireEvent = WireEvent.from(wire);

            if (wireEvent == null || !shouldDispatch(wireEvent)) {
                return true;
            }

            try {
                this.printStream.printf("data: %s %s%n%n", wireEvent.emitterKuraServicePid, wireEvent.emitterPort);
                this.printStream.flush();

                this.lastSentTimestamp.put(wireEvent, System.currentTimeMillis());
                return true;
            } catch (final Exception e) {
                return false;
            }
        }

        void run() {
            logger.info("Session started: {}", this.requestId);

            final long startTime = System.currentTimeMillis();

            while (processEvent(startTime)) {
                ;
            }

            logger.info("Session ended: {}", this.requestId);

            try {
                this.outputStream.close();
            } catch (Exception e) {
                logger.warn("failed to close stream", e);
            }

            removeContext(this.requestId);
        }

        boolean submit(final Wire wire) {

            return this.events.offer(wire);
        }

        void close() {
            this.run = false;
        }
    }

    private static final class WireEvent {

        final String emitterKuraServicePid;
        final String emitterPort;

        static WireEvent from(final Wire wire) {
            if (wire == null) {
                return null;
            }

            final Dictionary<?, ?> properties = wire.getProperties();
            final Object pid = properties.get(Constants.EMITTER_KURA_SERVICE_PID_PROP_NAME.value());
            final Object port = properties.get(Constants.WIRE_EMITTER_PORT_PROP_NAME.value());
            if (pid != null && port != null) {
                return new WireEvent(pid.toString(), port.toString());
            }
            return null;
        }

        WireEvent(final String emitterKuraServicePid, final String emitterPort) {
            this.emitterKuraServicePid = emitterKuraServicePid;
            this.emitterPort = emitterPort;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (this.emitterKuraServicePid == null ? 0 : this.emitterKuraServicePid.hashCode());
            result = prime * result + (this.emitterPort == null ? 0 : this.emitterPort.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            WireEvent other = (WireEvent) obj;
            if (this.emitterKuraServicePid == null) {
                if (other.emitterKuraServicePid != null) {
                    return false;
                }
            } else if (!this.emitterKuraServicePid.equals(other.emitterKuraServicePid)) {
                return false;
            }
            if (this.emitterPort == null) {
                if (other.emitterPort != null) {
                    return false;
                }
            } else if (!this.emitterPort.equals(other.emitterPort)) {
                return false;
            }
            return true;
        }
    }

    @Override
    public void wireAdminEvent(final WireAdminEvent event) {

        final Wire wire = event.getWire();

        for (final RequestContext context : requests.values()) {
            context.submit(wire);
        }
    }

}