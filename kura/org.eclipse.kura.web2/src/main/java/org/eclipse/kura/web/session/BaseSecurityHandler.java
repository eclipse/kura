/*******************************************************************************
 * Copyright (c) 2019, 2020 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.web.session;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseSecurityHandler implements SecurityHandler {

    private static final Logger logger = LoggerFactory.getLogger(BaseSecurityHandler.class);

    @Override
    public boolean handleSecurity(final HttpServletRequest request, final HttpServletResponse response)
            throws IOException {
        response.setHeader("X-FRAME-OPTIONS", "SAMEORIGIN");
        response.setHeader("X-XSS-protection", "1; mode=block");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Content-Security-Policy", "default-src 'self'; font-src 'self' data:; img-src 'self' data:; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'");

        fixTrailingSlashes(request, response);

        return true;
    }

    private static void fixTrailingSlashes(final HttpServletRequest request, final HttpServletResponse response) {

        final String path = request.getRequestURI();

        if (!path.endsWith("/") || "/".contentEquals(path) || path.isEmpty()) {
            return;
        }

        int end = path.length() - 1;
        for (; end > 1 && path.charAt(end) == '/'; end--) {
            ;
        }

        try {
            response.sendRedirect(path.substring(0, end + 1));
        } catch (IOException e) {
            logger.warn("unexpected exception", e);
        }
    }
}
