/*******************************************************************************
 * Copyright (c) 2017, 2022 Eurotech and/or its affiliates and others
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
package org.eclipse.kura.wire.script.filter.provider;

/**
 * @deprecated as of Kura 5.3.0
 */
@Deprecated
public class WireEnvelopeWrapper {

    @SuppressWarnings("checkstyle:visibilityModifier")
    public final String emitterPid;
    
    @SuppressWarnings("checkstyle:visibilityModifier")
    public final WireRecordListWrapper records;

    WireEnvelopeWrapper(WireRecordListWrapper records, String emitterPid) {
        this.records = records;
        this.emitterPid = emitterPid;
    }
}
