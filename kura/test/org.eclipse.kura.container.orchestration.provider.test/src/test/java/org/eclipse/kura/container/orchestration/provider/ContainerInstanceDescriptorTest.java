/*******************************************************************************
 * Copyright (c) 2022, 2024 Eurotech and/or its affiliates and others
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

package org.eclipse.kura.container.orchestration.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;
import org.eclipse.kura.container.orchestration.ContainerInstanceDescriptor;
import org.junit.Test;

public class ContainerInstanceDescriptorTest {

    private static final String H2_DB_NAME = "Kura_H2DB";
    private static final String H2_DB_IMAGE = "joedoe/h2db";
    private static final List<Integer> H2_DB_PORTS_EXTERNAL = new ArrayList<>(Arrays.asList(1521, 81));
    private static final List<Integer> H2_DB_PORTS_INTERNAL = new ArrayList<>(Arrays.asList(1521, 81));
    private ContainerInstanceDescriptor firstContainerConfig;
    private ContainerInstanceDescriptor seccondContainerConfig;

    @Test
    public void testSupportOfBasicParameters() {
        givenContainerOne();

        thenCompareContainerOneToExpectedOutput();
    }

    @Test
    public void testContainerDoesntEquals() {
        givenContainerOne();
        givenContainerTwoDiffrent();

        thenFirstContainerDoesntEqualSeccond();
    }

    /**
     * End Of Tests
     */

    // given
    private void givenContainerOne() {

        this.firstContainerConfig = ContainerInstanceDescriptor.builder().setContainerImageTag(H2_DB_NAME)
                .setContainerName(H2_DB_NAME).setContainerImage(H2_DB_IMAGE).setExternalPorts(H2_DB_PORTS_EXTERNAL)
                .setInternalPorts(H2_DB_PORTS_INTERNAL).build();
    }

    private void givenContainerTwoDiffrent() {

        this.seccondContainerConfig = ContainerInstanceDescriptor.builder().setContainerImageTag(H2_DB_NAME)
                .setContainerName(H2_DB_NAME).setContainerImage(H2_DB_IMAGE).setContainerImageTag("diffrent")
                .setExternalPorts(H2_DB_PORTS_EXTERNAL).setInternalPorts(H2_DB_PORTS_INTERNAL).build();
    }

    // then
    private void thenCompareContainerOneToExpectedOutput() {
        assertEquals(H2_DB_NAME, this.firstContainerConfig.getContainerName());
        assertEquals(H2_DB_IMAGE, this.firstContainerConfig.getContainerImage());
        assertTrue(ArrayUtils.isEquals(H2_DB_PORTS_EXTERNAL, this.firstContainerConfig.getContainerPortsExternal()));
        assertTrue(ArrayUtils.isEquals(H2_DB_PORTS_INTERNAL, this.firstContainerConfig.getContainerPortsInternal()));
    }

    private void thenFirstContainerDoesntEqualSeccond() {
        assertFalse(firstContainerConfig.equals(this.seccondContainerConfig));
    }

}
