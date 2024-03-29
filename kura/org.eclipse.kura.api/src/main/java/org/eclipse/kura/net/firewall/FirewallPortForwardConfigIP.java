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

import java.net.UnknownHostException;

import org.eclipse.kura.net.IP4Address;
import org.eclipse.kura.net.IPAddress;
import org.eclipse.kura.net.NetProtocol;
import org.eclipse.kura.net.NetworkPair;
import org.osgi.annotation.versioning.ProviderType;

/**
 * The base class for firewall port forward configurations
 *
 * @param <T>
 *            the type of IPAddess
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
@ProviderType
public abstract class FirewallPortForwardConfigIP<T extends IPAddress> implements FirewallPortForwardConfig {

    /** The interface name on which this configuration will listen for inbound connections **/
    private String inboundIface;

    /** The interface name on which packet will be forwarded */
    private String outboundIface;

    /** The LAN address to forward to **/
    private T address;

    /** The protocol (TCP or UDP) to listen for and forward **/
    private NetProtocol protocol;

    /** The inbound (WAN) port to listen on **/
    private int inPort;

    /** The outbound (LAN) port to listen on **/
    private int outPort;

    /** use masquerading */
    private boolean masquerade;

    /** The (optional) permitted network for inbound connections **/
    private NetworkPair<T> permittedNetwork;

    /** The (optional) permitted MAC address for inbound connections **/
    private String permittedMac;

    /** The (options) permitted source port range for inbound connections **/
    private String sourcePortRange;

    /**
     * Creates and empty port forward configuration
     * 
     * @deprecated since 2.6. Use the FirewallPortForwardConfigIP builder
     */
    @Deprecated
    public FirewallPortForwardConfigIP() {
        super();
    }

    /**
     * Creates a complete port forward configuration
     *
     * @param inboundIface
     *            The interface name on which this configuration will listen for inbound connections
     * @param outboundIface
     *            The inetrface name on which packet will be forwarded
     * @param address
     *            The LAN address to forward to
     * @param protocol
     *            The protocol (TCP or UDP) to listen for and forward
     * @param inPort
     *            The inbound (WAN) port to listen on
     * @param outPort
     *            The outbound (LAN) port to listen on
     * @param masquerade
     *            Use masquerade
     * @param permittedNetwork
     *            The (optional) permitted network for inbound connections
     * @param permittedMac
     *            The (optional) permitted MAC address for inbound connections
     * @param sourcePortRange
     *            The (options) permitted source port range for inbound connections
     * @deprecated since 2.6. Use the FirewallPortForwardConfigIP builder
     */
    @SuppressWarnings("checkstyle:parameterNumber")
    @Deprecated
    public FirewallPortForwardConfigIP(String inboundIface, String outboundIface, IP4Address address,
            NetProtocol protocol, int inPort, int outPort, boolean masquerade, NetworkPair<T> permittedNetwork,
            String permittedMac, String sourcePortRange) {
        super();
        this.inboundIface = inboundIface;
        this.outboundIface = outboundIface;
        this.address = (T) address;
        this.protocol = protocol;
        this.inPort = inPort;
        this.outPort = outPort;
        this.masquerade = masquerade;
        this.permittedNetwork = permittedNetwork;
        this.permittedMac = permittedMac;
        this.sourcePortRange = sourcePortRange;
    }

    protected FirewallPortForwardConfigIP(FirewallPortForwardConfigIPBuilder<T, ?> builder) {
        this.inboundIface = builder.inboundIface;
        this.outboundIface = builder.outboundIface;
        this.address = builder.address;
        this.protocol = builder.protocol;
        this.inPort = builder.inPort;
        this.outPort = builder.outPort;
        this.masquerade = builder.masquerade;
        this.permittedNetwork = builder.permittedNetwork;
        this.permittedMac = builder.permittedMac;
        this.sourcePortRange = builder.sourcePortRange;
    }

    @Override
    public String getInboundInterface() {
        return this.inboundIface;
    }

    /**
     * @deprecated since 2.6. Use the FirewallPortForwardConfigIP builder
     */
    @Deprecated
    public void setInboundInterface(String interfaceName) {
        this.inboundIface = interfaceName;
    }

    @Override
    public String getOutboundInterface() {
        return this.outboundIface;
    }

    /**
     * @deprecated since 2.6. Use the FirewallPortForwardConfigIP builder
     */
    @Deprecated
    public void setOutboundInterface(String interfaceName) {
        this.outboundIface = interfaceName;
    }

    /**
     * @deprecated since 2.6. Use {@link getIPAddress}
     */
    @Override
    @Deprecated
    public IP4Address getAddress() {
        return (IP4Address) this.address;
    }

    /**
     * @deprecated since 2.6. Use the FirewallPortForwardConfigIP builder
     */
    @Deprecated
    public void setAddress(IP4Address address) {
        this.address = (T) address;
    }

    /**
     * @since 2.6
     */
    @Override
    public T getIPAddress() {
        return this.address;
    }

    @Override
    public NetProtocol getProtocol() {
        return this.protocol;
    }

    /**
     * @deprecated since 2.6. Use the FirewallPortForwardConfigIP builder
     */
    @Deprecated
    public void setProtocol(NetProtocol protocol) {
        this.protocol = protocol;
    }

    @Override
    public int getInPort() {
        return this.inPort;
    }

    /**
     * @deprecated since 2.6. Use the FirewallPortForwardConfigIP builder
     */
    @Deprecated
    public void setInPort(int inPort) {
        this.inPort = inPort;
    }

    @Override
    public int getOutPort() {
        return this.outPort;
    }

    /**
     * @deprecated since 2.6. Use the FirewallPortForwardConfigIP builder
     */
    @Deprecated
    public void setOutPort(int outPort) {
        this.outPort = outPort;
    }

    @Override
    public boolean isMasquerade() {
        return this.masquerade;
    }

    /**
     * @deprecated since 2.6. Use the FirewallPortForwardConfigIP builder
     */
    @Deprecated
    public void setMasquerade(boolean masquerade) {
        this.masquerade = masquerade;
    }

    @Override
    public NetworkPair<T> getPermittedNetwork() {
        return this.permittedNetwork;
    }

    /**
     * @deprecated since 2.6. Use the FirewallPortForwardConfigIP builder
     */
    @Deprecated
    public void setPermittedNetwork(NetworkPair<T> permittedNetwork) {
        this.permittedNetwork = permittedNetwork;
    }

    @Override
    public String getPermittedMac() {
        return this.permittedMac;
    }

    /**
     * @deprecated since 2.6. Use the FirewallPortForwardConfigIP builder
     */
    @Deprecated
    public void setPermittedMac(String permittedMac) {
        this.permittedMac = permittedMac;
    }

    @Override
    public String getSourcePortRange() {
        return this.sourcePortRange;
    }

    /**
     * @deprecated since 2.6. Use the FirewallPortForwardConfigIP builder
     */
    @Deprecated
    public void setSourcePortRange(String sourcePortRange) {
        this.sourcePortRange = sourcePortRange;
    }

    /**
     * The base builder class for firewall port forward configurations
     * 
     * @since 2.6
     */
    @ProviderType
    public abstract static class FirewallPortForwardConfigIPBuilder<U extends IPAddress, T extends FirewallPortForwardConfigIPBuilder<U, T>> {

        protected String inboundIface;
        protected String outboundIface;
        protected U address;
        protected NetProtocol protocol;
        protected int inPort;
        protected int outPort;
        protected boolean masquerade = false;
        protected NetworkPair<U> permittedNetwork;
        protected String permittedMac;
        protected String sourcePortRange;

        public T withInboundIface(String inboundIface) {
            this.inboundIface = inboundIface;
            return getThis();
        }

        public T withOutboundIface(String outboundIface) {
            this.outboundIface = outboundIface;
            return getThis();
        }

        public T withAddress(U address) {
            this.address = address;
            return getThis();
        }

        public T withProtocol(NetProtocol protocol) {
            this.protocol = protocol;
            return getThis();
        }

        public T withInPort(int inPort) {
            this.inPort = inPort;
            return getThis();
        }

        public T withOutPort(int outPort) {
            this.outPort = outPort;
            return getThis();
        }

        public T withMasquerade(boolean masquerade) {
            this.masquerade = masquerade;
            return getThis();
        }

        public T withPermittedNetwork(NetworkPair<U> permittedNetwork) {
            this.permittedNetwork = permittedNetwork;
            return getThis();
        }

        public T withPermittedMac(String permittedMac) {
            this.permittedMac = permittedMac;
            return getThis();
        }

        public T withSourcePortRange(String sourcePortRange) {
            this.sourcePortRange = sourcePortRange;
            return getThis();
        }

        public abstract T getThis();

        public abstract FirewallPortForwardConfigIP<U> build() throws UnknownHostException;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (this.address == null ? 0 : this.address.hashCode());
        result = prime * result + this.inPort;
        result = prime * result + (this.inboundIface == null ? 0 : this.inboundIface.hashCode());
        result = prime * result + (this.outboundIface == null ? 0 : this.outboundIface.hashCode());
        result = prime * result + (this.masquerade ? 1231 : 1237);
        result = prime * result + this.outPort;
        result = prime * result + (this.permittedMac == null ? 0 : this.permittedMac.hashCode());
        result = prime * result + (this.permittedNetwork == null ? 0 : this.permittedNetwork.hashCode());
        result = prime * result + (this.protocol == null ? 0 : this.protocol.hashCode());
        result = prime * result + (this.sourcePortRange == null ? 0 : this.sourcePortRange.hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("FirewallPortForwardConfigIP [inboundIface=");
        builder.append(this.inboundIface);
        builder.append(", outboundIface=");
        builder.append(this.outboundIface);
        builder.append(", address=");
        builder.append(this.address);
        builder.append(", protocol=");
        builder.append(this.protocol);
        builder.append(", inPort=");
        builder.append(this.inPort);
        builder.append(", outPort=");
        builder.append(this.outPort);
        builder.append(", permittedNetwork=");
        builder.append(this.permittedNetwork);
        builder.append(", permittedMac=");
        builder.append(this.permittedMac);
        builder.append(", sourcePortRange=");
        builder.append(this.sourcePortRange);
        builder.append("]");
        return builder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        @SuppressWarnings("rawtypes")
        FirewallPortForwardConfigIP other = (FirewallPortForwardConfigIP) obj;
        if (this.address == null) {
            if (other.address != null) {
                return false;
            }
        } else if (!this.address.equals(other.address)) {
            return false;
        }
        if (this.inPort != other.inPort) {
            return false;
        }
        if (this.inboundIface == null) {
            if (other.inboundIface != null) {
                return false;
            }
        } else if (!this.inboundIface.equals(other.inboundIface)) {
            return false;
        }
        if (this.outboundIface == null) {
            if (other.outboundIface != null) {
                return false;
            }
        } else if (!this.outboundIface.equals(other.outboundIface)) {
            return false;
        }
        if (this.outPort != other.outPort) {
            return false;
        }
        if (this.masquerade != other.masquerade) {
            return false;
        }
        if (this.permittedMac == null) {
            if (other.permittedMac != null) {
                return false;
            }
        } else if (!this.permittedMac.equals(other.permittedMac)) {
            return false;
        }
        if (this.permittedNetwork == null) {
            if (other.permittedNetwork != null) {
                return false;
            }
        } else if (!this.permittedNetwork.equals(other.permittedNetwork)) {
            return false;
        }
        if (this.protocol != other.protocol) {
            return false;
        }
        if (this.sourcePortRange == null) {
            if (other.sourcePortRange != null) {
                return false;
            }
        } else if (!this.sourcePortRange.equals(other.sourcePortRange)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isValid() {
        if (this.inboundIface == null || this.inboundIface.trim().isEmpty()) {
            return false;
        }

        if (this.outboundIface == null || this.outboundIface.trim().isEmpty()) {
            return false;
        }

        if (this.address == null) {
            return false;
        }

        if (this.inPort < 0 || this.inPort > 65535 || this.outPort < 0 || this.outPort > 65535) {
            return false;
        }

        if (this.protocol == null || !this.protocol.equals(NetProtocol.tcp) || !this.protocol.equals(NetProtocol.udp)) {
            return false;
        }

        // TODO - add checks for optional parameters to make sure if they are not null they are valid

        return true;
    }
}
