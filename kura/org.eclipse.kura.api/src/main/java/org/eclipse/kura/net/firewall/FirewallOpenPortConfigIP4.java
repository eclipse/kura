/*******************************************************************************
 * Copyright (c) 2011, 2023 Eurotech and/or its affiliates and others
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
package org.eclipse.kura.net.firewall;

import org.eclipse.kura.net.IP4Address;
import org.eclipse.kura.net.NetProtocol;
import org.eclipse.kura.net.NetworkPair;
import org.osgi.annotation.versioning.ProviderType;

/**
 * The implementation of IPv4 firewall open port configurations
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
@ProviderType
public class FirewallOpenPortConfigIP4 extends FirewallOpenPortConfigIP<IP4Address> implements FirewallOpenPortConfig4 {

    /**
     * @deprecated since 2.6. Use {@link FirewallOpenPortConfigIP4.builder()}
     */
    @Deprecated
    public FirewallOpenPortConfigIP4() {
        super();
    }

    /**
     * @deprecated since 2.6. Uuse {@link FirewallOpenPortConfigIP4.builder()}
     */
    @Deprecated
    public FirewallOpenPortConfigIP4(int port, NetProtocol protocol, NetworkPair<IP4Address> permittedNetwork,
            String permittedInterfaceName, String unpermittedInterfaceName, String permittedMac,
            String sourcePortRange) {
        super(port, protocol, permittedNetwork, permittedInterfaceName, unpermittedInterfaceName, permittedMac,
                sourcePortRange);
    }

    /**
     * @deprecated since 2.6. Uuse {@link FirewallOpenPortConfigIP4.builder()}
     */
    @Deprecated
    public FirewallOpenPortConfigIP4(String portRange, NetProtocol protocol, NetworkPair<IP4Address> permittedNetwork,
            String permittedInterfaceName, String unpermittedInterfaceName, String permittedMac,
            String sourcePortRange) {
        super(portRange, protocol, permittedNetwork, permittedInterfaceName, unpermittedInterfaceName, permittedMac,
                sourcePortRange);
    }

    private FirewallOpenPortConfigIP4(FirewallOpenPortConfigIP4Builder builder) {
        super(builder);
    }

    public static FirewallOpenPortConfigIP4Builder builder() {
        return new FirewallOpenPortConfigIP4Builder();
    }

    public static class FirewallOpenPortConfigIP4Builder
            extends FirewallOpenPortConfigIPBuilder<IP4Address, FirewallOpenPortConfigIP4Builder> {

        @Override
        public FirewallOpenPortConfigIP4 build() {
            return new FirewallOpenPortConfigIP4(this);
        }

        @Override
        public FirewallOpenPortConfigIP4Builder getThis() {
            return this;
        }
    }
}
