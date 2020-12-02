/**
 * Copyright (c) 2017, 2020 Eurotech and/or its affiliates
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Eurotech
 */
package org.eclipse.kura.internal.driver.s7plc;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;

import Moka7.S7;

public enum S7PlcArea {

    AREA_CT(S7.S7AreaCT) {

        @Override
        public String toString() {
            return "Counters";
        }
    },
    AREA_PE(S7.S7AreaPE) {

        @Override
        public String toString() {
            return "Inputs";
        }
    },
    AREA_PA(S7.S7AreaPA) {

        @Override
        public String toString() {
            return "Outputs";
        }
    },
    AREA_MK(S7.S7AreaMK) {

        @Override
        public String toString() {
            return "Merkers";
        }
    },
    AREA_TM(S7.S7AreaTM) {

        @Override
        public String toString() {
            return "Timers";
        }
    },
    AREA_DB(S7.S7AreaDB) {

        @Override
        public String toString() {
            return "Data Blocks";
        }
    };

    private int value;

    S7PlcArea(int value) {
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }

    public static S7PlcArea fromValue(final int value) throws KuraException {
        switch (value) {
        case S7.S7AreaCT:
            return AREA_CT;
        case S7.S7AreaPE:
            return AREA_PE;
        case S7.S7AreaPA:
            return AREA_PA;
        case S7.S7AreaMK:
            return AREA_MK;
        case S7.S7AreaTM:
            return AREA_TM;
        case S7.S7AreaDB:
            return AREA_DB;
        default:
            throw new KuraException(KuraErrorCode.INVALID_PARAMETER, "Unsupported area: " + value);
        }
    }
}
