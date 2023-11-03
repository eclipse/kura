/*******************************************************************************
 * Copyright (c) 2023 Eurotech and/or its affiliates and others
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
package org.eclipse.kura.internal.rest.cloudconnection.provider.dto;

public class PidsDTO {

    private String pid;
    private String factoryPid;
    private String cloudConnectionPid;
    private String cloudServicePid;
    private String cloudServicePidRegex;

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getFactoryPid() {
        return factoryPid;
    }

    public void setFactoryPid(String factoryPid) {
        this.factoryPid = factoryPid;
    }

    public String getCloudConnectionPid() {
        return cloudConnectionPid;
    }

    public void setCloudConnectionPid(String cloudConnectionPid) {
        this.cloudConnectionPid = cloudConnectionPid;
    }

    public String getCloudServicePid() {
        return cloudServicePid;
    }

    public void setCloudServicePid(String cloudServicePid) {
        this.cloudServicePid = cloudServicePid;
    }

    public String getCloudServicePidRegex() {
        return cloudServicePidRegex;
    }

    public void setCloudServicePidRegex(String cloudServicePidRegex) {
        this.cloudServicePidRegex = cloudServicePidRegex;
    }

}
