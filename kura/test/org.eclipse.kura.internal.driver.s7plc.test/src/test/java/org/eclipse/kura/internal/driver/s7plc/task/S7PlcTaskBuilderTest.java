/*******************************************************************************
 * Copyright (c) 2017, 2020 Eurotech and/or its affiliates and others
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.eclipse.kura.internal.driver.s7plc.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.eclipse.kura.channel.ChannelRecord;
import org.eclipse.kura.core.testutil.TestUtil;
import org.eclipse.kura.driver.binary.BinaryData;
import org.eclipse.kura.driver.binary.BinaryDataTypes;
import org.eclipse.kura.driver.block.task.AbstractBlockDriver.Pair;
import org.eclipse.kura.driver.block.task.BinaryDataTask;
import org.eclipse.kura.driver.block.task.BitTask;
import org.eclipse.kura.driver.block.task.BlockTask;
import org.eclipse.kura.driver.block.task.ByteArrayTask;
import org.eclipse.kura.driver.block.task.Mode;
import org.eclipse.kura.driver.block.task.StringTask;
import org.eclipse.kura.internal.driver.s7plc.S7PlcDataType;
import org.eclipse.kura.internal.driver.s7plc.S7PlcDomain;
import org.eclipse.kura.type.DataType;
import org.junit.Test;

import Moka7.S7;

public class S7PlcTaskBuilderTest {

    @Test
    public void testBuildTypes() {
        List<ChannelRecord> records = new ArrayList<>();

        for (S7PlcDataType type : S7PlcDataType.values()) {
            DataType channelType = DataType.DOUBLE;
            if (S7PlcDataType.BOOL.equals(type)) {
                channelType = DataType.BOOLEAN;
            } else if (S7PlcDataType.CHAR.equals(type)) {
                channelType = DataType.STRING;
            }
            records.add(createChannelRecord(type.name(), type.ordinal(), channelType));
        }

        records.add(createChannelRecord("BYTE_ARRAY", 123, DataType.BYTE_ARRAY));

        // exception => null => not added
        records.add(createChannelRecord("BYTE_ARRAY", 124, DataType.BYTE_ARRAY, false));

        Mode mode = Mode.READ;

        Stream<Pair<S7PlcDomain, BlockTask>> stream = S7PlcTaskBuilder.build(records, mode);

        AtomicInteger count = new AtomicInteger(0);
        stream.forEach(action -> {
            int idx = count.getAndIncrement();

            BlockTask second = action.getSecond();

            if (idx < 8) {
                assertEquals(idx, second.getStart());
                assertEquals(mode, second.getMode());

                if (S7PlcDataType.INT.ordinal() == second.getStart()) {
                    assertTrue(second instanceof BinaryDataTask);
                    assertExpectedType(BinaryDataTypes.INT16_BE, second);
                }
                if (S7PlcDataType.DINT.ordinal() == second.getStart()) {
                    assertTrue(second instanceof BinaryDataTask);
                    assertExpectedType(BinaryDataTypes.INT32_BE, second);
                }
                if (S7PlcDataType.WORD.ordinal() == second.getStart()) {
                    assertTrue(second instanceof BinaryDataTask);
                    assertExpectedType(BinaryDataTypes.UINT16_BE, second);
                }
                if (S7PlcDataType.DWORD.ordinal() == second.getStart()) {
                    assertTrue(second instanceof BinaryDataTask);
                    assertExpectedType(BinaryDataTypes.UINT32_BE, second);
                }
                if (S7PlcDataType.BYTE.ordinal() == second.getStart()) {
                    assertTrue(second instanceof BinaryDataTask);
                    assertExpectedType(BinaryDataTypes.UINT8, second);
                }
                if (S7PlcDataType.REAL.ordinal() == second.getStart()) {
                    assertTrue(second instanceof BinaryDataTask);
                    assertExpectedType(BinaryDataTypes.FLOAT_BE, second);
                }
                if (S7PlcDataType.CHAR.ordinal() == second.getStart()) {
                    assertTrue(second instanceof StringTask);
                }
                if (S7PlcDataType.BOOL.ordinal() == second.getStart()) {
                    assertTrue(second instanceof BitTask);
                }
            } else {
                assertEquals(123, second.getStart());
                assertTrue(second instanceof ByteArrayTask);
            }
        });

        assertEquals(9, count.get());
    }

    private void assertExpectedType(BinaryData type, BlockTask second) {
        try {
            assertEquals(type, TestUtil.getFieldValue(second, "dataType"));
        } catch (NoSuchFieldException e) {
            fail(e.getMessage());
        }
    }

    private ChannelRecord createChannelRecord(String type, int ord, DataType channelType) {
        return createChannelRecord(type, ord, channelType, true);
    }

    private ChannelRecord createChannelRecord(String type, int ord, DataType channelType, boolean addCount) {
        ChannelRecord channel = ChannelRecord.createReadRecord("ch1", channelType);

        Map<String, Object> config = new HashMap<>();
        config.put("data.block.no", ord);
        config.put("s7.area.type", S7.S7AreaDB);
        config.put("offset", ord);
        config.put("s7.data.type", type);
        config.put("bit.index", ord); // required for BOOL
        if (addCount) {
            config.put("byte.count", ord); // required for CHAR and byte array
        }
        channel.setChannelConfig(config);

        return channel;
    }

}
