/*******************************************************************************
 * Copyright (c) 2017, 2020 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech
 *******************************************************************************/

package org.eclipse.kura.internal.driver.s7plc.task;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.channel.ChannelFlag;
import org.eclipse.kura.channel.ChannelRecord;
import org.eclipse.kura.channel.ChannelStatus;
import org.eclipse.kura.driver.binary.BinaryDataTypes;
import org.eclipse.kura.driver.block.task.AbstractBlockDriver.Pair;
import org.eclipse.kura.driver.block.task.BinaryDataTask;
import org.eclipse.kura.driver.block.task.BitTask;
import org.eclipse.kura.driver.block.task.BlockTask;
import org.eclipse.kura.driver.block.task.ByteArrayTask;
import org.eclipse.kura.driver.block.task.Mode;
import org.eclipse.kura.driver.block.task.StringTask;
import org.eclipse.kura.internal.driver.s7plc.S7PlcArea;
import org.eclipse.kura.internal.driver.s7plc.S7PlcChannelDescriptor;
import org.eclipse.kura.internal.driver.s7plc.S7PlcDataType;
import org.eclipse.kura.internal.driver.s7plc.S7PlcDomain;
import org.eclipse.kura.type.DataType;

public final class S7PlcTaskBuilder {

    private S7PlcTaskBuilder() {
    }

    private static int getDbNo(ChannelRecord record) throws KuraException {
        try {
            return getIntProperty(record, S7PlcChannelDescriptor.DATA_BLOCK_NO_ID, "Error while retrieving Address No");
        } catch (KuraException e) {
            record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
            record.setTimestamp(System.currentTimeMillis());
            throw e;
        }
    }

    private static S7PlcArea getAreaNo(ChannelRecord record) throws KuraException {
        try {
            final int raw = getIntProperty(record, S7PlcChannelDescriptor.AREA_NO_ID, "Error while retrieving Area No");
            return S7PlcArea.fromValue(raw);
        } catch (KuraException e) {
            record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
            record.setTimestamp(System.currentTimeMillis());
            throw e;
        }
    }

    private static int getIntProperty(ChannelRecord record, String propertyName, String failureMessage)
            throws KuraException {
        try {
            return Integer.parseInt(record.getChannelConfig().get(propertyName).toString());
        } catch (Exception e) {
            throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, failureMessage);
        }
    }

    private static void assertChannelType(ChannelRecord record, DataType channelType) throws KuraException {
        if (channelType != record.getValueType()) {
            throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, "Channel Value Type must be " + channelType);
        }
    }

    private static BlockTask build(ChannelRecord record, Mode mode) throws KuraException {

        final Map<String, Object> channelConfig = record.getChannelConfig();

        DataType type = record.getValueType();

        int offset = getIntProperty(record, S7PlcChannelDescriptor.OFFSET_ID, "Error while retrieving Area Offset");
        String s7DataTypeId = (String) channelConfig.get(S7PlcChannelDescriptor.S7_ELEMENT_TYPE_ID);

        if (type == DataType.BYTE_ARRAY) {

            int byteCount = getIntProperty(record, S7PlcChannelDescriptor.BYTE_COUNT_ID,
                    "Error while retrieving Byte Count");
            return new ByteArrayTask(record, offset, offset + byteCount, mode);

        } else if (S7PlcDataType.INT.name().equals(s7DataTypeId)) {

            return new BinaryDataTask<>(record, offset, BinaryDataTypes.INT16_BE, type, mode);

        } else if (S7PlcDataType.DINT.name().equals(s7DataTypeId)) {

            return new BinaryDataTask<>(record, offset, BinaryDataTypes.INT32_BE, type, mode);

        } else if (S7PlcDataType.BOOL.name().equals(s7DataTypeId)) {

            assertChannelType(record, DataType.BOOLEAN);
            int bitIndex = getIntProperty(record, S7PlcChannelDescriptor.BIT_INDEX_ID,
                    "Error while retreiving bit index");
            return new BitTask(record, offset, bitIndex, mode == Mode.WRITE ? Mode.UPDATE : Mode.READ);

        } else if (S7PlcDataType.WORD.name().equals(s7DataTypeId)) {

            return new BinaryDataTask<>(record, offset, BinaryDataTypes.UINT16_BE, type, mode);

        } else if (S7PlcDataType.DWORD.name().equals(s7DataTypeId)) {

            return new BinaryDataTask<>(record, offset, BinaryDataTypes.UINT32_BE, type, mode);

        } else if (S7PlcDataType.BYTE.name().equals(s7DataTypeId)) {

            return new BinaryDataTask<>(record, offset, BinaryDataTypes.UINT8, type, mode);

        } else if (S7PlcDataType.CHAR.name().equals(s7DataTypeId)) {

            assertChannelType(record, DataType.STRING);
            int byteCount = getIntProperty(record, S7PlcChannelDescriptor.BYTE_COUNT_ID,
                    "Error while retrieving Byte Count");
            return new StringTask(record, offset, offset + byteCount, mode);

        } else if (S7PlcDataType.REAL.name().equals(s7DataTypeId)) {

            return new BinaryDataTask<>(record, offset, BinaryDataTypes.FLOAT_BE, type, mode);

        }

        throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, "Unable to determine operation");

    }

    public static Stream<Pair<S7PlcDomain, BlockTask>> build(List<ChannelRecord> records, Mode mode) {
        return records.stream().map(record -> {
            try {
                final int db = S7PlcTaskBuilder.getDbNo(record);
                final S7PlcArea area = S7PlcTaskBuilder.getAreaNo(record);
                return new Pair<>(new S7PlcDomain(db, area), build(record, mode));
            } catch (Exception e) {
                record.setTimestamp(System.currentTimeMillis());
                record.setChannelStatus(new ChannelStatus(ChannelFlag.FAILURE, e.getMessage(), e));
                return null;
            }
        }).filter(Objects::nonNull);
    }

}
