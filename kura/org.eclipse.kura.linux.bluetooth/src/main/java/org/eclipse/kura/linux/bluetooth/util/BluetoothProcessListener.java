/*******************************************************************************
 * Copyright (c) 2011, 2021 Eurotech and/or its affiliates and others
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
package org.eclipse.kura.linux.bluetooth.util;

/**
 * @deprecated since {@link org.eclipse.kura.linux.bluetooth.util} version 1.0.600
 */
@Deprecated
public interface BluetoothProcessListener {

    public void processInputStream(String string);

    public void processInputStream(int ch);

    public void processErrorStream(String string);

}
