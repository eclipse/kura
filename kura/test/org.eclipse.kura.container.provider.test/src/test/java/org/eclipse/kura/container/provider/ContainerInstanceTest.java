/*******************************************************************************
  * Copyright (c) 2022 Eurotech and/or its affiliates and others
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

package org.eclipse.kura.container.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.container.orchestration.ContainerConfiguration;
import org.eclipse.kura.container.orchestration.ContainerInstanceDescriptor;
import org.eclipse.kura.container.orchestration.ContainerOrchestrationService;
import org.eclipse.kura.container.provider.ContainerInstance.ContainerInstanceState;
import org.junit.After;
import org.junit.Test;

public class ContainerInstanceTest {

    private static final String CONTAINER_PATH_FILE_PATH = "container.path.filePath";
    private static final String CONTAINER_PATH_DESTINATION = "container.path.destination";
    private static final String CONTAINER_ENV1 = "container.env1";
    private static final String CONTAINER_ARGS = "container.args";
    private static final String CONTAINER_PORTS_INTERNAL = "container.ports.internal";
    private static final String CONTAINER_PORTS_EXTERNAL = "container.ports.external";
    private static final String CONTAINER_NAME = "container.name";
    private static final String CONTAINER_IMAGE_TAG = "container.image.tag";
    private static final String CONTAINER_IMAGE = "container.image";
    private static final String CONTAINER_ENABLED = "container.enabled";
    private static final String CONTAINER_DEVICE = "container.Device";
    private static final String CONTAINER_LOGGER_PARAMETERS = "container.loggerParameters";
    private static final String CONTAINER_LOGGING_TYPE = "container.loggingType";
    private static final String CONTAINER_NETWORKING_MODE = "container.networkMode";

    private ContainerOrchestrationService mockContainerOrchestrationService = mock(ContainerOrchestrationService.class);
    private Map<String, Object> properties = new HashMap<>();
    private Map<String, Object> newProperties = new HashMap<>();
    private ContainerInstance containerInstance = new ContainerInstance();
    private Exception occurredException;

    public void testServiceActivateNullProperties() {
        givenContainerInstanceWith(this.mockContainerOrchestrationService);

        whenActivateInstanceIsCalledWith(null);

        thenExceptionOccurred(IllegalArgumentException.class);
    }

    @Test
    public void testServiceActivateWithPropertiesDisabled() throws KuraException, InterruptedException {
        givenPropertiesWith(CONTAINER_ENABLED, false);
        givenContainerOrchestratorHasNoRunningContainers();
        givenContainerInstanceWith(this.mockContainerOrchestrationService);

        whenActivateInstanceIsCalledWith(this.properties);

        thenNoExceptionOccurred();
        thenWaitForContainerInstanceToBecome(ContainerInstanceState.DISABLED);
        thenStartContainerWasCalled(false);
    }

    @Test
    public void testServiceActivateWithPropertiesEnabled() throws KuraException, InterruptedException {
        givenPropertiesWith(CONTAINER_ENABLED, true);
        givenContainerOrchestratorHasNoRunningContainers();
        givenContainerInstanceWith(this.mockContainerOrchestrationService);

        whenActivateInstanceIsCalledWith(this.properties);

        thenNoExceptionOccurred();
        thenWaitForContainerInstanceToBecome(ContainerInstanceState.CREATED);
        thenStopContainerWasCalled(false);
        thenStartContainerWasCalled(true);
    }

    @Test
    public void testServiceUpdateSameProperties() throws KuraException {
        givenPropertiesWith(CONTAINER_ENABLED, false);
        givenContainerOrchestratorHasNoRunningContainers();
        givenContainerInstanceWith(this.mockContainerOrchestrationService);
        givenContainerInstanceActivatedWith(this.properties);

        whenUpdateInstanceIsCalledWith(this.properties);

        thenNoExceptionOccurred();
        thenWaitForContainerInstanceToBecome(ContainerInstanceState.DISABLED);
        thenStopContainerWasCalled(false);
    }

    @Test
    public void testServiceUpdateEnable() throws KuraException, InterruptedException {
        givenPropertiesWith(CONTAINER_ENABLED, false);
        givenContainerOrchestratorHasNoRunningContainers();
        givenContainerInstanceWith(this.mockContainerOrchestrationService);
        givenContainerInstanceActivatedWith(this.properties);

        givenNewPropertiesWith(CONTAINER_ENABLED, true);

        whenUpdateInstanceIsCalledWith(this.newProperties);

        thenWaitForContainerInstanceToBecome(ContainerInstanceState.CREATED);
        thenNoExceptionOccurred();
        thenStartContainerWasCalled(true);
    }

    @Test
    public void testServiceUpdateDisable() throws KuraException {
        givenPropertiesWith(CONTAINER_ENABLED, true);
        givenPropertiesWith(CONTAINER_NAME, "pippo");
        givenContainerOrchestratorHasNoRunningContainers();
        givenContainerInstanceWith(this.mockContainerOrchestrationService);
        givenContainerInstanceActivatedWith(this.properties);
        givenContainerStateIs(ContainerInstanceState.CREATED);

        givenContainerOrchestratorIsRunningContainer("pippo", "1234");
        givenNewPropertiesWith(CONTAINER_ENABLED, false);

        whenUpdateInstanceIsCalledWith(this.newProperties);

        thenWaitForContainerInstanceToBecome(ContainerInstanceState.DISABLED);
        thenNoExceptionOccurred();
        thenStopContainerWasCalled(true);
        thenDeleteContainerWasCalled(true);
    }

    @Test
    public void testServiceDeactivateNoRunningContainers() throws KuraException, InterruptedException {
        givenPropertiesWith(CONTAINER_ENABLED, false);
        givenContainerOrchestratorHasNoRunningContainers();
        givenContainerInstanceWith(this.mockContainerOrchestrationService);
        givenContainerInstanceActivatedWith(this.properties);

        whenDeactivateInstance();

        thenNoExceptionOccurred();
        thenStartContainerWasCalled(false);
    }

    @Test
    public void testServiceDeactivateStopContainer() throws KuraException {
        givenPropertiesWith(CONTAINER_ENABLED, true);
        givenContainerOrchestratorHasNoRunningContainers();
        givenContainerInstanceWith(this.mockContainerOrchestrationService);
        givenContainerInstanceActivatedWith(this.properties);
        givenContainerStateIs(ContainerInstanceState.CREATED);

        whenDeactivateInstance();

        thenNoExceptionOccurred();
        thenWaitForContainerInstanceToBecome(ContainerInstanceState.DISABLED);
        thenStopContainerWasCalled(true);
        thenDeleteContainerWasCalled(true);
    }

    @After
    public void tearDown() {
        this.containerInstance.deactivate();
    }

    /*
     * GIVEN
     */

    private void givenContainerInstanceWith(ContainerOrchestrationService cos) {
        this.containerInstance.setContainerOrchestrationService(cos);
    }

    private void givenPropertiesWith(String key, Object value) {
        this.properties.put(key, value);
    }

    private void givenNewPropertiesWith(String key, Object value) {
        this.newProperties.put(key, value);
    }

    private void givenContainerInstanceActivatedWith(Map<String, Object> configuration) {
        try {
            this.containerInstance.activate(configuration);
        } catch (Exception e) {
            fail("Failed to activate container instance. Caused by: " + e.getMessage());
        }
    }

    private void givenContainerStateIs(ContainerInstanceState expectedState) {
        int count = 10;
        while (this.containerInstance.getState() != expectedState && count-- > 0) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (count <= 0) {
            fail(String.format("Container instance did not reach expected state \"%s\"", expectedState));
        }
    }

    private void givenContainerOrchestratorHasNoRunningContainers() {
        when(this.mockContainerOrchestrationService.listContainerDescriptors()).thenReturn(Collections.emptyList());
    }

    private void givenContainerOrchestratorIsRunningContainer(String containerName, String containerId) {
        List<ContainerInstanceDescriptor> runningContainers = Collections.singletonList(ContainerInstanceDescriptor
                .builder().setContainerName(containerName).setContainerID(containerId).build());

        when(this.mockContainerOrchestrationService.listContainerDescriptors()).thenReturn(runningContainers);
    }

    /*
     * WHEN
     */

    private void whenActivateInstanceIsCalledWith(Map<String, Object> configuration) {
        try {
            this.containerInstance.activate(configuration);
        } catch (Exception e) {
            this.occurredException = e;
        }
    }

    private void whenUpdateInstanceIsCalledWith(Map<String, Object> configuration) {
        try {
            this.containerInstance.updated(configuration);
        } catch (Exception e) {
            this.occurredException = e;
        }
    }

    private void whenDeactivateInstance() {
        try {
            this.containerInstance.deactivate();
        } catch (Exception e) {
            this.occurredException = e;
        }
    }

    /*
     * THEN
     */

    private void thenStopContainerWasCalled(boolean expectCalled) throws KuraException {
        int timesCalled = expectCalled ? 1 : 0;
        verify(this.mockContainerOrchestrationService, times(timesCalled)).stopContainer(any());
    }

    private void thenWaitForContainerInstanceToBecome(ContainerInstanceState expectedState) {
        int count = 10;
        while (this.containerInstance.getState() != expectedState && count-- > 0) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (count <= 0) {
            fail(String.format("Container instance did not reach expected state \"%s\"", expectedState));
        }
    }

    private void thenStartContainerWasCalled(boolean expectCalled) throws KuraException, InterruptedException {
        int times = expectCalled ? 1 : 0;
        verify(this.mockContainerOrchestrationService, times(times)).startContainer(any(ContainerConfiguration.class));
    }

    private void thenDeleteContainerWasCalled(boolean expectCalled) throws KuraException {
        int times = expectCalled ? 1 : 0;
        verify(this.mockContainerOrchestrationService, times(times)).deleteContainer(any());
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

    private <E extends Exception> void thenExceptionOccurred(Class<E> expectedException) {
        assertNotNull(this.occurredException);
        assertEquals(expectedException.getName(), this.occurredException.getClass().getName());
    }

}
