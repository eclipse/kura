/*******************************************************************************
 * Copyright (c) 2011, 2018 Eurotech and/or its affiliates
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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilterForwardChainRule {

    private static final Logger logger = LoggerFactory.getLogger(FilterForwardChainRule.class);

    private String rule;
    private String inputInterface;
    private String outputInterface;
    private String state;
    private String srcNetwork;
    private short srcMask;
    private String dstNetwork;
    private short dstMask;
    private String protocol;
    private String permittedMacAddress;
    private int srcPortFirst;
    private int srcPortLast;
    private int dstPort;
    private boolean matched;
    private String ruleTag;

    public FilterForwardChainRule(String inputInterface, String outputInterface, String srcNetwork, short srcMask,
            String dstNetwork, short dstMask, String protocol, String permittedMacAddress, int srcPortFirst,
            int srcPortLast) {
        this.inputInterface = inputInterface;
        this.outputInterface = outputInterface;
        this.srcNetwork = srcNetwork;
        this.srcMask = srcMask;
        this.dstNetwork = dstNetwork;
        this.dstMask = dstMask;
        this.protocol = protocol;
        this.permittedMacAddress = permittedMacAddress;
        this.srcPortFirst = srcPortFirst;
        this.srcPortLast = srcPortLast;
        this.ruleTag = "";
    }

    public FilterForwardChainRule(String rule) throws KuraException {
        this.ruleTag = "";
        try {
            String[] aRuleTokens = rule.split(" ");
            int i = 0;
            while (i < aRuleTokens.length) {
                if ("-i".equals(aRuleTokens[i])) {
                    this.inputInterface = aRuleTokens[++i];
                } else if ("-o".equals(aRuleTokens[i])) {
                    this.outputInterface = aRuleTokens[++i];
                } else if ("--state".equals(aRuleTokens[i])) {
                    this.state = aRuleTokens[++i];
                } else if ("-p".equals(aRuleTokens[i])) {
                    this.protocol = aRuleTokens[++i];
                } else if ("-s".equals(aRuleTokens[i])) {
                    this.srcNetwork = aRuleTokens[i + 1].split("/")[0];
                    this.srcMask = Short.parseShort(aRuleTokens[++i].split("/")[1]);
                } else if ("-d".equals(aRuleTokens[i])) {
                    this.dstNetwork = aRuleTokens[i + 1].split("/")[0];
                    this.dstMask = Short.parseShort(aRuleTokens[++i].split("/")[1]);
                } else if ("--comment".equals(aRuleTokens[i])) {
                    this.ruleTag = aRuleTokens[++i];
                }
                i++;
            }
            this.rule = new StringBuilder("iptables ").append(rule).toString();
        } catch (Exception e) {
            throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, e);
        }
    }

    public List<String> toStrings() {
        List<String> ret = new ArrayList<>();
        StringBuilder sb = new StringBuilder("-A FORWARD");
        if (this.srcNetwork != null) {
            sb.append(" -s ") //
                    .append(this.srcNetwork) //
                    .append('/') //
                    .append(this.srcMask);
        }
        if (this.dstNetwork != null) {
            sb.append(" -d ") //
                    .append(this.dstNetwork) //
                    .append('/') //
                    .append(this.dstMask);
        }
        sb.append(" -i ").append(this.inputInterface);
        sb.append(" -o ").append(this.outputInterface);
        if (this.protocol != null) {
            sb.append(" -p ").append(this.protocol);
            sb.append(" -m ").append(this.protocol);
        }
        if (this.permittedMacAddress != null) {
            sb.append(" -m mac --mac-source ").append(this.permittedMacAddress);
        }
        if (this.srcPortFirst > 0 && this.srcPortLast >= this.srcPortFirst) {
            sb.append(" --sport ") //
                    .append(this.srcPortFirst) //
                    .append(':') //
                    .append(this.srcPortLast);
        }
        if (this.dstPort > 0) {
            sb.append(" --dport ").append(this.dstPort);
        }
        sb.append(" -j ACCEPT");
        if (this.ruleTag != null) {
            sb.append(" -m comment --comment \"").append(this.ruleTag).append('"');
        }
        ret.add(sb.toString());
        sb = new StringBuilder("-A FORWARD");
        if (this.dstNetwork != null) {
            sb.append(" -s ") //
                    .append(this.dstNetwork) //
                    .append('/') //
                    .append(this.dstMask);
        }
        sb.append(" -i ").append(this.outputInterface);
        sb.append(" -o ").append(this.inputInterface);
        if (this.protocol != null) {
            sb.append(" -p ").append(this.protocol);
        }
        sb.append(" -m state --state RELATED,ESTABLISHED -j ACCEPT");
        if (this.ruleTag != null) {
            sb.append(" -m comment --comment \"").append(this.ruleTag).append('"');
        }
        ret.add(sb.toString());
        return ret;
    }

    @Override
    public int hashCode() {
        final int prime = 71;
        int result = 1;
        result = prime * result + (this.rule == null ? 0 : this.rule.hashCode());
        result = prime * result + (this.inputInterface == null ? 0 : this.inputInterface.hashCode());
        result = prime * result + (this.outputInterface == null ? 0 : this.outputInterface.hashCode());
        result = prime * result + (this.state == null ? 0 : this.state.hashCode());
        result = prime * result + (this.srcNetwork == null ? 0 : this.srcNetwork.hashCode());
        result = prime * result + this.srcMask;
        result = prime * result + (this.dstNetwork == null ? 0 : this.dstNetwork.hashCode());
        result = prime * result + this.dstMask;
        result = prime * result + (this.protocol == null ? 0 : this.protocol.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object o) {
        FilterForwardChainRule other;
        if (o instanceof FilterForwardChainRule) {
            other = (FilterForwardChainRule) o;
        } else if (o instanceof String) {
            try {
                other = new FilterForwardChainRule((String) o);
            } catch (KuraException e) {
                logger.error("equals() :: failed to parse FilterForwardChainRule ", e);
                return false;
            }
        } else {
            return false;
        }

        if (!compareObjects(this.rule, other.rule)) {
            return false;
        }
        if (!compareObjects(this.inputInterface, other.inputInterface)) {
            return false;
        }
        if (!compareObjects(this.outputInterface, other.outputInterface)) {
            return false;
        }
        if (!compareObjects(this.state, other.state)) {
            return false;
        }
        if (!compareObjects(this.srcNetwork, other.srcNetwork)) {
            return false;
        }
        if (this.srcMask != other.srcMask) {
            return false;
        }
        if (!compareObjects(this.dstNetwork, other.dstNetwork)) {
            return false;
        }
        if (this.dstMask != other.dstMask) {
            return false;
        }
        if (!compareObjects(this.protocol, other.protocol)) {
            return false;
        }
        return true;
    }

    private boolean compareObjects(Object obj1, Object obj2) {
        if (obj1 != null) {
            return obj1.equals(obj2);
        } else if (obj2 != null) {
            return false;
        }
        return true;
    }

    public boolean isInboundForwardRule() {
        return this.state != null ? true : false;
    }

    @Override
    public String toString() {
        return this.rule;
    }

    public String getInputInterface() {
        return this.inputInterface;
    }

    public String getOutputInterface() {
        return this.outputInterface;
    }

    public String getState() {
        return this.state;
    }

    public String getSrcNetwork() {
        return this.srcNetwork;
    }

    public short getSrcMask() {
        return this.srcMask;
    }

    public String getDstNetwork() {
        return this.dstNetwork;
    }

    public short getDstMask() {
        return this.dstMask;
    }

    public String getProtocol() {
        return this.protocol;
    }

    public boolean isMatched() {
        return this.matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public String getRuleTag() {
        return this.ruleTag;
    }

    public void setRuleTag(String ruleTag) {
        this.ruleTag = ruleTag;
    }
}
