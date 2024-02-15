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

import java.util.Map;
import java.util.Objects;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.configuration.Password;
import org.eclipse.kura.container.orchestration.ImageInstanceDescriptor;
import org.eclipse.kura.container.signature.ContainerSignatureValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DummyContainerSignatureValidationService
        implements ContainerSignatureValidationService, ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(DummyContainerSignatureValidationService.class);
    private static final String SERVICE_NAME = "DummyContainerSignatureValidationService";
    private static final String PROPERTY_NAME = "manual.setValidationOutcome";
    private boolean validationResult = false;

    protected void activate(Map<String, Object> properties) {
        logger.info("Activate {}...", SERVICE_NAME);
        updated(properties);
    }

    public void updated(Map<String, Object> properties) {
        logger.info("Update {}...", SERVICE_NAME);

        if (Objects.nonNull(properties) && !properties.isEmpty()) {
            Object property = properties.get(PROPERTY_NAME);
            this.validationResult = Objects.nonNull(property) && (boolean) property;
        }

        logger.info("Setting signature validation outcome to: \"{}\"", outcome(this.validationResult));
    }

    protected void deactivate() {
        logger.info("Deactivate {}...", SERVICE_NAME);
    }

    @Override
    public boolean verify(String imageName, String imageReference, String trustAnchor, boolean verifyInTransparencyLog)
            throws KuraException {
        logger.info("Validating signature for {}:{} - {}", imageName, imageReference, outcome(this.validationResult));
        return this.validationResult;
    }

    @Override
    public boolean verify(String imageName, String imageReference, String trustAnchor, boolean verifyInTransparencyLog,
            String registryUsername, Password registryPassword) throws KuraException {
        logger.info("Validating signature for {}:{} using authenticated registry - {}", imageName, imageReference,
                outcome(this.validationResult));
        return this.validationResult;
    }

    @Override
    public boolean verify(ImageInstanceDescriptor imageDescriptor, String trustAnchor, boolean verifyInTransparencyLog)
            throws KuraException {
        logger.info("Validating signature for {}:{} - {}", imageDescriptor.getImageName(), imageDescriptor.getImageId(),
                outcome(this.validationResult));
        return this.validationResult;
    }

    @Override
    public boolean verify(ImageInstanceDescriptor imageDescriptor, String trustAnchor, boolean verifyInTransparencyLog,
            String registryUsername, Password registryPassword) throws KuraException {
        logger.info("Validating signature for {}:{} using authenticated registry - {}", imageDescriptor.getImageName(),
                imageDescriptor.getImageId(), outcome(this.validationResult));
        return this.validationResult;
    }

    private String outcome(boolean verifyResult) {
        return verifyResult ? "success" : "failure";
    }

}