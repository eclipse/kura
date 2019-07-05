/*******************************************************************************
 * Copyright (c) 2018 Eurotech and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech
 *******************************************************************************/

package org.eclipse.kura.linux.net.iptables;

public enum PostRoutingRuleType {

    UNKNOWN(0),
    ANAT(1),
    NAT(2),
    PF(3);

    private int type;

    private PostRoutingRuleType(int type) {
        this.type = type;
    }

    public int getValue() {
        return this.type;
    }

    @Override
    public String toString() {
        String ret;
        switch (this.type) {
        case 1:
            ret = "AUTO-NAT";
            break;
        case 2:
            ret = "NAT";
            break;
        case 3:
            ret = "PortForward";
            break;
        case 0:
        default:
            ret = "UNKNOWN";
        }
        return ret;
    }

    public static PostRoutingRuleType getPostRoutingRuleType(int type) {
        PostRoutingRuleType ret;
        switch (type) {
        case 1:
            ret = ANAT;
            break;
        case 2:
            ret = NAT;
            break;
        case 3:
            ret = PF;
            break;
        case 0:
        default:
            ret = UNKNOWN;
        }
        return ret;
    }
}
