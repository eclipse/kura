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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.core.linux.util.LinuxProcessUtil;
import org.eclipse.kura.core.util.ProcessUtil;
import org.eclipse.kura.core.util.SafeProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IptablesConfig {

    private static final String ZERO_IPV4_ADDRESS = "0.0.0.0";

    private class IptablesParserState {

        boolean readingNatTable;
        boolean readingFilterTable;

        public boolean isReadingNatTable() {
            return this.readingNatTable;
        }

        public void setReadingNatTable(boolean readingNatTable) {
            this.readingNatTable = readingNatTable;
        }

        public boolean isReadingFilterTable() {
            return this.readingFilterTable;
        }

        public void setReadingFilterTable(boolean readingFilterTable) {
            this.readingFilterTable = readingFilterTable;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(IptablesConfig.class);
    public static final String FIREWALL_CONFIG_FILE_NAME = "/etc/sysconfig/iptables";
    public static final String FIREWALL_TMP_CONFIG_FILE_NAME = "/tmp/iptables";

    private static final String ALLOW_ALL_TRAFFIC_TO_LOOPBACK = "-A INPUT -i lo -j ACCEPT";
    private static final String ALLOW_ONLY_INCOMING_TO_OUTGOING = "-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT";

    private static final String[] ALLOW_ICMP = {
            "-A INPUT -p icmp -m icmp --icmp-type 8 -m state --state NEW,RELATED,ESTABLISHED -j ACCEPT",
            "-A OUTPUT -p icmp -m icmp --icmp-type 0 -m state --state RELATED,ESTABLISHED -j ACCEPT" };

    private static final String[] DO_NOT_ALLOW_ICMP = {
            "-A INPUT -p icmp -m icmp --icmp-type 8 -m state --state NEW,RELATED,ESTABLISHED -j DROP",
            "-A OUTPUT -p icmp -m icmp --icmp-type 0 -m state --state RELATED,ESTABLISHED -j DROP" };

    private final Set<LocalRule> localRules;
    private final Set<PortForwardRule> portForwardRules;
    private final Set<NATRule> autoNatRules;
    private final Set<NATRule> natRules;
    private boolean allowIcmp;

    public IptablesConfig() {
        this.localRules = new LinkedHashSet<>();
        this.portForwardRules = new LinkedHashSet<>();
        this.autoNatRules = new LinkedHashSet<>();
        this.natRules = new LinkedHashSet<>();
    }

    public IptablesConfig(Set<LocalRule> localRules, Set<PortForwardRule> portForwardRules, Set<NATRule> autoNatRules,
            Set<NATRule> natRules, boolean allowIcmp) {
        this.localRules = localRules != null ? localRules : new LinkedHashSet<>();
        this.portForwardRules = portForwardRules != null ? portForwardRules : new LinkedHashSet<>();
        this.autoNatRules = autoNatRules != null ? autoNatRules : new LinkedHashSet<>();
        this.natRules = natRules != null ? natRules : new LinkedHashSet<>();
        this.allowIcmp = allowIcmp;
    }

    /*
     * Clears all chains
     */
    public static void clearAllChains() throws KuraException {
        FileOutputStream fos = null;
        PrintWriter writer = null;
        try {
            fos = new FileOutputStream(FIREWALL_TMP_CONFIG_FILE_NAME);
            writer = new PrintWriter(fos);
            writer.println("*nat");
            writer.println("COMMIT");
            writer.println("*filter");
            writer.println("COMMIT");
        } catch (Exception e) {
            logger.error("clear() :: failed to clear all chains ", e);
            throw new KuraException(KuraErrorCode.STORE_ERROR, e);
        } finally {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    logger.error("clear() :: failed to close FileOutputStream ", e);
                }
            }
        }
        File configFile = new File(FIREWALL_TMP_CONFIG_FILE_NAME);
        if (configFile.exists()) {
            restore(FIREWALL_TMP_CONFIG_FILE_NAME);
        }
    }

    public static void applyBlockPolicy() throws KuraException {
        try (FileOutputStream fos = new FileOutputStream(FIREWALL_TMP_CONFIG_FILE_NAME);
                PrintWriter writer = new PrintWriter(fos)) {
            writer.println("*nat");
            writer.println("COMMIT");
            writer.println("*filter");
            writer.println(ALLOW_ALL_TRAFFIC_TO_LOOPBACK);
            writer.println(ALLOW_ONLY_INCOMING_TO_OUTGOING);
            writer.println("COMMIT");
        } catch (Exception e) {
            logger.error("applyBlockPolicy() :: failed to clear all chains ", e);
            throw new KuraException(KuraErrorCode.STORE_ERROR, e);
        }
        File configFile = new File(FIREWALL_TMP_CONFIG_FILE_NAME);
        if (configFile.exists()) {
            restore(FIREWALL_TMP_CONFIG_FILE_NAME);
        }
    }

    /*
     * Saves (using iptables-save) the current iptables config into /etc/sysconfig/iptables
     */
    public static void save() throws KuraException {
        SafeProcess proc = null;

        try {
            int status = -1;
            proc = ProcessUtil.exec("iptables-save");
            status = proc.waitFor();
            if (status != 0) {
                logger.error("save() :: failed - {}", LinuxProcessUtil.getInputStreamAsString(proc.getErrorStream()));
                throw new KuraException(KuraErrorCode.OS_COMMAND_ERROR, "Failed to execute the iptable-save command");
            }
            iptablesSave(proc);
            logger.debug("iptablesSave() :: completed!, status={}", status);
        } catch (Exception e) {
            throw new KuraException(KuraErrorCode.PROCESS_EXECUTION_ERROR, e);
        } finally {
            if (proc != null) {
                ProcessUtil.destroy(proc);
            }
        }
    }

    private static void iptablesSave(SafeProcess proc) throws KuraException {
        try (InputStreamReader isr = new InputStreamReader(proc.getInputStream());
                BufferedReader br = new BufferedReader(isr);
                PrintWriter out = new PrintWriter(FIREWALL_CONFIG_FILE_NAME)) {
            String line = null;
            while ((line = br.readLine()) != null) {
                out.println(line);
            }
        } catch (Exception e) {
            logger.error("failed to write {} file ", FIREWALL_CONFIG_FILE_NAME, e);
            throw new KuraException(KuraErrorCode.STORE_ERROR, e);
        }
    }

    /*
     * Restores (using iptables-restore) firewall settings from temporary iptables configuration file.
     * Temporary configuration file is deleted upon completion.
     */
    public static void restore(String filename) throws KuraException {
        SafeProcess proc = null;
        try {
            proc = ProcessUtil.exec("iptables-restore " + filename);
            int status = proc.waitFor();
            if (status != 0) {
                logger.error("restore() :: failed - {}",
                        LinuxProcessUtil.getInputStreamAsString(proc.getErrorStream()));
                throw new KuraException(KuraErrorCode.OS_COMMAND_ERROR,
                        "Failed to execute the iptable-restore command");
            }
        } catch (Exception e) {
            logger.error("restore() :: Failed to execute the iptable-restore command ", e);
            throw new KuraException(KuraErrorCode.PROCESS_EXECUTION_ERROR, e);
        } finally {
            if (proc != null) {
                ProcessUtil.destroy(proc);
            }
            File configFile = new File(filename);
            if (configFile.exists() && configFile.delete()) {
                logger.debug("restore() :: removing the {} file", filename);
            }
        }
    }

    /*
     * Saves current configurations from the localRules, portForwardRules, natRules, and autoNatRules
     * into specified temporary file
     */
    public void save(String filename) throws KuraException {
        try (FileOutputStream fos = new FileOutputStream(filename); PrintWriter writer = new PrintWriter(fos)) {
            writer.println("*filter");
            writer.println(ALLOW_ALL_TRAFFIC_TO_LOOPBACK);
            writer.println(ALLOW_ONLY_INCOMING_TO_OUTGOING);
            if (this.allowIcmp) {
                for (String sAllowIcmp : ALLOW_ICMP) {
                    writer.println(sAllowIcmp);
                }
            } else {
                for (String sDoNotAllowIcmp : DO_NOT_ALLOW_ICMP) {
                    writer.println(sDoNotAllowIcmp);
                }
            }

            for (LocalRule lr : this.localRules) {
                writer.println(lr);
            }

            for (PortForwardRule portForwardRule : this.portForwardRules) {
                List<String> filterForwardChainRules = portForwardRule.getFilterForwardChainRule().toStrings();
                for (String filterForwardChainRule : filterForwardChainRules) {
                    writer.println(filterForwardChainRule);
                }
            }

            for (NATRule autoNatRule : this.autoNatRules) {
                List<String> filterForwardChainRules = autoNatRule.getFilterForwardChainRule().toStrings();
                for (String filterForwardChainRule : filterForwardChainRules) {
                    writer.println(filterForwardChainRule);
                }
            }

            for (NATRule natRule : this.natRules) {
                List<String> filterForwardChainRules = natRule.getFilterForwardChainRule().toStrings();
                for (String filterForwardChainRule : filterForwardChainRules) {
                    writer.println(filterForwardChainRule);
                }
            }

            writer.println("COMMIT");
            writer.println("*nat");

            for (PortForwardRule portForwardRule : this.portForwardRules) {
                writer.println(portForwardRule.getNatPreroutingChainRule());
                writer.println(portForwardRule.getNatPostroutingChainRule());
            }

            List<NatPostroutingChainRule> appliedNatPostroutingChainRules = new ArrayList<>();
            for (NATRule autoNatRule : this.autoNatRules) {
                boolean found = false;
                NatPostroutingChainRule natPostroutingChainRule = autoNatRule.getNatPostroutingChainRule();

                for (NatPostroutingChainRule appliedNatPostroutingChainRule : appliedNatPostroutingChainRules) {
                    if (appliedNatPostroutingChainRule.equals(natPostroutingChainRule)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    writer.println(autoNatRule.getNatPostroutingChainRule());
                    appliedNatPostroutingChainRules.add(natPostroutingChainRule);
                }
            }

            for (NATRule natRule : this.natRules) {
                writer.println(natRule.getNatPostroutingChainRule());
            }
            writer.println("COMMIT");
        } catch (Exception e) {
            logger.error("save() :: failed to clear all chains ", e);
            throw new KuraException(KuraErrorCode.STORE_ERROR, e);
        }
    }

    /*
     * Populates the m_localRules, m_portForwardRules, m_natRules, and m_autoNatRules by parsing
     * the iptables configuration file.
     */
    public void restore() throws KuraException {
        try (FileReader fr = new FileReader(FIREWALL_CONFIG_FILE_NAME); BufferedReader br = new BufferedReader(fr)) {
            List<NatPreroutingChainRule> natPreroutingChain = new ArrayList<>();
            List<NatPostroutingChainRule> natPostroutingChain = new ArrayList<>();
            List<FilterForwardChainRule> filterForwardChain = new ArrayList<>();
            parseIptablesFile(natPreroutingChain, natPostroutingChain, filterForwardChain, br);
            restorePortForwardRules(natPreroutingChain, natPostroutingChain, filterForwardChain);
            restoreNatRules(natPostroutingChain, filterForwardChain);
        } catch (Exception e) {
            throw new KuraException(KuraErrorCode.STORE_ERROR, e);
        }
    }

    private void parseIptablesFile(List<NatPreroutingChainRule> natPreroutingChain,
            List<NatPostroutingChainRule> natPostroutingChain, List<FilterForwardChainRule> filterForwardChain,
            BufferedReader br) throws KuraException, IOException {
        String line;
        IptablesParserState iptablesParserState = new IptablesParserState();
        while ((line = br.readLine()) != null) {
            line = line.trim();
            parseIptablesFileLine(natPreroutingChain, natPostroutingChain, filterForwardChain, iptablesParserState,
                    line);
        }
    }

    private void parseIptablesFileLine(List<NatPreroutingChainRule> natPreroutingChain,
            List<NatPostroutingChainRule> natPostroutingChain, List<FilterForwardChainRule> filterForwardChain,
            IptablesParserState iptablesParserState, String line) throws KuraException {

        // skip any predefined lines or comment lines
        if ("".equals(line) || line.startsWith("#") || line.startsWith(":")) {
            return;
        }

        if ("*nat".equals(line)) {
            iptablesParserState.setReadingNatTable(true);
        } else if ("*filter".equals(line)) {
            iptablesParserState.setReadingFilterTable(true);
        } else if ("COMMIT".equals(line)) {
            if (iptablesParserState.isReadingNatTable()) {
                iptablesParserState.setReadingNatTable(false);
            }
            if (iptablesParserState.isReadingFilterTable()) {
                iptablesParserState.setReadingFilterTable(false);
            }
        } else if (iptablesParserState.isReadingNatTable() && line.startsWith("-A PREROUTING")) {
            natPreroutingChain.add(new NatPreroutingChainRule(line));
        } else if (iptablesParserState.isReadingNatTable() && line.startsWith("-A POSTROUTING")) {
            natPostroutingChain.add(new NatPostroutingChainRule(line));
        } else if (iptablesParserState.isReadingFilterTable() && line.startsWith("-A FORWARD")) {
            filterForwardChain.add(new FilterForwardChainRule(line));
        } else if (iptablesParserState.isReadingFilterTable() && line.startsWith("-A INPUT")) {
            parseIptablesFileInputLine(line);
        }
    }

    private void parseIptablesFileInputLine(String line) {
        if (ALLOW_ALL_TRAFFIC_TO_LOOPBACK.equals(line) || ALLOW_ONLY_INCOMING_TO_OUTGOING.equals(line)) {
            return;
        }
        if (isAcceptIcmpLine(line)) {
            this.allowIcmp = true;
            return;
        }
        if (isDropIcmpLine(line)) {
            this.allowIcmp = false;
            return;
        }
        try {
            LocalRule localRule = new LocalRule(line);
            logger.debug("parseFirewallConfigurationFile() :: Adding local rule: {}", localRule);
            this.localRules.add(localRule);
        } catch (KuraException e) {
            logger.error("Failed to parse Local Rule: {} ", line, e);
        }
    }

    private boolean isAcceptIcmpLine(String line) {
        boolean ret = false;
        for (String allowIcmpProto : ALLOW_ICMP) {
            if (allowIcmpProto.equals(line)) {
                ret = true;
                break;
            }
        }
        return ret;
    }

    private boolean isDropIcmpLine(String line) {
        boolean ret = false;
        for (String allowIcmpProto : DO_NOT_ALLOW_ICMP) {
            if (allowIcmpProto.equals(line)) {
                ret = true;
                break;
            }
        }
        return ret;
    }

    private void restorePortForwardRules(List<NatPreroutingChainRule> natPreroutingChain,
            List<NatPostroutingChainRule> natPostroutingChain, List<FilterForwardChainRule> filterForwardChain) {
        for (NatPreroutingChainRule natPreroutingChainRule : natPreroutingChain) {
            // found port forwarding rule ...
            restorePortForwardRule(natPreroutingChainRule, natPostroutingChain, filterForwardChain);
        }
    }

    private void restorePortForwardRule(NatPreroutingChainRule natPreroutingChainRule,
            List<NatPostroutingChainRule> natPostroutingChain, List<FilterForwardChainRule> filterForwardChain) {
        String inboundIfaceName = natPreroutingChainRule.getInputInterface();
        String outboundIfaceName = null;
        String protocol = natPreroutingChainRule.getProtocol();
        int inPort = natPreroutingChainRule.getExternalPort();
        int outPort = natPreroutingChainRule.getInternalPort();
        boolean masquerade = false;
        String sport = null;
        if (natPreroutingChainRule.getSrcPortFirst() > 0
                && natPreroutingChainRule.getSrcPortFirst() <= natPreroutingChainRule.getSrcPortLast()) {
            StringBuilder sbSport = new StringBuilder().append(natPreroutingChainRule.getSrcPortFirst()).append(':')
                    .append(natPreroutingChainRule.getSrcPortLast());
            sport = sbSport.toString();
        }
        String permittedMac = natPreroutingChainRule.getPermittedMacAddress();
        String permittedNetwork = natPreroutingChainRule.getPermittedNetwork();
        int permittedNetworkMask = natPreroutingChainRule.getPermittedNetworkMask();
        String address = natPreroutingChainRule.getDstIpAddress();

        String ruleTag = "";
        NatPostroutingChainRule matchingNatPostroutingChainRule = natPreroutingChainRule
                .getMatchingPostRoutingChainRule(natPostroutingChain);
        if (matchingNatPostroutingChainRule != null) {
            ruleTag = matchingNatPostroutingChainRule.getRuleTag();
            outboundIfaceName = matchingNatPostroutingChainRule.getDstInterface();
            if (matchingNatPostroutingChainRule.isMasquerade()) {
                masquerade = true;
            }
        } else {
            List<FilterForwardChainRule> matchingForwardChainRules = natPreroutingChainRule
                    .getMatchingForwardChainRules(filterForwardChain);
            for (FilterForwardChainRule matchingForwardChainRule : matchingForwardChainRules) {
                matchingForwardChainRule.setMatched(true);
                ruleTag = matchingForwardChainRule.getRuleTag();
                if (!matchingForwardChainRule.isInboundForwardRule()) {
                    outboundIfaceName = matchingForwardChainRule.getOutputInterface();
                }
            }
        }

        if (permittedNetwork == null) {
            permittedNetwork = ZERO_IPV4_ADDRESS;
        }
        PortForwardRule portForwardRule = new PortForwardRule(inboundIfaceName, outboundIfaceName, address, protocol,
                inPort, outPort, masquerade, permittedNetwork, permittedNetworkMask, permittedMac, sport);
        portForwardRule.setRuleTag(ruleTag);
        logger.debug("restorePortForwardRule() :: restoring port forward rule: {}", portForwardRule);
        this.portForwardRules.add(portForwardRule);
    }

    private void restoreNatRules(List<NatPostroutingChainRule> natPostroutingChain,
            List<FilterForwardChainRule> filterForwardChain) {
        for (NatPostroutingChainRule natPostroutingChainRule : natPostroutingChain) {
            if (natPostroutingChainRule.getRuleType() == PostRoutingRuleType.ANAT) {
                // found Auto NAT rule ...
                restoreAutoNatRule(natPostroutingChainRule, filterForwardChain);
            } else if (natPostroutingChainRule.getRuleType() == PostRoutingRuleType.NAT) {
                // found NAT rule, ... maybe
                restoreNatRule(natPostroutingChainRule, filterForwardChain);
            }
        }
        // handle NAT rules with the 'nat' tag (IP Forwarding w/o masquerading)
        for (FilterForwardChainRule filterForwardChainRule : filterForwardChain) {
            if (filterForwardChainRule.getRuleTag().startsWith(NATRule.RULE_TAG)
                    && !filterForwardChainRule.isMatched()) {
                filterForwardChainRule.setMatched(true);
                if (!filterForwardChainRule.isInboundForwardRule()) {
                    logger.debug("restoreNatRules() :: restoring NAT rule with" + "   sourceInterface: "
                            + filterForwardChainRule.getInputInterface() + "   destinationInterface: "
                            + filterForwardChainRule.getOutputInterface() + "   masquerade: false protocol: "
                            + filterForwardChainRule.getProtocol() + "  source network/host: "
                            + filterForwardChainRule.getSrcNetwork() + "  destination network/host "
                            + filterForwardChainRule.getDstNetwork());
                    NATRule natRule = new NATRule(filterForwardChainRule.getInputInterface(),
                            filterForwardChainRule.getOutputInterface(), filterForwardChainRule.getProtocol(),
                            filterForwardChainRule.getSrcNetwork(), filterForwardChainRule.getDstNetwork(), false);
                    natRule.setRuleTag(filterForwardChainRule.getRuleTag());
                    logger.debug("restoreNatRules() :: Adding NAT rule {}", natRule);
                    this.natRules.add(natRule);
                }
            }
        }
    }

    private void restoreNatRule(NatPostroutingChainRule natPostroutingChainRule,
            List<FilterForwardChainRule> filterForwardChain) {
        String destinationInterface = natPostroutingChainRule.getDstInterface();
        boolean masquerade = natPostroutingChainRule.isMasquerade();
        String protocol = natPostroutingChainRule.getProtocol();
        String source = natPostroutingChainRule.getSrcNetwork();
        String destination = natPostroutingChainRule.getDstNetwork();
        if (destination != null) {
            StringBuilder sbDestination = new StringBuilder().append(destination).append('/')
                    .append(natPostroutingChainRule.getDstMask());
            destination = sbDestination.toString();
        }
        // match FORWARD rule to find out source interface ...
        List<FilterForwardChainRule> matchingForwardChainRules = natPostroutingChainRule
                .getMatchingForwardChainRules(filterForwardChain);
        for (FilterForwardChainRule matchingForwardChainRule : matchingForwardChainRules) {
            matchingForwardChainRule.setMatched(true);
            if (!matchingForwardChainRule.isInboundForwardRule()) {
                String sourceInterface = matchingForwardChainRule.getInputInterface();
                logger.debug("restoreNatRule() :: Restoring NAT rule with" + "   sourceInterface: " + sourceInterface
                        + "   destinationInterface: " + destinationInterface + "   masquerade: " + masquerade
                        + " protocol: " + protocol + "  source network/host: " + source + " destination network/host "
                        + destination);
                NATRule natRule = new NATRule(sourceInterface, destinationInterface, protocol, source, destination,
                        masquerade);
                natRule.setRuleTag(natPostroutingChainRule.getRuleTag());
                logger.debug("parseFirewallConfigurationFile() :: Adding NAT rule {}", natRule);
                this.natRules.add(natRule);
            }
        }
    }

    private void restoreAutoNatRule(NatPostroutingChainRule natPostroutingChainRule,
            List<FilterForwardChainRule> filterForwardChain) {
        String destinationInterface = natPostroutingChainRule.getDstInterface();
        List<FilterForwardChainRule> matchingForwardChainRules = natPostroutingChainRule
                .getMatchingForwardChainRules(filterForwardChain);
        for (FilterForwardChainRule matchingForwardChainRule : matchingForwardChainRules) {
            matchingForwardChainRule.setMatched(true);
            if (!matchingForwardChainRule.isInboundForwardRule()) {
                String sourceInterface = matchingForwardChainRule.getInputInterface();
                logger.debug(
                        "restoreAutoNatRule() :: Restoring auto NAT rule with  sourceInterface: {}  destinationInterface: {}  masquerade: true",
                        sourceInterface, destinationInterface);
                NATRule natRule = new NATRule(sourceInterface, destinationInterface, true);
                logger.debug("parseFirewallConfigurationFile() :: Adding auto NAT rule {}", natRule);
                this.autoNatRules.add(natRule);
            }
        }
    }

    public Set<LocalRule> getLocalRules() {
        return this.localRules;
    }

    public Set<PortForwardRule> getPortForwardRules() {
        return this.portForwardRules;
    }

    public Set<NATRule> getAutoNatRules() {
        return this.autoNatRules;
    }

    public Set<NATRule> getNatRules() {
        return this.natRules;
    }

    public boolean allowIcmp() {
        return this.allowIcmp;
    }
}
