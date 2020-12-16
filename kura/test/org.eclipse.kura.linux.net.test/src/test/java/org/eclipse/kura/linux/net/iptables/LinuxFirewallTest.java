/*******************************************************************************
 * Copyright (c) 2020 Eurotech and/or its affiliates and others
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
package org.eclipse.kura.linux.net.iptables;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.KuraIOException;
import org.eclipse.kura.net.IP4Address;
import org.eclipse.kura.net.IPAddress;
import org.eclipse.kura.net.NetworkPair;
import org.junit.Test;

public class LinuxFirewallTest extends FirewallTestUtils {

    @SuppressWarnings("unused")
    @Test
    public void LinuxFirewallConstructorTest() {
        setUpMock();
        new LinuxFirewall(executorServiceMock);

        verify(executorServiceMock, atLeast(1)).execute(commandRestore);
        verify(executorServiceMock, times(1)).execute(commandSave);
    }

    @Test
    public void addLocalRuleTest() throws KuraException {
        setUpMock();
        LinuxFirewall linuxFirewall = new LinuxFirewall(executorServiceMock);
        try {
            linuxFirewall.addLocalRule(5400, "tcp", null, null, "eth0", null, "00:11:22:33:44:55:66", "10100:10200");
        } catch (KuraIOException e) {
            // do nothing...
        }

        assertTrue(linuxFirewall.getLocalRules().stream().anyMatch(rule -> {
            return rule.getPort() == 5400 && rule.getProtocol().equals("tcp")
                    && rule.getPermittedInterfaceName().equals("eth0")
                    && rule.getPermittedMAC().equals("00:11:22:33:44:55:66")
                    && rule.getSourcePortRange().equals("10100:10200");
        }));
    }

    @Test
    public void addLocalRulesTest() throws KuraException {
        setUpMock();
        LinuxFirewall linuxFirewall = new LinuxFirewall(executorServiceMock);
        List<LocalRule> rules = new ArrayList<>();
        try {
            rules.add(new LocalRule(5400, "tcp",
                    new NetworkPair<>((IP4Address) IPAddress.parseHostAddress("0.0.0.0"), (short) 0), "eth0", null,
                    "00:11:22:33:44:55:66", "10100:10200"));
            linuxFirewall.addLocalRules(rules);
        } catch (KuraIOException | UnknownHostException e) {
            // do nothing...
        }

        verify(executorServiceMock, atLeast(1)).execute(commandRestore);
        verify(executorServiceMock, times(2)).execute(commandSave);
        verify(executorServiceMock, times(1)).execute(commandFlushInputFilter);
        verify(executorServiceMock, times(1)).execute(commandFlushOutputFilter);
        verify(executorServiceMock, times(1)).execute(commandFlushForwardFilter);
        verify(executorServiceMock, times(1)).execute(commandFlushInputNat);
        verify(executorServiceMock, times(1)).execute(commandFlushOutputNat);
        verify(executorServiceMock, times(1)).execute(commandFlushPreroutingNat);
        verify(executorServiceMock, times(1)).execute(commandFlushPostroutingNat);
    }

    @Test
    public void addPortForwardTest() throws KuraException {
        setUpMock();
        LinuxFirewall linuxFirewall = new LinuxFirewall(executorServiceMock);
        try {
            linuxFirewall.addPortForwardRule("eth0", "eth1", "172.16.0.1", "tcp", 3040, 4050, true, "172.16.0.100",
                    "32", "00:11:22:33:44:55:66", "10100:10200");
        } catch (KuraIOException e) {
            // do nothing...
        }

        assertTrue(linuxFirewall.getPortForwardRules().stream().anyMatch(rule -> {
            return rule.getInboundIface().equals("eth0") && rule.getOutboundIface().equals("eth1")
                    && rule.getAddress().equals("172.16.0.1") && rule.getProtocol().equals("tcp")
                    && rule.getInPort() == 3040 && rule.getOutPort() == 4050 && rule.isMasquerade()
                    && rule.getPermittedNetwork().equals("172.16.0.100") && rule.getPermittedNetworkMask() == 32
                    && rule.getPermittedMAC().equals("00:11:22:33:44:55:66")
                    && rule.getSourcePortRange().equals("10100:10200");
        }));
    }

    @Test
    public void addPortForwardRulesTest() throws KuraException {
        setUpMock();
        LinuxFirewall linuxFirewall = new LinuxFirewall(executorServiceMock);
        List<PortForwardRule> rules = new ArrayList<>();
        try {
            rules.add(new PortForwardRule("eth0", "eth1", "172.16.0.1", "tcp", 3040, 4050, true, "172.16.0.100", 32,
                    "00:11:22:33:44:55:66", "10100:10200"));
            linuxFirewall.addPortForwardRules(rules);
        } catch (KuraIOException e) {
            // do nothing...
        }

        verify(executorServiceMock, atLeast(1)).execute(commandRestore);
        verify(executorServiceMock, times(2)).execute(commandSave);
        verify(executorServiceMock, times(1)).execute(commandFlushInputFilter);
        verify(executorServiceMock, times(1)).execute(commandFlushOutputFilter);
        verify(executorServiceMock, times(1)).execute(commandFlushForwardFilter);
        verify(executorServiceMock, times(1)).execute(commandFlushInputNat);
        verify(executorServiceMock, times(1)).execute(commandFlushOutputNat);
        verify(executorServiceMock, times(1)).execute(commandFlushPreroutingNat);
        verify(executorServiceMock, times(1)).execute(commandFlushPostroutingNat);
    }

    @Test
    public void addAutoNatRuleTest() throws KuraException {
        setUpMock();
        LinuxFirewall linuxFirewall = new LinuxFirewall(executorServiceMock);
        try {
            linuxFirewall.addNatRule("eth0", "eth1", true);
        } catch (KuraIOException e) {
            // do nothing...
        }

        assertTrue(linuxFirewall.getAutoNatRules().stream().anyMatch(rule -> {
            return rule.getSourceInterface().equals("eth0") && rule.getDestinationInterface().equals("eth1")
                    && rule.isMasquerade();
        }));
    }

    @Test
    public void addAutoNatRulesTest() throws KuraException {
        setUpMock();
        LinuxFirewall linuxFirewall = new LinuxFirewall(executorServiceMock);
        List<NATRule> rules = new ArrayList<>();
        try {
            rules.add(new NATRule("eth0", "eth1", true));
            linuxFirewall.addAutoNatRules(rules);
        } catch (KuraIOException e) {
            // do nothing...
        }

        verify(executorServiceMock, atLeast(1)).execute(commandRestore);
        verify(executorServiceMock, times(2)).execute(commandSave);
        verify(executorServiceMock, times(1)).execute(commandFlushInputFilter);
        verify(executorServiceMock, times(1)).execute(commandFlushOutputFilter);
        verify(executorServiceMock, times(1)).execute(commandFlushForwardFilter);
        verify(executorServiceMock, times(1)).execute(commandFlushInputNat);
        verify(executorServiceMock, times(1)).execute(commandFlushOutputNat);
        verify(executorServiceMock, times(1)).execute(commandFlushPreroutingNat);
        verify(executorServiceMock, times(1)).execute(commandFlushPostroutingNat);
    }

    @Test
    public void addNatRuleTest() throws KuraException {
        setUpMock();
        LinuxFirewall linuxFirewall = new LinuxFirewall(executorServiceMock);
        try {
            linuxFirewall.addNatRule("eth0", "eth1", "tcp", "172.16.0.1", "172.16.0.2", true);
        } catch (KuraIOException e) {
            // do nothing...
        }

        assertTrue(linuxFirewall.getNatRules().stream().anyMatch(rule -> {
            return rule.getSourceInterface().equals("eth0") && rule.getDestinationInterface().equals("eth1")
                    && rule.getSource().equals("172.16.0.1") && rule.getDestination().equals("172.16.0.2")
                    && rule.isMasquerade();
        }));
    }

    @Test
    public void addNatRulesTest() throws KuraException {
        setUpMock();
        LinuxFirewall linuxFirewall = new LinuxFirewall(executorServiceMock);
        List<NATRule> rules = new ArrayList<>();
        try {
            rules.add(new NATRule("eth0", "eth1", "tcp", "172.16.0.1", "172.16.0.2", true));
            linuxFirewall.addAutoNatRules(rules);
        } catch (KuraIOException e) {
            // do nothing...
        }

        verify(executorServiceMock, atLeast(1)).execute(commandRestore);
        verify(executorServiceMock, times(2)).execute(commandSave);
        verify(executorServiceMock, times(1)).execute(commandFlushInputFilter);
        verify(executorServiceMock, times(1)).execute(commandFlushOutputFilter);
        verify(executorServiceMock, times(1)).execute(commandFlushForwardFilter);
        verify(executorServiceMock, times(1)).execute(commandFlushInputNat);
        verify(executorServiceMock, times(1)).execute(commandFlushOutputNat);
        verify(executorServiceMock, times(1)).execute(commandFlushPreroutingNat);
        verify(executorServiceMock, times(1)).execute(commandFlushPostroutingNat);
    }

    @Test
    public void deleteLocalRuleTest() throws KuraException, UnknownHostException {
        setUpMock();
        LinuxFirewall linuxFirewall = new LinuxFirewall(executorServiceMock);
        try {
            linuxFirewall.addLocalRule(5400, "tcp", null, null, "eth0", null, "00:11:22:33:44:55:66", "10100:10200");
        } catch (KuraIOException e) {
            // do nothing...
        }

        assertFalse(linuxFirewall.getLocalRules().isEmpty());
        int size = linuxFirewall.getLocalRules().size();

        LocalRule rule = new LocalRule(5400, "tcp",
                new NetworkPair<>((IP4Address) IPAddress.parseHostAddress("0.0.0.0"), (short) 0), "eth0", null,
                "00:11:22:33:44:55:66", "10100:10200");
        try {
            linuxFirewall.deleteLocalRule(rule);
        } catch (KuraIOException e) {
            // do nothing...
        }

        assertEquals(size - 1, linuxFirewall.getLocalRules().size());
    }

    @Test
    public void deletePortForwardRuleTest() throws KuraException, UnknownHostException {
        setUpMock();
        LinuxFirewall linuxFirewall = new LinuxFirewall(executorServiceMock);
        try {
            linuxFirewall.addPortForwardRule("eth0", "eth1", "172.16.0.1", "tcp", 3040, 4050, true, "172.16.0.100",
                    "32", "00:11:22:33:44:55:66", "10100:10200");
        } catch (KuraIOException e) {
            // do nothing...
        }

        assertFalse(linuxFirewall.getPortForwardRules().isEmpty());
        int size = linuxFirewall.getPortForwardRules().size();

        PortForwardRule rule = new PortForwardRule("eth0", "eth1", "172.16.0.1", "tcp", 3040, 4050, true,
                "172.16.0.100", 32, "00:11:22:33:44:55:66", "10100:10200");
        try {
            linuxFirewall.deletePortForwardRule(rule);
        } catch (KuraIOException e) {
            // do nothing...
        }

        assertEquals(size - 1, linuxFirewall.getPortForwardRules().size());
    }

    @Test
    public void deleteAutoNatRuleTest() throws KuraException, UnknownHostException {
        setUpMock();
        LinuxFirewall linuxFirewall = new LinuxFirewall(executorServiceMock);
        try {
            linuxFirewall.addNatRule("eth0", "eth1", true);
        } catch (KuraIOException e) {
            // do nothing...
        }

        assertFalse(linuxFirewall.getAutoNatRules().isEmpty());
        int size = linuxFirewall.getAutoNatRules().size();

        NATRule rule = new NATRule("eth0", "eth1", true);
        try {
            linuxFirewall.deleteAutoNatRule(rule);
        } catch (KuraIOException e) {
            // do nothing...
        }

        assertEquals(size - 1, linuxFirewall.getAutoNatRules().size());
    }

    @Test
    public void deleteAllLocalRuleTest() throws KuraException, UnknownHostException {
        setUpMock();
        LinuxFirewall linuxFirewall = new LinuxFirewall(executorServiceMock);
        try {
            linuxFirewall.addLocalRule(5400, "tcp", null, null, "eth0", null, "00:11:22:33:44:55:66", "10100:10200");
        } catch (KuraIOException e) {
            // do nothing...
        }

        assertFalse(linuxFirewall.getLocalRules().isEmpty());
        try {
            linuxFirewall.deleteAllLocalRules();
        } catch (KuraIOException e) {
            // do nothing...
        }
        assertTrue(linuxFirewall.getLocalRules().isEmpty());
    }

    @Test
    public void deleteAllPortForwardRuleTest() throws KuraException, UnknownHostException {
        setUpMock();
        LinuxFirewall linuxFirewall = new LinuxFirewall(executorServiceMock);
        try {
            linuxFirewall.addPortForwardRule("eth0", "eth1", "172.16.0.1", "tcp", 3040, 4050, true, "172.16.0.100",
                    "32", "00:11:22:33:44:55:66", "10100:10200");
        } catch (KuraIOException e) {
            // do nothing...
        }

        assertFalse(linuxFirewall.getPortForwardRules().isEmpty());
        try {
            linuxFirewall.deleteAllPortForwardRules();
        } catch (KuraIOException e) {
            // do nothing...
        }
        assertTrue(linuxFirewall.getPortForwardRules().isEmpty());
    }

    @Test
    public void deleteAllAutoNatRuleTest() throws KuraException, UnknownHostException {
        setUpMock();
        LinuxFirewall linuxFirewall = new LinuxFirewall(executorServiceMock);
        try {
            linuxFirewall.addNatRule("eth0", "eth1", true);
        } catch (KuraIOException e) {
            // do nothing...
        }

        assertFalse(linuxFirewall.getAutoNatRules().isEmpty());
        try {
            linuxFirewall.deleteAllAutoNatRules();
        } catch (KuraIOException e) {
            // do nothing...
        }
        assertTrue(linuxFirewall.getAutoNatRules().isEmpty());
    }

    @Test
    public void deleteAllNatRuleTest() throws KuraException, UnknownHostException {
        setUpMock();
        LinuxFirewall linuxFirewall = new LinuxFirewall(executorServiceMock);
        try {
            linuxFirewall.addNatRule("eth0", "eth1", "tcp", "172.16.0.1", "172.16.0.2", true);
        } catch (KuraIOException e) {
            // do nothing...
        }

        assertFalse(linuxFirewall.getNatRules().isEmpty());
        try {
            linuxFirewall.deleteAllNatRules();
        } catch (KuraIOException e) {
            // do nothing...
        }
        assertTrue(linuxFirewall.getNatRules().isEmpty());
    }

}
