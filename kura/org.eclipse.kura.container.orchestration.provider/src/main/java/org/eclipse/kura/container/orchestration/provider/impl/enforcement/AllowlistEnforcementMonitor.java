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
 *******************************************************************************/

package org.eclipse.kura.container.orchestration.provider.impl.enforcement;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.container.orchestration.ContainerInstanceDescriptor;
import org.eclipse.kura.container.orchestration.ContainerState;
import org.eclipse.kura.container.orchestration.provider.impl.ContainerOrchestrationServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.model.Event;

public class AllowlistEnforcementMonitor extends ResultCallbackTemplate<AllowlistEnforcementMonitor, Event> {

    private static final Logger logger = LoggerFactory.getLogger(AllowlistEnforcementMonitor.class);
    private static final String ENFORCEMENT_SUCCESS = "Enforcement allowlist contains image digests {}...container {} is starting";
    private static final String ENFORCEMENT_FAILURE = "Enforcement allowlist doesn't contain image digests...container {} will be stopped";
    private final List<String> enforcementAllowlistContent;
    private final ContainerOrchestrationServiceImpl orchestrationServiceImpl;

    public AllowlistEnforcementMonitor(String allowlistContent,
            ContainerOrchestrationServiceImpl containerOrchestrationService) {

        this.enforcementAllowlistContent = Arrays.asList(allowlistContent.replace(" ", "").split("\\r?\\n|\\r"))
                .stream().filter(line -> !line.isEmpty()).collect(Collectors.toList());
        this.orchestrationServiceImpl = containerOrchestrationService;
    }

    @Override
    public void onNext(Event item) {
        enforceAllowlistFor(item.getId());
    }

    private void enforceAllowlistFor(String containerId) {

        List<String> digestsList = this.orchestrationServiceImpl
                .getImageDigestsByContainerName(getContainerNameById(containerId));

        List<String> digestIntersection = this.enforcementAllowlistContent.stream().distinct()
                .filter(digestsList::contains).collect(Collectors.toList());

        if (!digestIntersection.isEmpty()) {
            logger.info(ENFORCEMENT_SUCCESS, digestIntersection, containerId);
        } else {
            logger.error(ENFORCEMENT_FAILURE, containerId);
            stopContainer(containerId);
            deleteContainer(containerId);
        }
    }

    public void enforceAllowlistFor(List<ContainerInstanceDescriptor> containerDescriptors) {

        for (ContainerInstanceDescriptor descriptor : containerDescriptors) {
            enforceAllowlistFor(descriptor.getContainerId());
        }
    }

    private String getContainerNameById(String containerId) {
        return this.orchestrationServiceImpl.listContainerDescriptors().stream()
                .filter(container -> container.getContainerId().equals(containerId)).findFirst()
                .map(container -> container.getContainerName()).orElse(null);
    }

    private void stopContainer(String containerId) {

        this.orchestrationServiceImpl.listContainerDescriptors().stream()
                .filter(descriptor -> descriptor.getContainerId().equals(containerId)).findFirst()
                .ifPresent(descriptor -> {
                    if (descriptor.getContainerState().equals(ContainerState.ACTIVE)
                            || descriptor.getContainerState().equals(ContainerState.STARTING)) {
                        try {
                            this.orchestrationServiceImpl.stopContainer(descriptor.getContainerId());
                        } catch (KuraException ex) {
                            logger.error("Error during container stopping process of {}:", descriptor.getContainerId(),
                                    ex);
                        }
                    }

                });
    }

    private void deleteContainer(String containerId) {
        try {
            this.orchestrationServiceImpl.deleteContainer(containerId);
        } catch (KuraException ex) {
            logger.error("Error during container deleting process of {}:", containerId, ex);
        }
    }
}
