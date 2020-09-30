/*******************************************************************************
 * Copyright (c) 2018, 2020 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.web.server.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.kura.asset.provider.AssetConstants;
import org.eclipse.kura.configuration.ComponentConfiguration;
import org.eclipse.kura.configuration.ConfigurationService;
import org.eclipse.kura.core.configuration.ComponentConfigurationImpl;
import org.eclipse.kura.core.configuration.XmlComponentConfigurations;
import org.eclipse.kura.marshalling.Marshaller;
import org.eclipse.kura.web.server.GwtWireGraphServiceImpl;
import org.eclipse.kura.web.server.KuraRemoteServiceServlet;
import org.eclipse.kura.web.server.util.ServiceLocator;
import org.eclipse.kura.web.session.Attributes;
import org.eclipse.kura.web.shared.GwtKuraException;
import org.eclipse.kura.web.shared.model.GwtXSRFToken;
import org.eclipse.kura.wire.graph.WireComponentConfiguration;
import org.eclipse.kura.wire.graph.WireGraphService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WiresSnapshotServlet extends HttpServlet {

    private static final String WIRE_GRAPH_SERVICE_PID = "org.eclipse.kura.wire.graph.WireGraphService";
    private static final String WIRE_ASSET_FACTORY_PID = "org.eclipse.kura.wire.WireAsset";

    private static final long serialVersionUID = -7483037360719617846L;
    private static final Logger logger = LoggerFactory.getLogger(WiresSnapshotServlet.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AuditLogger");

    private String toSnapshot(XmlComponentConfigurations configs) throws GwtKuraException {
        final BundleContext context = FrameworkUtil.getBundle(GwtWireGraphServiceImpl.class).getBundleContext();
        ServiceReference<Marshaller> marshallerRef = null;
        Marshaller marshaller = null;
        try {
            marshallerRef = context
                    .getServiceReferences(Marshaller.class,
                            "(kura.service.pid=org.eclipse.kura.xml.marshaller.unmarshaller.provider)")
                    .iterator().next();

            marshaller = context.getService(marshallerRef);
            return marshaller.marshal(configs);
        } catch (Exception e) {
            throw new GwtKuraException(e.getMessage());
        } finally {
            if (marshaller != null) {
                context.ungetService(marshallerRef);
            }
        }
    }

    private Set<String> findReferencedDrivers(List<ComponentConfiguration> configs) {
        return configs.stream().map(config -> {
            final Map<String, Object> configurationProperties = config.getConfigurationProperties();
            if (configurationProperties == null) {
                return null;
            }
            if (!WIRE_ASSET_FACTORY_PID.equals(configurationProperties.get(ConfigurationAdmin.SERVICE_FACTORYPID))) {
                return null;
            }
            final Object driverPid = configurationProperties.get(AssetConstants.ASSET_DRIVER_PROP.value());
            if (!(driverPid instanceof String)) {
                return null;
            }
            return (String) driverPid;
        }).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {
            GwtXSRFToken token = new GwtXSRFToken(request.getParameter("xsrfToken"));
            KuraRemoteServiceServlet.checkXSRFToken(request, token);
        } catch (Exception e) {
            throw new ServletException("Security error: please retry this operation correctly.", e);
        }

        HttpSession session = request.getSession(false);

        try {
            final List<ComponentConfiguration> result = new ArrayList<>();

            ServiceLocator.applyToServiceOptionally(WireGraphService.class, wireGraphService -> {
                wireGraphService.get().getWireComponentConfigurations().stream()
                        .map(WireComponentConfiguration::getConfiguration).map(WiresSnapshotServlet::removeDefinition)
                        .forEach(result::add);
                return null;
            });

            final Set<String> driverPids = findReferencedDrivers(result);

            ServiceLocator.applyToServiceOptionally(ConfigurationService.class, configurationService -> {
                configurationService.getComponentConfigurations().stream()
                        .filter(config -> driverPids.contains(config.getPid()))
                        .map(WiresSnapshotServlet::removeDefinition).forEach(result::add);
                result.add(removeDefinition(configurationService.getComponentConfiguration(WIRE_GRAPH_SERVICE_PID)));
                return null;
            });

            final XmlComponentConfigurations xmlConfigs = new XmlComponentConfigurations();
            xmlConfigs.setConfigurations(result);

            final String marshalled = toSnapshot(xmlConfigs);

            final String snapshotName = "graph_snapshot_" + System.currentTimeMillis() + ".xml";

            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/xml");
            response.setHeader("Content-Disposition", "attachment; filename=" + snapshotName);
            response.setHeader("Cache-Control", "no-transform, no-cache, max-age=0");

            try (PrintWriter writer = response.getWriter()) {
                writer.write(marshalled);
            }

            auditLogger.info(
                    "UI Wires Snapshots - Success - Successfully generated wire graph snapshot for user: {}, session: {}, generated file name: {}",
                    session.getAttribute(Attributes.AUTORIZED_USER.getValue()), session.getId(), snapshotName);
        } catch (Exception e) {
            logger.warn("Failed to download snapshot", e);
            auditLogger.warn("UI Wires Snapshots - Failure - Failed to get wires snapshot for user: {}, session: {}",
                    session.getAttribute(Attributes.AUTORIZED_USER.getValue()), session.getId());
            throw new ServletException("Failed to download snapshot");
        }
    }

    private static ComponentConfiguration removeDefinition(final ComponentConfiguration config) {
        return new ComponentConfigurationImpl(config.getPid(), null, config.getConfigurationProperties());
    }
}
