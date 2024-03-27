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

import org.eclipse.kura.container.orchestration.ImageInstanceDescriptor;
import org.junit.Test;

public class ImageInstanceDescriptorTest {

    private static final String IMAGE_NAME = "nginx";
    private static final String IMAGE_TAG = "latest";
    private static final String IMAGE_ARCH = "ARM64";
    private static final String IMAGE_AUTHOR = "Nginx";
    private static final String IMAGE_ID = "f45f645f457uyrthjfghje4r6t";
    private static final long IMAGE_SIZE = 123123;

    private ImageInstanceDescriptor firstImageConfig;
    private ImageInstanceDescriptor seccondImageConfig;

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

        this.firstImageConfig = ImageInstanceDescriptor.builder().setImageName(IMAGE_NAME).setImageTag(IMAGE_TAG)
                .setImageArch(IMAGE_ARCH).setImageAuthor(IMAGE_AUTHOR).setimageSize(IMAGE_SIZE).setImageId(IMAGE_ID)
                .build();
    }

    private void givenContainerTwoDiffrent() {

        this.seccondImageConfig = ImageInstanceDescriptor.builder().setImageName("NOT_" + IMAGE_NAME)
                .setImageTag("NOT_" + IMAGE_TAG).setImageArch("NOT_" + IMAGE_ARCH).setImageAuthor(IMAGE_AUTHOR)
                .setimageSize(IMAGE_SIZE).setImageId("3rhf8943hf78934hf734t7r8fw38fy234897fh8").build();
    }

    // then
    private void thenCompareContainerOneToExpectedOutput() {
        assertEquals(IMAGE_NAME, this.firstImageConfig.getImageName());
        assertEquals(IMAGE_TAG, this.firstImageConfig.getImageTag());
        assertEquals(IMAGE_ARCH, this.firstImageConfig.getImageArch());
        assertEquals(IMAGE_AUTHOR, this.firstImageConfig.getImageAuthor());
        assertEquals(IMAGE_ID, this.firstImageConfig.getImageId());
        assertEquals(IMAGE_SIZE, this.firstImageConfig.getImageSize());

    }

    private void thenFirstContainerDoesntEqualSeccond() {
        assertFalse(firstImageConfig.equals(this.seccondImageConfig));
    }

}
