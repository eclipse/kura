/*******************************************************************************
 * Copyright (c) 2011, 2020 Eurotech and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech
 *******************************************************************************/
package org.eclipse.kura.web.server.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.kura.configuration.ComponentConfiguration;
import org.eclipse.kura.configuration.ConfigurationService;
import org.eclipse.kura.core.configuration.XmlComponentConfigurations;
import org.eclipse.kura.marshalling.Marshaller;
import org.eclipse.kura.util.service.ServiceUtil;
import org.eclipse.kura.web.server.KuraRemoteServiceServlet;
import org.eclipse.kura.web.server.util.ServiceLocator;
import org.eclipse.kura.web.session.Attributes;
import org.eclipse.kura.web.shared.model.GwtXSRFToken;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeviceSnapshotsServlet extends HttpServlet {

    private static final long serialVersionUID = -2533869595709953567L;

    private static Logger logger = LoggerFactory.getLogger(DeviceSnapshotsServlet.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AuditLogger");

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // BEGIN XSRF - Servlet dependent code

        try {
            GwtXSRFToken token = new GwtXSRFToken(request.getParameter("xsrfToken"));
            KuraRemoteServiceServlet.checkXSRFToken(request, token);
        } catch (Exception e) {
            throw new ServletException("Security error: please retry this operation correctly.", e);
        }
        // END XSRF security check

        HttpSession session = request.getSession(false);

        String snapshotId = request.getParameter("snapshotId");

        try (PrintWriter writer = response.getWriter();) {

            ServiceLocator locator = ServiceLocator.getInstance();
            ConfigurationService cs = locator.getService(ConfigurationService.class);
            if (snapshotId != null) {

                long sid = Long.parseLong(snapshotId);
                List<ComponentConfiguration> configs = cs.getSnapshot(sid);

                // build a list of configuration which can be marshalled in XML
                List<ComponentConfiguration> configImpls = new ArrayList<>();
                for (ComponentConfiguration config : configs) {
                    configImpls.add(config);
                }
                XmlComponentConfigurations xmlConfigs = new XmlComponentConfigurations();
                xmlConfigs.setConfigurations(configImpls);

                //
                // marshall the response and write it
                String result = marshal(xmlConfigs);

                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/xml");
                response.setHeader("Content-Disposition", "attachment; filename=snapshot_" + sid + ".xml");
                response.setHeader("Cache-Control", "no-transform, no-cache, max-age=0");

                writer.write(result);

                auditLogger.info(
                        "UI Snapshots - Success - Successfully returned device snapshot for user: {}, session: {}, snapshot id: {}",
                        session.getAttribute(Attributes.AUTORIZED_USER.getValue()), session.getId(), snapshotId);
            }
        } catch (Exception e) {
            logger.error("Error exporting snapshot");
            auditLogger.warn("UI Snapshots - Failure - Failed to export device snapshot for user: {}, session: {}",
                    session.getAttribute(Attributes.AUTORIZED_USER.getValue()), session.getId(), e);
            throw new ServletException(e);
        }
    }

    private ServiceReference<Marshaller>[] getXmlMarshallers() {
        String filterString = String.format("(&(kura.service.pid=%s))",
                "org.eclipse.kura.xml.marshaller.unmarshaller.provider");
        return ServiceUtil.getServiceReferences(
                FrameworkUtil.getBundle(DeviceSnapshotsServlet.class).getBundleContext(), Marshaller.class,
                filterString);
    }

    private void ungetServiceReferences(final ServiceReference<?>[] refs) {
        ServiceUtil.ungetServiceReferences(FrameworkUtil.getBundle(DeviceSnapshotsServlet.class).getBundleContext(),
                refs);
    }

    protected String marshal(Object object) {
        String result = null;
        ServiceReference<Marshaller>[] marshallerSRs = getXmlMarshallers();
        try {
            for (final ServiceReference<Marshaller> marshallerSR : marshallerSRs) {
                Marshaller marshaller = FrameworkUtil.getBundle(DeviceSnapshotsServlet.class).getBundleContext()
                        .getService(marshallerSR);
                result = marshaller.marshal(object);
                if (result != null) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to marshal configuration.");
        } finally {
            ungetServiceReferences(marshallerSRs);
        }
        return result;
    }
}
