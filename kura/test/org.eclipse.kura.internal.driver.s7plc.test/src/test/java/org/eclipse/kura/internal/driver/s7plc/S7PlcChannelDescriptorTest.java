/*******************************************************************************
 * Copyright (c) 2017, 2020 Eurotech and/or its affiliates and others
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.eclipse.kura.internal.driver.s7plc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.eclipse.kura.core.configuration.metatype.Tad;
import org.junit.Test;


public class S7PlcChannelDescriptorTest {

    @Test
    public void testGetDescriptor() {
        S7PlcChannelDescriptor descriptor = new S7PlcChannelDescriptor();

        List<Tad> result = (List<Tad>) descriptor.getDescriptor();

        assertNotNull(result);
        assertEquals(6, result.size());

        assertEquals("s7.data.type", result.get(0).getName());
        assertEquals(S7PlcDataType.values().length, result.get(0).getOption().size());

        assertEquals("s7.area.type", result.get(1).getName());
        assertEquals(S7PlcArea.values().length, result.get(1).getOption().size());

        assertEquals("data.block.no", result.get(2).getName());
        assertEquals("offset", result.get(3).getName());
        assertEquals("byte.count", result.get(4).getName());
        assertEquals("bit.index", result.get(5).getName());
    }

}
