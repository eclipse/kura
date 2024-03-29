/*******************************************************************************
 * Copyright (c) 2011, 2023 Eurotech and/or its affiliates and others
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
package org.eclipse.kura.linux.net.dhcp;

public enum DhcpServerTool {
    NONE("none"),
    DHCPD("dhcpd"),
    UDHCPD("udhcpd"),
    DNSMASQ("dnsmasq");

    private String toolName;

    private DhcpServerTool(String toolName) {
        this.toolName = toolName;
    }

    public String getValue() {
        return this.toolName;
    }
}
