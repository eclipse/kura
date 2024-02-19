/*******************************************************************************
 * Copyright (c) 2024 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Eurotech
 ******************************************************************************/
package org.eclipse.kura.example.container.signature.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.configuration.Password;
import org.eclipse.kura.container.orchestration.ImageInstanceDescriptor;
import org.junit.Test;

public class DummyContainerSignatureValidationServiceTest {

    private DummyContainerSignatureValidationService containerSignatureValidationService = new DummyContainerSignatureValidationService();
    private Map<String, Object> properties = new HashMap<>();

    private static final String PROPERTY_NAME = "manual.setValidationOutcome";
    private static final String IMAGE_ID = "imageId";
    private static final String TRUST_ANCHOR = "trustAnchor";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    private boolean validationResult = false;
    private Exception occurredException;
    private ImageInstanceDescriptor imageDescriptor;
    private String rawValidationOutcomeStringSetting = "";

    @Test
    public void updatedWorksWithEmptyConfiguration() {
        whenUpdatedIsCalledWith(this.properties);

        thenNoExceptionOccurred();
        thenValidationResultsSizeIs(0);
    }

    @Test
    public void updatedWorksWithEmptyStringConfiguration() {
        givenPropertyWith(PROPERTY_NAME, this.rawValidationOutcomeStringSetting);

        whenUpdatedIsCalledWith(this.properties);

        thenNoExceptionOccurred();
        thenValidationResultsSizeIs(0);
    }

    @Test
    public void updatedWorksWithSingleStringConfiguration() {
        givenRawValidationStringWith("alpine:latest@sha256:1234567890");
        givenPropertyWith(PROPERTY_NAME, this.rawValidationOutcomeStringSetting);

        whenUpdatedIsCalledWith(this.properties);

        thenNoExceptionOccurred();
        thenValidationResultsSizeIs(1);
        thenValidationResultsContains("alpine", "latest", "sha256:1234567890");
    }

    @Test
    public void updatedWorksWithMultipleStringConfiguration() {
        givenRawValidationStringWith("alpine:latest@sha256:1234567890");
        givenRawValidationStringWith("alpine:develop@sha256:1234567891");
        givenRawValidationStringWith("ubuntu:latest@sha512:12345678911234567891");
        givenPropertyWith(PROPERTY_NAME, this.rawValidationOutcomeStringSetting);

        whenUpdatedIsCalledWith(this.properties);

        thenNoExceptionOccurred();
        thenValidationResultsSizeIs(3);
        thenValidationResultsContains("alpine", "latest", "sha256:1234567890");
        thenValidationResultsContains("alpine", "develop", "sha256:1234567891");
        thenValidationResultsContains("ubuntu", "latest", "sha512:12345678911234567891");
    }

    @Test
    public void updatedThrowsWithWrongFormatString() {
        givenRawValidationStringWith("alpine:latest:sha256:1234567890");
        givenPropertyWith(PROPERTY_NAME, this.rawValidationOutcomeStringSetting);

        whenUpdatedIsCalledWith(this.properties);
        thenExceptionOccurred(IllegalArgumentException.class);
    }

    private <E extends Exception> void thenExceptionOccurred(Class<E> expectedException) {
        assertNotNull(this.occurredException);
        assertEquals(expectedException.getName(), this.occurredException.getClass().getName());
    }

    private void thenValidationResultsContains(String imageName, String imageTag, String expectedDigest) {
        String digest = this.containerSignatureValidationService.getValidationResultsFor(imageName, imageTag);
        assertTrue(Objects.nonNull(digest));
        assertEquals(expectedDigest, digest);
    }

    private void givenRawValidationStringWith(String entry) {
        if (this.rawValidationOutcomeStringSetting.isEmpty()) {
            this.rawValidationOutcomeStringSetting = entry;
        } else {
            this.rawValidationOutcomeStringSetting = String.format("%s\n%s", this.rawValidationOutcomeStringSetting,
                    entry);
        }
    }

    private void whenUpdatedIsCalledWith(Map<String, Object> props) {
        try {
            this.containerSignatureValidationService.updated(props);
        } catch (Exception e) {
            this.occurredException = e;
        }
    }

    private void thenValidationResultsSizeIs(int expectedSize) {
        assertEquals(expectedSize, this.containerSignatureValidationService.getValidationResultsSize());
    }

    @Test
    public void verifyReturnsFailureWithEmptyConfiguration() {
        givenContainerSignatureValidationServiceWith(this.properties);

        whenVerifyIsCalledWith("alpine", "latest", TRUST_ANCHOR, false);

        thenNoExceptionOccurred();
        thenVerificationResultIs(false);
    }

    @Test
    public void verifyReturnsFailureWithSingleEntryInConfiguration() {
        givenPropertyWith(PROPERTY_NAME, "alpine:latest@sha256:1234567890");
        givenContainerSignatureValidationServiceWith(this.properties);

        whenVerifyIsCalledWith("alpine", "develop", TRUST_ANCHOR, false);

        thenNoExceptionOccurred();
        thenVerificationResultIs(false);
    }

    @Test
    public void verifyReturnsSuccessWithSingleEntryInConfiguration() {
        givenPropertyWith(PROPERTY_NAME, "alpine:latest@sha256:1234567890");
        givenContainerSignatureValidationServiceWith(this.properties);

        whenVerifyIsCalledWith("alpine", "latest", TRUST_ANCHOR, false);

        thenNoExceptionOccurred();
        thenVerificationResultIs(true);
    }

    @Test
    public void verifyWithAuthReturnsFailureWithSingleEntryInConfiguration() {
        givenPropertyWith(PROPERTY_NAME, "alpine:latest@sha256:1234567890");
        givenContainerSignatureValidationServiceWith(this.properties);

        whenVerifyWithAuthIsCalledWith("alpine", "develop", TRUST_ANCHOR, false, USERNAME, PASSWORD);

        thenNoExceptionOccurred();
        thenVerificationResultIs(false);
    }

