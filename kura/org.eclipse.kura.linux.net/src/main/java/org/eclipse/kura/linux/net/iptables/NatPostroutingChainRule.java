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

public class NatPostroutingChainRule {

    private static final Logger logger = LoggerFactory.getLogger(NatPostroutingChainRule.class);
    private static final String ZERO_IPV4_ADDRESS = "0.0.0.0";

    private String rule;
    private String dstNetwork;
    private short dstMask;
    private String srcNetwork;
    private short srcMask;
    private String dstInterface;
    private String protocol;
    private boolean masquerade;
    private String ruleTag;

    public NatPostroutingChainRule(String dstNetwork, short dstMask, String srcNetwork, short srcMask,
            String dstInterface, String protocol, boolean masquerade) {
        this.dstNetwork = dstNetwork;
        this.dstMask = dstMask;
        this.srcNetwork = srcNetwork;
        this.srcMask = srcMask;
        this.dstInterface = dstInterface;
        this.protocol = protocol;
        this.masquerade = masquerade;
        this.ruleTag = "";
    }

    public NatPostroutingChainRule(String dstInterface, boolean masquerade) {
        this.dstInterface = dstInterface;
        this.masquerade = masquerade;
        StringBuilder sbRule = new StringBuilder("iptables -t nat -o ");
        sbRule.append(this.dstInterface);
        if (this.masquerade) {
            sbRule.append(" -j MASQUERADE");
        }
        this.rule = sbRule.toString();
        this.ruleTag = "";
    }

