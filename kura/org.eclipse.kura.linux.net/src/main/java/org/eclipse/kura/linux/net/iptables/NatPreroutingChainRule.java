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

public class NatPreroutingChainRule {

    private static final Logger logger = LoggerFactory.getLogger(NatPreroutingChainRule.class);
    private static final String ZERO_IPV4_ADDRESS = "0.0.0.0";

    private String rule;
    private String inputInterface;
    private String protocol;
    private int externalPort;
    private int internalPort;
    private int srcPortFirst;
    private int srcPortLast;
    private String dstIpAddress;
    private String permittedNetwork;
    private int permittedNetworkMask;
    private String permittedMacAddress;
    private String ruleTag;

    public NatPreroutingChainRule(String inputInterface, String protocol, int externalPort, int internalPort,
            int srcPortFirst, int srcPortLast, String dstIpAddress, String permittedNetwork, int permittedNetworkMask,
            String permittedMacAddress) {
        this.inputInterface = inputInterface;
        this.protocol = protocol;
        this.externalPort = externalPort;
        this.internalPort = internalPort;
        this.srcPortFirst = srcPortFirst;
        this.srcPortLast = srcPortLast;
        this.dstIpAddress = dstIpAddress;
        this.permittedNetwork = permittedNetwork;
        this.permittedNetworkMask = permittedNetworkMask;
        this.permittedMacAddress = permittedMacAddress;
        this.ruleTag = "";
    }