    @Test
    public void verifyWithAuthReturnsSuccessWithSingleEntryInConfiguration() {
        givenPropertyWith(PROPERTY_NAME, "alpine:latest@sha256:1234567890");
        givenContainerSignatureValidationServiceWith(this.properties);

        whenVerifyWithAuthIsCalledWith("alpine", "latest", TRUST_ANCHOR, false, USERNAME, PASSWORD);

        thenNoExceptionOccurred();
        thenVerificationResultIs(true);
    }

    @Test
    public void verifyWithImageReturnsFailureWithSingleEntryInConfiguration() {
        givenPropertyWith(PROPERTY_NAME, "alpine:latest@sha256:1234567890");
        givenImageInstanceDescriptorWith("alpine", "develop", IMAGE_ID);
        givenContainerSignatureValidationServiceWith(this.properties);

        whenVerifyImageInstanceDescriptorIsCalledWith(this.imageDescriptor, TRUST_ANCHOR, false);

        thenNoExceptionOccurred();
        thenVerificationResultIs(false);
    }

    @Test
    public void verifyWithImageReturnsSuccessWithSingleEntryInConfiguration() {
        givenPropertyWith(PROPERTY_NAME, "alpine:latest@sha256:1234567890");
        givenImageInstanceDescriptorWith("alpine", "latest", IMAGE_ID);
        givenContainerSignatureValidationServiceWith(this.properties);

        whenVerifyImageInstanceDescriptorIsCalledWith(this.imageDescriptor, TRUST_ANCHOR, false);

        thenNoExceptionOccurred();
        thenVerificationResultIs(true);
    }

    @Test
    public void verifyWithImageWithAuthReturnsFailureWithSingleEntryInConfiguration() {
        givenPropertyWith(PROPERTY_NAME, "alpine:latest@sha256:1234567890");
        givenImageInstanceDescriptorWith("alpine", "develop", IMAGE_ID);
        givenContainerSignatureValidationServiceWith(this.properties);

        whenVerifyImageInstanceDescriptorWithAuthIsCalledWith(this.imageDescriptor, TRUST_ANCHOR, false, USERNAME,
                PASSWORD);

        thenNoExceptionOccurred();
        thenVerificationResultIs(false);
    }

    @Test
    public void verifyWithImageWithAuthReturnsSuccessWithSingleEntryInConfiguration() {
        givenPropertyWith(PROPERTY_NAME, "alpine:latest@sha256:1234567890");
        givenImageInstanceDescriptorWith("alpine", "latest", IMAGE_ID);
        givenContainerSignatureValidationServiceWith(this.properties);

        whenVerifyImageInstanceDescriptorWithAuthIsCalledWith(this.imageDescriptor, TRUST_ANCHOR, false, USERNAME,
                PASSWORD);

        thenNoExceptionOccurred();
        thenVerificationResultIs(true);
    }

    /*
     * GIVEN
     */
    private void givenContainerSignatureValidationServiceWith(Map<String, Object> configuration) {
        this.containerSignatureValidationService.activate(configuration);
    }

    private void givenPropertyWith(String propertyName, Object value) {
        this.properties.put(propertyName, value);
    }

    private void givenImageInstanceDescriptorWith(String imageName, String imageTag, String imageId) {
        this.imageDescriptor = ImageInstanceDescriptor.builder().setImageName(imageName).setImageTag(imageTag)
                .setImageId(imageId).setImageLabels(new HashMap<>()).build();
    }

    /*
     * WHEN
     */
    private void whenVerifyIsCalledWith(String imageName, String imageTag, String trustAnchor, boolean isVerify) {
        try {
            this.validationResult = this.containerSignatureValidationService.verify(imageName, imageTag, trustAnchor,
                    isVerify);
        } catch (KuraException e) {
            this.occurredException = e;
        }
    }

    private void whenVerifyWithAuthIsCalledWith(String imageName, String imageTag, String trustAnchor, boolean isVerify,
            String user, String pass) {
        try {
            this.validationResult = this.containerSignatureValidationService.verify(imageName, imageTag, trustAnchor,
                    isVerify, user, new Password(pass));
        } catch (KuraException e) {
            this.occurredException = e;
        }
    }

    private void whenVerifyImageInstanceDescriptorIsCalledWith(ImageInstanceDescriptor descriptor, String trustAnchor,
            boolean isVerify) {
        try {
            this.validationResult = this.containerSignatureValidationService.verify(descriptor, trustAnchor, isVerify);
        } catch (KuraException e) {
            this.occurredException = e;
        }
    }

    private void whenVerifyImageInstanceDescriptorWithAuthIsCalledWith(ImageInstanceDescriptor descriptor,
            String trustAnchor, boolean isVerify, String user, String pass) {
        try {
            this.validationResult = this.containerSignatureValidationService.verify(descriptor, trustAnchor, isVerify,
                    user, new Password(pass));
        } catch (KuraException e) {
            this.occurredException = e;
        }
    }

    /*
     * THEN
     */
    private void thenVerificationResultIs(boolean expectedResult) {
        assertEquals(expectedResult, this.validationResult);
    }

    private void thenNoExceptionOccurred() {
        String errorMessage = "Empty message";
        if (Objects.nonNull(this.occurredException)) {
            StringWriter sw = new StringWriter();
            this.occurredException.printStackTrace(new PrintWriter(sw));

            errorMessage = String.format("No exception expected, \"%s\" found. Caused by: %s",
                    this.occurredException.getClass().getName(), sw.toString());
        }

        assertNull(errorMessage, this.occurredException);
    }
}
