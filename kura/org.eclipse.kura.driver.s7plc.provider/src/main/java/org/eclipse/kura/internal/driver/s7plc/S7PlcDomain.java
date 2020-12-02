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

import java.util.Objects;

public class S7PlcDomain {

    private final int db;
    private final S7PlcArea area;

    public S7PlcDomain(int db, S7PlcArea area) {
        this.db = db;
        this.area = area;
    }

    public int getDB() {
        return this.db;
    }

    public S7PlcArea getArea() {
        return this.area;
    }

    @Override
    public int hashCode() {
        return Objects.hash(area, db);
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
        S7PlcDomain other = (S7PlcDomain) obj;
        return area == other.area && db == other.db;
    }

}