    public NatPreroutingChainRule(String rule) throws KuraException {
        this.ruleTag = "";
        try {
            String[] aRuleTokens = rule.split(" ");
            int i = 0;
            while (i < aRuleTokens.length) {
                if ("-i".equals(aRuleTokens[i])) {
                    this.inputInterface = aRuleTokens[++i];
                } else if ("-p".equals(aRuleTokens[i])) {
                    this.protocol = aRuleTokens[++i];
                } else if ("--dport".equals(aRuleTokens[i])) {
                    this.externalPort = Integer.parseInt(aRuleTokens[++i]);
                } else if ("--sport".equals(aRuleTokens[i])) {
                    if (aRuleTokens[i + 1].indexOf(':') > 0) {
                        this.srcPortFirst = Integer.parseInt(aRuleTokens[i + 1].split(":")[0]);
                        this.srcPortLast = Integer.parseInt(aRuleTokens[++i].split(":")[1]);
                    } else {
                        this.srcPortFirst = Integer.parseInt(aRuleTokens[++i]);
                        this.srcPortLast = this.srcPortFirst;
                    }
                } else if ("--to-destination".equals(aRuleTokens[i])) {
                    this.dstIpAddress = aRuleTokens[i + 1].split(":")[0];
                    this.internalPort = Integer.parseInt(aRuleTokens[++i].split(":")[1]);
                } else if ("-s".equals(aRuleTokens[i])) {
                    this.permittedNetwork = aRuleTokens[i + 1].split("/")[0];
                    this.permittedNetworkMask = Integer.parseInt(aRuleTokens[++i].split("/")[1]);
                } else if ("--mac-source".equals(aRuleTokens[i])) {
                    this.permittedMacAddress = aRuleTokens[++i];
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

    public NatPostroutingChainRule getMatchingPostRoutingChainRule(List<NatPostroutingChainRule> natPostroutingChain) {
        NatPostroutingChainRule matchingNatPostroutingChainRule = null;
        for (NatPostroutingChainRule natPostroutingChainRule : natPostroutingChain) {
            if (this.ruleTag.equals(natPostroutingChainRule.getRuleTag())) {
                matchingNatPostroutingChainRule = natPostroutingChainRule;
                break;
            }
        }
        if (matchingNatPostroutingChainRule == null) {
            for (NatPostroutingChainRule natPostroutingChainRule : natPostroutingChain) {
                if (this.dstIpAddress.equals(natPostroutingChainRule.getDstNetwork())) {
                    matchingNatPostroutingChainRule = natPostroutingChainRule;
                    break;
                }
            }
        }
        return matchingNatPostroutingChainRule;
    }

    public List<FilterForwardChainRule> getMatchingForwardChainRules(List<FilterForwardChainRule> filterForwardChain) {
        List<FilterForwardChainRule> matchingMilterForwardChainRules = new ArrayList<>();
        for (FilterForwardChainRule filterForwardChainRule : filterForwardChain) {
            if (this.ruleTag.equals(filterForwardChainRule.getRuleTag())) {
                matchingMilterForwardChainRules.add(filterForwardChainRule);
            }
        }
        return matchingMilterForwardChainRules;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("-A PREROUTING");
        if (this.permittedNetwork != null && !ZERO_IPV4_ADDRESS.equals(this.permittedNetwork)) {
            sb.append(" -s ").append(this.permittedNetwork).append('/').append(this.permittedNetworkMask);
        }
        sb.append(" -i ").append(this.inputInterface).append(" -p ").append(this.protocol);
        if (this.permittedMacAddress != null) {
            sb.append(" -m mac --mac-source ").append(this.permittedMacAddress);
        }
        sb.append(" -m ").append(this.protocol);
        if (this.srcPortFirst > 0 && this.srcPortLast >= this.srcPortFirst) {
            sb.append(" --sport ").append(this.srcPortFirst).append(':').append(this.srcPortLast);
        }
        sb.append(" --dport ").append(this.externalPort);
        sb.append(" -j DNAT --to-destination ").append(this.dstIpAddress).append(':').append(this.internalPort);
        if (this.ruleTag != null) {
            sb.append(" -m comment --comment \"").append(this.ruleTag).append('"');
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 71;
        int result = 1;
        result = prime * result + (this.rule == null ? 0 : this.rule.hashCode());
        result = prime * result + (this.inputInterface == null ? 0 : this.inputInterface.hashCode());
        result = prime * result + (this.protocol == null ? 0 : this.protocol.hashCode());
        result = prime * result + this.externalPort;
        result = prime * result + this.internalPort;
        result = prime * result + this.srcPortFirst;
        result = prime * result + this.srcPortLast;
        result = prime * result + (this.dstIpAddress == null ? 0 : this.dstIpAddress.hashCode());
        result = prime * result + (this.permittedNetwork == null ? 0 : this.permittedNetwork.hashCode());
        result = prime * result + this.permittedNetworkMask;
        result = prime * result + (this.permittedMacAddress == null ? 0 : this.permittedMacAddress.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object o) {
        NatPreroutingChainRule other;
        if (o instanceof NatPreroutingChainRule) {
            other = (NatPreroutingChainRule) o;
        } else if (o instanceof String) {
            try {
                other = new NatPreroutingChainRule((String) o);
            } catch (KuraException e) {
                logger.error("equals() :: failed to parse NatPreroutingChainRule ", e);
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
        if (!compareObjects(this.protocol, other.protocol)) {
            return false;
        }
        if (this.externalPort != other.externalPort) {
            return false;
        }
        if (this.internalPort != other.internalPort) {
            return false;
        }
        if (this.srcPortFirst != other.srcPortFirst) {
            return false;
        }
        if (this.srcPortLast != other.srcPortLast) {
            return false;
        }
        if (!compareObjects(this.dstIpAddress, other.dstIpAddress)) {
            return false;
        }
        if (!compareObjects(this.permittedNetwork, other.permittedNetwork)) {
            return false;
        }
        if (this.permittedNetworkMask != other.permittedNetworkMask) {
            return false;
        }
        if (!compareObjects(this.permittedMacAddress, other.permittedMacAddress)) {
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

    public String getInputInterface() {
        return this.inputInterface;
    }

    public String getProtocol() {
        return this.protocol;
    }

    public int getExternalPort() {
        return this.externalPort;
    }

    public int getInternalPort() {
        return this.internalPort;
    }

    public int getSrcPortFirst() {
        return this.srcPortFirst;
    }

    public int getSrcPortLast() {
        return this.srcPortLast;
    }

    public String getDstIpAddress() {
        return this.dstIpAddress;
    }

    public String getPermittedMacAddress() {
        return this.permittedMacAddress;
    }

    public String getPermittedNetwork() {
        return this.permittedNetwork;
    }

    public int getPermittedNetworkMask() {
        return this.permittedNetworkMask;
    }

    public String getRuleTag() {
        return this.ruleTag;
    }

    public void setRuleTag(String ruleTag) {
        this.ruleTag = ruleTag;
    }
}
