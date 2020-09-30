/*******************************************************************************
 * Copyright (c) 2018, 2020 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Eurotech
 *
 *******************************************************************************/
package org.eclipse.kura.web.server.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.ws.http.HTTPException;

import org.eclipse.kura.web.server.KuraRemoteServiceServlet;
import org.eclipse.kura.web.session.Attributes;
import org.eclipse.kura.web.shared.model.GwtXSRFToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelServlet extends HttpServlet {

    private static final long serialVersionUID = -1445700937173920652L;

    private static Logger logger = LoggerFactory.getLogger(ChannelServlet.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AuditLogger");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        // BEGIN XSRF - Servlet dependent code
        try {
            GwtXSRFToken token = new GwtXSRFToken(req.getParameter("xsrfToken"));
            KuraRemoteServiceServlet.checkXSRFToken(req, token);
            // END XSRF security check
            String assetPid = req.getParameter("assetPid");
            String id = req.getParameter("id");

            final String attributeKey = "kura.csv.download." + id;

            final String result = (String) session.getAttribute(attributeKey);

            if (result == null) {
                throw new HTTPException(404);
            }

            session.removeAttribute(attributeKey);

            resp.setCharacterEncoding("UTF-8");
            resp.setContentType("text/csv");
            resp.setHeader("Content-Disposition", "attachment; filename=asset_" + assetPid + ".csv");
            resp.setHeader("Cache-Control", "no-transform, no-cache, max-age=0");
            try (PrintWriter writer = resp.getWriter()) {
                writer.write(result);
            }

            auditLogger.info(
                    "UI Channel Servlet - Success - Successfully wrote Channel CSV description for user: {}, session: {}, asset pid: {}",
                    session.getAttribute(Attributes.AUTORIZED_USER.getValue()), session.getId(), assetPid);
        } catch (Exception ex) {
            logger.error("Error while exporting CSV output!", ex);
            auditLogger.warn(
                    "UI Channel Servlet - Failure - Failed to write Channel CSV description for user: {}, session: {}",
                    session.getAttribute(Attributes.AUTORIZED_USER.getValue()), session.getId());
        }
    }

}