    public NatPostroutingChainRule(String dstInterface, String protocol, String dstNetwork, String srcNetwork,
            boolean masquerade) throws KuraException {
        try {
            this.dstInterface = dstInterface;
            this.protocol = protocol;
            this.masquerade = masquerade;
            if (dstNetwork != null) {
                this.dstNetwork = dstNetwork.split("/")[0];
                this.dstMask = Short.parseShort(dstNetwork.split("/")[1]);
            }
            if (srcNetwork != null) {
                this.srcNetwork = srcNetwork.split("/")[0];
                this.srcMask = Short.parseShort(srcNetwork.split("/")[1]);
            }
            StringBuilder sbRule = new StringBuilder("iptables -t nat ");
            if (this.dstNetwork != null) {
                sbRule.append("-d ");
                sbRule.append(this.dstNetwork);
                sbRule.append('/');
                sbRule.append(this.dstMask);
            }
            if (this.srcNetwork != null) {
                sbRule.append("-s ");
                sbRule.append(this.srcNetwork);
                sbRule.append('/');
                sbRule.append(this.srcMask);
            }
            sbRule.append("-o ");
            sbRule.append(this.dstInterface);
            sbRule.append("-p ");
            sbRule.append(this.protocol);
            if (this.masquerade) {
                sbRule.append(" -j MASQUERADE");
            }
            this.rule = sbRule.toString();
            this.ruleTag = "";
        } catch (Exception e) {
            throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, e);
        }
    }

    public NatPostroutingChainRule(String rule) throws KuraException {
        this.ruleTag = "";
        try {
            String[] aRuleTokens = rule.split(" ");
            int i = 0;
            while (i < aRuleTokens.length) {
                if ("-o".equals(aRuleTokens[i])) {
                    this.dstInterface = aRuleTokens[++i];
                } else if ("-p".equals(aRuleTokens[i])) {
                    this.protocol = aRuleTokens[++i];
                } else if ("-s".equals(aRuleTokens[i])) {
                    this.srcNetwork = aRuleTokens[i + 1].split("/")[0];
                    this.srcMask = Short.parseShort(aRuleTokens[++i].split("/")[1]);
                } else if ("-d".equals(aRuleTokens[i])) {
                    this.dstNetwork = aRuleTokens[i + 1].split("/")[0];
                    this.dstMask = Short.parseShort(aRuleTokens[++i].split("/")[1]);
                } else if ("-j".equals(aRuleTokens[i])) {
                    if ("MASQUERADE".equals(aRuleTokens[++i])) {
                        this.masquerade = true;
                    }
                } else if ("--comment".equals(aRuleTokens[i])) {
                    this.ruleTag = aRuleTokens[++i];
                }
                i++;
            }
            this.rule = new StringBuilder("iptables -t nat ").append(rule).toString();
        } catch (Exception e) {
            throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, e);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (this.masquerade) {
            sb.append("-A POSTROUTING");
            if (this.srcNetwork != null && !ZERO_IPV4_ADDRESS.equals(this.srcNetwork)) {
                sb.append(" -s ").append(this.srcNetwork).append('/').append(this.srcMask);
            }
            if (this.dstNetwork != null) {
                sb.append(" -d ").append(this.dstNetwork).append('/').append(this.dstMask);
            }
            sb.append(" -o ").append(this.dstInterface);

            if (this.protocol != null) {
                sb.append(" -p ").append(this.protocol);
            }
            sb.append(" -j MASQUERADE");
            if (this.ruleTag != null) {
                sb.append(" -m comment --comment \"").append(this.ruleTag).append('"');
            }
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 71;
        int result = 1;
        result = prime * result + (this.rule == null ? 0 : this.rule.hashCode());
        result = prime * result + (this.dstNetwork == null ? 0 : this.dstNetwork.hashCode());
        result = prime * result + this.dstMask;
        result = prime * result + (this.srcNetwork == null ? 0 : this.srcNetwork.hashCode());
        result = prime * result + this.srcMask;
        result = prime * result + (this.dstInterface == null ? 0 : this.dstInterface.hashCode());
        result = prime * result + (this.protocol == null ? 0 : this.protocol.hashCode());
        result = prime * result + (this.masquerade ? 1277 : 1279);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        NatPostroutingChainRule other;
        if (o instanceof NatPostroutingChainRule) {
            other = (NatPostroutingChainRule) o;
        } else if (o instanceof String) {
            try {
                other = new NatPostroutingChainRule((String) o);
            } catch (KuraException e) {
                logger.error("equals() :: failed to parse NatPostroutingChainRule ", e);
                return false;
            }
        } else {
            return false;
        }

        if (!compareObjects(this.rule, other.rule)) {
            return false;
        }
        if (!compareObjects(this.dstNetwork, other.dstNetwork)) {
            return false;
        }
        if (this.dstMask != other.dstMask) {
            return false;
        }
        if (!compareObjects(this.srcNetwork, other.srcNetwork)) {
            return false;
        }
        if (this.srcMask != other.srcMask) {
            return false;
        }
        if (!compareObjects(this.dstInterface, other.dstInterface)) {
            return false;
        }
        if (!compareObjects(this.protocol, other.protocol)) {
            return false;
        }
        if (this.masquerade != other.masquerade) {
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

    private boolean isMatchingForwardChainRule(FilterForwardChainRule forwardChainRule) {
        if (!this.dstInterface.equals(forwardChainRule.getOutputInterface())) {
            return false;
        }
        if (this.protocol != null) {
            if (!this.protocol.equals(forwardChainRule.getProtocol())) {
                return false;
            }
        } else {
            if (forwardChainRule.getProtocol() != null) {
                return false;
            }
        }
        if (this.srcNetwork != null) {
            if (!this.srcNetwork.equals(forwardChainRule.getSrcNetwork())) {
                return false;
            }
        } else {
            if (forwardChainRule.getSrcNetwork() != null) {
                return false;
            }
        }
        if (this.dstNetwork != null) {
            if (!this.dstNetwork.equals(forwardChainRule.getDstNetwork())) {
                return false;
            }
        } else {
            if (forwardChainRule.getDstNetwork() != null) {
                return false;
            }
        }
        if (this.srcMask != forwardChainRule.getSrcMask()) {
            return false;
        }
        if (this.dstMask != forwardChainRule.getDstMask()) {
            return false;
        }
        return true;
    }

    public PostRoutingRuleType getRuleType() {
        if (this.ruleTag.startsWith(NATRule.RULE_TAG)) {
            return PostRoutingRuleType.NAT;
        } else if (this.ruleTag.startsWith(PortForwardRule.RULE_TAG)) {
            return PostRoutingRuleType.PF;
        }
        if (this.protocol == null) {
            return PostRoutingRuleType.ANAT;

        } else {
            return PostRoutingRuleType.UNKNOWN;
        }
    }

    public List<FilterForwardChainRule> getMatchingForwardChainRules(List<FilterForwardChainRule> filterForwardChain) {
        List<FilterForwardChainRule> matchingForwardChainRules = new ArrayList<>();
        for (FilterForwardChainRule filterForwardChainRule : filterForwardChain) {
            if (this.ruleTag.equals(filterForwardChainRule.getRuleTag())) {
                matchingForwardChainRules.add(filterForwardChainRule);
            }
        }
        if (matchingForwardChainRules.isEmpty()) {
            for (FilterForwardChainRule filterForwardChainRule : filterForwardChain) {
                if (isMatchingForwardChainRule(filterForwardChainRule)) {
                    matchingForwardChainRules.add(filterForwardChainRule);
                }
            }
        }
        return matchingForwardChainRules;
    }

    public String getDstNetwork() {
        return this.dstNetwork;
    }

    public short getDstMask() {
        return this.dstMask;
    }

    public String getSrcNetwork() {
        return this.srcNetwork;
    }

    public short getSrcMask() {
        return this.srcMask;
    }

    public String getDstInterface() {
        return this.dstInterface;
    }

    public String getProtocol() {
        return this.protocol;
    }

    public boolean isMasquerade() {
        return this.masquerade;
    }

    public String getRuleTag() {
        return this.ruleTag;
    }

    public void setRuleTag(String ruleTag) {
        this.ruleTag = ruleTag;
    }

}
