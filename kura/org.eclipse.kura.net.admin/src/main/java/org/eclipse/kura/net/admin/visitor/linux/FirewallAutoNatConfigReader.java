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
package org.eclipse.kura.net.admin.visitor.linux;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.core.net.AbstractNetInterface;
import org.eclipse.kura.core.net.NetInterfaceAddressConfigImpl;
import org.eclipse.kura.core.net.NetworkConfiguration;
import org.eclipse.kura.core.net.NetworkConfigurationVisitor;
import org.eclipse.kura.core.net.WifiInterfaceAddressConfigImpl;
import org.eclipse.kura.linux.net.iptables.LinuxFirewall;
import org.eclipse.kura.linux.net.iptables.NATRule;
import org.eclipse.kura.net.NetConfig;
import org.eclipse.kura.net.NetInterfaceAddressConfig;
import org.eclipse.kura.net.NetInterfaceConfig;
import org.eclipse.kura.net.NetInterfaceType;
import org.eclipse.kura.net.admin.visitor.linux.util.KuranetConfig;
import org.eclipse.kura.net.firewall.FirewallAutoNatConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FirewallAutoNatConfigReader implements NetworkConfigurationVisitor {

    private static final Logger logger = LoggerFactory.getLogger(FirewallAutoNatConfigReader.class);
    private static final String NET_INTERFACE_STR = "net.interface.";

    private static FirewallAutoNatConfigReader instance;

    public static FirewallAutoNatConfigReader getInstance() {

        if (instance == null) {
            instance = new FirewallAutoNatConfigReader();
        }
        return instance;
    }

    @Override
    public void visit(NetworkConfiguration config) throws KuraException {
        List<NetInterfaceConfig<? extends NetInterfaceAddressConfig>> netInterfaceConfigs = config
                .getNetInterfaceConfigs();
        for (NetInterfaceConfig<? extends NetInterfaceAddressConfig> netInterfaceConfig : netInterfaceConfigs) {
            getConfig(netInterfaceConfig, getKuranetProperties());
        }
    }

    protected Set<NATRule> getAutoNatRules() throws KuraException {
        LinuxFirewall firewall = LinuxFirewall.getInstance();
        return firewall.getAutoNatRules();
    }

    protected Properties getKuranetProperties() {
        return KuranetConfig.getProperties();
    }

    private void getConfig(NetInterfaceConfig<? extends NetInterfaceAddressConfig> netInterfaceConfig,
            Properties kuraProps) throws KuraException {

        String interfaceName = netInterfaceConfig.getName();

        NetInterfaceType type = netInterfaceConfig.getType();
        if (type == NetInterfaceType.ETHERNET || type == NetInterfaceType.WIFI) {
            logger.debug("Getting NAT config for {}", interfaceName);
            if (kuraProps != null) {
                getRulesFromConfig(netInterfaceConfig, kuraProps, interfaceName);
            } else {
                getRulesFromFile(netInterfaceConfig, interfaceName);
            }
        }
    }

    private void getRulesFromConfig(NetInterfaceConfig<? extends NetInterfaceAddressConfig> netInterfaceConfig,
            Properties kuraProps, String interfaceName) throws KuraException {

        logger.debug("Getting NAT config from kuraProps");

        boolean natEnabled = false;
        boolean useMasquerade = false;
        String srcIface = null;
        String dstIface = null;
        String prop;
        StringBuilder sb = new StringBuilder().append(NET_INTERFACE_STR).append(interfaceName)
                .append(".config.nat.enabled");
        if ((prop = kuraProps.getProperty(sb.toString())) != null) {
            natEnabled = Boolean.parseBoolean(prop);
        }

        sb = new StringBuilder().append(NET_INTERFACE_STR).append(interfaceName).append(".config.nat.masquerade");
        if ((prop = kuraProps.getProperty(sb.toString())) != null) {
            useMasquerade = Boolean.parseBoolean(prop);
        }

        sb = new StringBuilder().append(NET_INTERFACE_STR).append(interfaceName).append(".config.nat.src.interface");
        if ((prop = kuraProps.getProperty(sb.toString())) != null) {
            srcIface = prop;
        }

        sb = new StringBuilder().append(NET_INTERFACE_STR).append(interfaceName).append(".config.nat.dst.interface");
        if ((prop = kuraProps.getProperty(sb.toString())) != null) {
            dstIface = prop;
        }

        if (natEnabled) {
            addNatConfig(netInterfaceConfig, srcIface, dstIface, useMasquerade);
        }
    }

    private void addNatConfig(NetInterfaceConfig<? extends NetInterfaceAddressConfig> netInterfaceConfig,
            String srcIface, String dstIface, boolean useMasquerade) throws KuraException {

        FirewallAutoNatConfig natConfig = new FirewallAutoNatConfig(srcIface, dstIface, useMasquerade);
        NetInterfaceAddressConfig netInterfaceAddressConfig = ((AbstractNetInterface<?>) netInterfaceConfig)
                .getNetInterfaceAddressConfig();
        List<NetConfig> netConfigs = netInterfaceAddressConfig.getConfigs();

        if (netConfigs == null) {
            netConfigs = new ArrayList<>();
            if (netInterfaceAddressConfig instanceof NetInterfaceAddressConfigImpl) {
                ((NetInterfaceAddressConfigImpl) netInterfaceAddressConfig).setNetConfigs(netConfigs);
            } else if (netInterfaceAddressConfig instanceof WifiInterfaceAddressConfigImpl) {
                ((WifiInterfaceAddressConfigImpl) netInterfaceAddressConfig).setNetConfigs(netConfigs);
            }
        }

        netConfigs.add(natConfig);
    }

    private void getRulesFromFile(NetInterfaceConfig<? extends NetInterfaceAddressConfig> netInterfaceConfig,
            String interfaceName) throws KuraException {

        // get it from the firewall file if possible
        logger.debug("Getting NAT config from the firewall file");

        Set<NATRule> natRules = getAutoNatRules();
        if (natRules != null && !natRules.isEmpty()) {
            Iterator<NATRule> it = natRules.iterator();
            while (it.hasNext()) {
                NATRule rule = it.next();
                if (rule.getSourceInterface().equals(interfaceName)) {
                    logger.debug("found NAT rule: {}", rule);

                    addNatConfig(netInterfaceConfig, rule.getSourceInterface(), rule.getDestinationInterface(),
                            rule.isMasquerade());
                }
            }
        }
    }
}
