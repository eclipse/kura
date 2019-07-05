/*******************************************************************************
 * Copyright (c) 2017 Eurotech and/or its affiliates and others
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.eclipse.kura.net.admin.visitor.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.core.net.EthernetInterfaceConfigImpl;
import org.eclipse.kura.core.net.NetInterfaceAddressConfigImpl;
import org.eclipse.kura.core.net.NetworkConfiguration;
import org.eclipse.kura.core.net.WifiInterfaceAddressConfigImpl;
import org.eclipse.kura.core.net.WifiInterfaceConfigImpl;
import org.eclipse.kura.core.testutil.TestUtil;
import org.eclipse.kura.linux.net.iptables.NATRule;
import org.eclipse.kura.net.NetConfig;
import org.eclipse.kura.net.NetConfigIP4;
import org.eclipse.kura.net.NetInterfaceAddressConfig;
import org.eclipse.kura.net.NetInterfaceConfig;
import org.eclipse.kura.net.NetInterfaceStatus;
import org.eclipse.kura.net.firewall.FirewallAutoNatConfig;
import org.eclipse.kura.net.firewall.FirewallNatConfig;
import org.eclipse.kura.net.wifi.WifiInterfaceAddressConfig;
import org.junit.Test;

public class FirewallAutoNatConfigTest {

    @Test
    public void testWriterVisit() throws KuraException {
        String intfName = "testinterface";
        String destinationInterface = "destIntf";
        boolean[] visited = { false, false };

        FirewallAutoNatConfigWriter writer = new FirewallAutoNatConfigWriter() {

            @Override
            protected Properties getKuranetProperties() {
                return null;
            }

            @Override
            protected void storeKuranetProperties(Properties kuraProps) throws KuraException {
                String enabled = (String) kuraProps.get("net.interface." + intfName + ".config.nat.enabled");
                assertTrue(Boolean.parseBoolean(enabled));
                String dst = (String) kuraProps.get("net.interface." + intfName + ".config.nat.dst.interface");
                assertEquals(destinationInterface, dst);
                String masq = (String) kuraProps.get("net.interface." + intfName + ".config.nat.masquerade");
                assertTrue(Boolean.parseBoolean(masq));

                visited[1] = true;
            }

            @Override
            protected void applyNatConfig(NetworkConfiguration networkConfig) throws KuraException {
                visited[0] = true;
            }
        };

        NetworkConfiguration config = prepareNetworkConfiguration(intfName, destinationInterface, false, false);

        writer.visit(config);

        assertTrue("Configuration expected to be applied.", visited[0]);
        assertTrue("Properties expected to be stored.", visited[1]);
    }

    private NetworkConfiguration prepareNetworkConfiguration(String intfName, String destinationInterface,
            boolean second, boolean omitNetConfigs) {
        NetworkConfiguration config = new NetworkConfiguration();

        WifiInterfaceConfigImpl netInterfaceConfig = new WifiInterfaceConfigImpl(intfName);
        config.addNetInterfaceConfig(netInterfaceConfig);

        List<WifiInterfaceAddressConfig> interfaceAddressConfigs = new ArrayList<>();
        WifiInterfaceAddressConfigImpl wifiInterfaceAddressConfig = new WifiInterfaceAddressConfigImpl();

        if (!omitNetConfigs) {
            List<NetConfig> netConfigs = new ArrayList<>();
            FirewallAutoNatConfig natConfig = new FirewallAutoNatConfig();
            natConfig.setDestinationInterface(destinationInterface);
            natConfig.setSourceInterface(intfName);
            natConfig.setMasquerade(true);
            netConfigs.add(natConfig);
            NetConfigIP4 netConfig = new NetConfigIP4(NetInterfaceStatus.netIPv4StatusEnabledLAN, true);
            netConfigs.add(netConfig);
            FirewallNatConfig natConfig2 = new FirewallNatConfig(intfName, destinationInterface, "TCP", "", "", true);
            netConfigs.add(natConfig2);
            wifiInterfaceAddressConfig.setNetConfigs(netConfigs);
        }
        interfaceAddressConfigs.add(wifiInterfaceAddressConfig);
        netInterfaceConfig.setNetInterfaceAddresses(interfaceAddressConfigs);

        if (second) {
            EthernetInterfaceConfigImpl netInterfaceConfig2 = new EthernetInterfaceConfigImpl(destinationInterface);
            config.addNetInterfaceConfig(netInterfaceConfig2);

            List<NetInterfaceAddressConfig> interfaceAddressConfigs2 = new ArrayList<>();
            NetInterfaceAddressConfigImpl ethInterfaceAddressConfig = new NetInterfaceAddressConfigImpl();

            if (!omitNetConfigs) {
                List<NetConfig> netConfigs = new ArrayList<>();
                FirewallAutoNatConfig natConfig = new FirewallAutoNatConfig();
                natConfig.setDestinationInterface(destinationInterface);
                natConfig.setSourceInterface(intfName);
                natConfig.setMasquerade(true);
                netConfigs.add(natConfig);
                NetConfigIP4 netConfig = new NetConfigIP4(NetInterfaceStatus.netIPv4StatusEnabledWAN, true);
                netConfigs.add(netConfig);
                ethInterfaceAddressConfig.setNetConfigs(netConfigs);
            }
            interfaceAddressConfigs2.add(ethInterfaceAddressConfig);
            netInterfaceConfig2.setNetInterfaceAddresses(interfaceAddressConfigs2);
        }

        return config;
    }

    @Test
    public void testWriterNatConfigs() throws Throwable {
        String intfName = "testinterface";
        String destinationInterface = "destIntf";

        FirewallAutoNatConfigWriter writer = new FirewallAutoNatConfigWriter();

        NetworkConfiguration config = prepareNetworkConfiguration(intfName, destinationInterface, true, false);

        Set<NATRule> natConfigs = (Set<NATRule>) TestUtil.invokePrivate(writer, "getNatConfigs", config);
        assertNotNull(natConfigs);
        assertEquals(1, natConfigs.size());
        NATRule natRule = natConfigs.iterator().next();
        assertEquals(destinationInterface, natRule.getDestinationInterface());
        assertEquals(intfName, natRule.getSourceInterface());
    }

    @Test
    public void testReaderVisitNoFirewallFileNullAddressList() throws KuraException {
        String intfName = "testInterface";
        String destinationInterface = "destInterface";

        FirewallAutoNatConfigReader reader = new FirewallAutoNatConfigReader() {

            @Override
            protected Properties getKuranetProperties() {
                Properties properties = new Properties();

                properties.put("net.interface." + intfName + ".config.nat.enabled", "true");
                properties.put("net.interface." + intfName + ".config.nat.dst.interface", destinationInterface);
                properties.put("net.interface." + intfName + ".config.nat.src.interface", intfName);
                properties.put("net.interface." + intfName + ".config.nat.masquerade", "true");

                return properties;
            }
        };

        NetworkConfiguration config = new NetworkConfiguration();

        WifiInterfaceConfigImpl netInterfaceConfig = new WifiInterfaceConfigImpl(intfName);
        netInterfaceConfig.setNetInterfaceAddresses(null);
        config.addNetInterfaceConfig(netInterfaceConfig);

        try {
            reader.visit(config);
            fail("Exception expected.");
        } catch (KuraException e) {
            assertEquals(KuraErrorCode.CONFIGURATION_ERROR, e.getCode());
        }
    }

    @Test
    public void testReaderVisitNoFirewallFileEmptyAddressList() throws KuraException {
        String intfName = "testInterface";
        String destinationInterface = "destInterface";

        FirewallAutoNatConfigReader reader = new FirewallAutoNatConfigReader() {

            @Override
            protected Properties getKuranetProperties() {
                Properties properties = new Properties();

                properties.put("net.interface." + intfName + ".config.nat.enabled", "true");
                properties.put("net.interface." + intfName + ".config.nat.dst.interface", destinationInterface);
                properties.put("net.interface." + intfName + ".config.nat.src.interface", intfName);
                properties.put("net.interface." + intfName + ".config.nat.masquerade", "true");

                return properties;
            }
        };

        NetworkConfiguration config = new NetworkConfiguration();

        WifiInterfaceConfigImpl netInterfaceConfig = new WifiInterfaceConfigImpl(intfName);
        config.addNetInterfaceConfig(netInterfaceConfig);

        try {
            reader.visit(config);
            fail("Exception expected.");
        } catch (KuraException e) {
            assertEquals(KuraErrorCode.CONFIGURATION_ERROR, e.getCode());
        }
    }

    @Test
    public void testReaderVisitNoFirewallFile() throws KuraException {
        String intfName = "testInterface";
        String destinationInterface = "destInterface";

        FirewallAutoNatConfigReader reader = new FirewallAutoNatConfigReader() {

            @Override
            protected Properties getKuranetProperties() {
                Properties properties = new Properties();

                properties.put("net.interface." + intfName + ".config.nat.enabled", "true");
                properties.put("net.interface." + intfName + ".config.nat.dst.interface", destinationInterface);
                properties.put("net.interface." + intfName + ".config.nat.src.interface", intfName);
                properties.put("net.interface." + intfName + ".config.nat.masquerade", "true");

                return properties;
            }
        };

        NetworkConfiguration config = prepareNetworkConfiguration(intfName, destinationInterface, false, true);

        reader.visit(config);

        assertEquals(1, config.getNetInterfaceConfigs().size());

        NetInterfaceConfig<? extends NetInterfaceAddressConfig> cfg = config.getNetInterfaceConfig(intfName);
        assertEquals(1, cfg.getNetInterfaceAddresses().size());

        NetInterfaceAddressConfig addressConfig = cfg.getNetInterfaceAddresses().get(0);
        assertEquals(1, addressConfig.getConfigs().size());

        NetConfig netConfig = addressConfig.getConfigs().get(0);
        assertTrue(netConfig instanceof FirewallAutoNatConfig);
        assertEquals(intfName, ((FirewallAutoNatConfig) netConfig).getSourceInterface());
        assertEquals(destinationInterface, ((FirewallAutoNatConfig) netConfig).getDestinationInterface());
        assertTrue(((FirewallAutoNatConfig) netConfig).isMasquerade());
    }

    @Test
    public void testReaderVisitNoFirewallFileNoNat() throws KuraException {
        String intfName = "testInterface";
        String destinationInterface = "destInterface";

        FirewallAutoNatConfigReader reader = new FirewallAutoNatConfigReader() {

            @Override
            protected Properties getKuranetProperties() {
                Properties properties = new Properties();

                properties.put("net.interface." + intfName + ".config.nat.enabled", "false");
                properties.put("net.interface." + intfName + ".config.nat.dst.interface", destinationInterface);
                properties.put("net.interface." + intfName + ".config.nat.src.interface", intfName);
                properties.put("net.interface." + intfName + ".config.nat.masquerade", "true");

                return properties;
            }
        };

        NetworkConfiguration config = prepareNetworkConfiguration(intfName, destinationInterface, false, true);

        reader.visit(config);

        assertEquals(1, config.getNetInterfaceConfigs().size());

        NetInterfaceConfig<? extends NetInterfaceAddressConfig> cfg = config.getNetInterfaceConfig(intfName);
        assertEquals(1, cfg.getNetInterfaceAddresses().size());

        NetInterfaceAddressConfig addressConfig = cfg.getNetInterfaceAddresses().get(0);
        assertNull(addressConfig.getConfigs());
    }

    @Test
    public void testReaderVisitWithFirewallFileNullAddressList() throws KuraException {
        String intfName = "testInterface";
        String destinationInterface = "destInterface";
        FirewallAutoNatConfigReader reader = new FirewallAutoNatConfigReader() {

            @Override
            protected Set<NATRule> getAutoNatRules() throws KuraException {
                HashSet<NATRule> natRules = new HashSet<>();

                NATRule rule = new NATRule(intfName, destinationInterface, true);
                natRules.add(rule);

                return natRules;
            }

            @Override
            protected Properties getKuranetProperties() {
                return null;
            }
        };

        NetworkConfiguration config = new NetworkConfiguration();

        WifiInterfaceConfigImpl netInterfaceConfig = new WifiInterfaceConfigImpl(intfName);
        netInterfaceConfig.setNetInterfaceAddresses(null);
        config.addNetInterfaceConfig(netInterfaceConfig);
        try {
            reader.visit(config);
            fail("Exception expected.");
        } catch (KuraException e) {
            assertEquals(KuraErrorCode.CONFIGURATION_ERROR, e.getCode());
        }
    }

    @Test
    public void testReaderVisitWithFirewallFileEmptyAddressList() throws KuraException {
        String intfName = "testInterface";
        String destinationInterface = "destInterface";

        FirewallAutoNatConfigReader reader = new FirewallAutoNatConfigReader() {

            @Override
            protected Set<NATRule> getAutoNatRules() throws KuraException {
                HashSet<NATRule> natRules = new HashSet<>();

                NATRule rule = new NATRule(intfName, destinationInterface, true);
                natRules.add(rule);

                return natRules;
            }

            @Override
            protected Properties getKuranetProperties() {
                return null;
            }
        };

        NetworkConfiguration config = new NetworkConfiguration();

        WifiInterfaceConfigImpl netInterfaceConfig = new WifiInterfaceConfigImpl(intfName);
        config.addNetInterfaceConfig(netInterfaceConfig);

        try {
            reader.visit(config);
            fail("Exception expected.");
        } catch (KuraException e) {
            assertEquals(KuraErrorCode.CONFIGURATION_ERROR, e.getCode());
        }
    }

    @Test
    public void testReaderVisitWithFirewallFile() throws KuraException {
        String intfName = "testInterface";
        String destinationInterface = "destInterface";

        FirewallAutoNatConfigReader reader = new FirewallAutoNatConfigReader() {

            @Override
            protected Set<NATRule> getAutoNatRules() throws KuraException {
                HashSet<NATRule> natRules = new HashSet<>();

                NATRule rule = new NATRule(intfName, destinationInterface, true);
                natRules.add(rule);

                return natRules;
            }

            @Override
            protected Properties getKuranetProperties() {
                return null;
            }
        };

        NetworkConfiguration config = prepareNetworkConfiguration(intfName, destinationInterface, false, true);

        reader.visit(config);

        assertEquals(1, config.getNetInterfaceConfigs().size());

        NetInterfaceConfig<? extends NetInterfaceAddressConfig> cfg = config.getNetInterfaceConfig(intfName);
        assertEquals(1, cfg.getNetInterfaceAddresses().size());

        NetInterfaceAddressConfig addressConfig = cfg.getNetInterfaceAddresses().get(0);
        assertNotNull(addressConfig.getConfigs());
        assertEquals(1, addressConfig.getConfigs().size());

        NetConfig netConfig = addressConfig.getConfigs().get(0);
        assertTrue(netConfig instanceof FirewallAutoNatConfig);
        assertEquals(intfName, ((FirewallAutoNatConfig) netConfig).getSourceInterface());
        assertEquals(destinationInterface, ((FirewallAutoNatConfig) netConfig).getDestinationInterface());
        assertTrue(((FirewallAutoNatConfig) netConfig).isMasquerade());
    }

}
