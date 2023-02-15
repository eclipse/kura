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
 ******************************************************************************/
package org.eclipse.kura.net.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.kura.net.IPAddress;

public class NetworkInterfaceIpAddressStatus<T extends IPAddress> {

    private final List<NetworkInterfaceIpAddress<T>> addresses;
    private Optional<T> gateway;
    private final List<T> dnsServerAddresses;

    public NetworkInterfaceIpAddressStatus() {
        this.addresses = new ArrayList<>();
        this.gateway = Optional.empty();
        this.dnsServerAddresses = new ArrayList<>();
    }

    public List<NetworkInterfaceIpAddress<T>> getAddresses() {
        return this.addresses;
    }

    public void addAddress(NetworkInterfaceIpAddress<T> address) {
        this.addresses.add(address);
    }

    public Optional<T> getGateway() {
        return this.gateway;
    }

    public void setGateway(T gateway) {
        this.gateway = Optional.of(gateway);
    }

    public List<T> getDnsServerAddresses() {
        return this.dnsServerAddresses;
    }

    public void addDnsServerAddress(T dnsServerAddress) {
        this.dnsServerAddresses.add(dnsServerAddress);
    }

}
