/*******************************************************************************
 * Copyright (c) 2011, 2020 Eurotech and/or its affiliates and others
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
package org.eclipse.kura.net;

/**
 * The current state of the a NetworkInterface.
 */
public enum NetInterfaceState {

    /** The device is in an unknown state. */
    UNKNOWN(0),
    /** The device is recognized but not managed by NetworkManager. */
    UNMANAGED(10),
    /** The device cannot be used (carrier off, rfkill, etc). */
    UNAVAILABLE(20),
    /** The device is not connected. */
    DISCONNECTED(30),
    /** The device is preparing to connect. */
    PREPARE(40),
    /** The device is being configured. */
    CONFIG(50),
    /** The device is awaiting secrets necessary to continue connection. */
    NEED_AUTH(60),
    /** The IP settings of the device are being requested and configured. */
    IP_CONFIG(70),
    /** The device's IP connectivity ability is being determined. */
    IP_CHECK(80),
    /** The device is waiting for secondary connections to be activated. */
    SECONDARIES(90),
    /** The device is active. */
    ACTIVATED(100),
    /** The device's network connection is being torn down. */
    DEACTIVATING(110),
    /** The device is in a failure state following an attempt to activate it. */
    FAILED(120);

    private int code;

    private NetInterfaceState(int code) {
        this.code = code;
    }

    public static NetInterfaceState parseCode(int code) {
        for (NetInterfaceState state : NetInterfaceState.values()) {
            if (state.code == code) {
                return state;
            }
        }

        return null;
    }
}
