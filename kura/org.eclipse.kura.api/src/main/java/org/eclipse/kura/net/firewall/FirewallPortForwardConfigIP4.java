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
 * @noextend This class is not intended to be subclassed by clients.
 */
@ProviderType
public class FirewallPortForwardConfigIP4 extends FirewallPortForwardConfigIP<IP4Address>
        implements FirewallPortForwardConfig4 {

    /**
     * @deprecated since 2.6. Use {@link FirewallPortForwardConfigIP4.builder()}
     */
    @Deprecated
    public FirewallPortForwardConfigIP4() {
        super();
    }

    /**
     * @deprecated since 2.6. Use {@link FirewallPortForwardConfigIP4.builder()}
     */
    @SuppressWarnings("checkstyle:parameterNumber")
    @Deprecated
    public FirewallPortForwardConfigIP4(String inboundIface, String outboundIface, IP4Address address,
            NetProtocol protocol, int inPort, int outPort, boolean masquerade, NetworkPair<IP4Address> permittedNetwork,
            String permittedMac, String sourcePortRange) {
        super(inboundIface, outboundIface, address, protocol, inPort, outPort, masquerade, permittedNetwork,
                permittedMac, sourcePortRange);
    }

    private FirewallPortForwardConfigIP4(FirewallPortForwardConfigIP4Builder builder) {
        super(builder);
    }

    public static FirewallPortForwardConfigIP4Builder builder() {
        return new FirewallPortForwardConfigIP4Builder();
    }

    public static class FirewallPortForwardConfigIP4Builder
            extends FirewallPortForwardConfigIPBuilder<IP4Address, FirewallPortForwardConfigIP4Builder> {

        @Override
        public FirewallPortForwardConfigIP4 build() {
            return new FirewallPortForwardConfigIP4(this);
        }

        @Override
        public FirewallPortForwardConfigIP4Builder getThis() {
            return this;
        }
    }

}
