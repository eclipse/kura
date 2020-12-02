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
 *   Amit Kumar Mondal (admin@amitinside.com)
 */
package org.eclipse.kura.internal.driver.s7plc;

import java.util.List;

import org.eclipse.kura.core.configuration.metatype.Tad;
import org.eclipse.kura.core.configuration.metatype.Toption;
import org.eclipse.kura.core.configuration.metatype.Tscalar;
import org.eclipse.kura.driver.ChannelDescriptor;
import org.eclipse.kura.util.collection.CollectionUtil;

import Moka7.S7;

/**
 * S7 PLC specific channel descriptor. The descriptor contains the following
 * attribute definition identifiers.
 *
 * <ul>
 * <li>area.no</li> denotes the Area Number
 * <li>offset</li> the offset
 * <li>byte.count</li> the number of bytes to read
 * </ul>
 */
public final class S7PlcChannelDescriptor implements ChannelDescriptor {

    public static final String S7_ELEMENT_TYPE_ID = "s7.data.type";
    public static final String DATA_BLOCK_NO_ID = "data.block.no";
    public static final String AREA_NO_ID = "s7.area.type";
    public static final String BYTE_COUNT_ID = "byte.count";
    public static final String OFFSET_ID = "offset";
    public static final String BIT_INDEX_ID = "bit.index";

    private Toption generateDataTypeOption(S7PlcDataType type) {
        Toption option = new Toption();
        option.setLabel(type.name());
        option.setValue(type.name());
        return option;
    }

    private Toption generateAreaOption(S7PlcArea area) {
        Toption option = new Toption();
        option.setLabel(area.toString());
        option.setValue(String.valueOf(area.getValue()));
        return option;
    }

    /** {@inheritDoc} */
    @Override
    public Object getDescriptor() {
        final List<Tad> elements = CollectionUtil.newArrayList();

        final Tad s7ElementType = new Tad();
        s7ElementType.setName(S7_ELEMENT_TYPE_ID);
        s7ElementType.setId(S7_ELEMENT_TYPE_ID);
        s7ElementType.setDescription("S7 Data Type");
        s7ElementType.setType(Tscalar.STRING);
        s7ElementType.setRequired(true);
        s7ElementType.setDefault(S7PlcDataType.INT.name());

        for (S7PlcDataType t : S7PlcDataType.values()) {
            s7ElementType.setOption(generateDataTypeOption(t));
        }

        elements.add(s7ElementType);

        final Tad areaNo = new Tad();
        areaNo.setName(AREA_NO_ID);
        areaNo.setId(AREA_NO_ID);
        areaNo.setDescription("S7 DB area");
        areaNo.setType(Tscalar.INTEGER);
        areaNo.setRequired(true);
        areaNo.setDefault(String.valueOf(S7.S7AreaDB));

        for (S7PlcArea a : S7PlcArea.values()) {
            areaNo.setOption(generateAreaOption(a));
        }

        elements.add(areaNo);

        final Tad dbNo = new Tad();
        dbNo.setName(DATA_BLOCK_NO_ID);
        dbNo.setId(DATA_BLOCK_NO_ID);
        dbNo.setDescription("DB number");
        dbNo.setType(Tscalar.INTEGER);
        dbNo.setRequired(true);
        dbNo.setDefault("0");

        elements.add(dbNo);

        final Tad offset = new Tad();
        offset.setName(OFFSET_ID);
        offset.setId(OFFSET_ID);
        offset.setDescription("offset");
        offset.setType(Tscalar.INTEGER);
        offset.setRequired(true);
        offset.setDefault("0");

        elements.add(offset);

        final Tad byteCount = new Tad();
        byteCount.setName(BYTE_COUNT_ID);
        byteCount.setId(BYTE_COUNT_ID);
        byteCount.setDescription("Byte Count");
        byteCount.setType(Tscalar.INTEGER);
        byteCount.setRequired(true);
        byteCount.setDefault("0");

        elements.add(byteCount);

        final Tad bitIndex = new Tad();
        bitIndex.setName(BIT_INDEX_ID);
        bitIndex.setId(BIT_INDEX_ID);
        bitIndex.setDescription("Bit Index");
        bitIndex.setType(Tscalar.INTEGER);
        bitIndex.setRequired(true);
        bitIndex.setMin("0");
        bitIndex.setMax("7");
        bitIndex.setDefault("0");

        elements.add(bitIndex);

        return elements;
    }

}
