/*******************************************************************************
 * Copyright (c) 2021, 2023 Eurotech and/or its affiliates and others
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
package org.eclipse.kura.internal.rest.network.configuration;

import static java.util.Objects.isNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.configuration.ComponentConfiguration;
import org.eclipse.kura.configuration.ConfigurationService;
import org.eclipse.kura.crypto.CryptoService;
import org.eclipse.kura.request.handler.jaxrs.DefaultExceptionHandler;
import org.eclipse.kura.rest.configuration.api.ComponentConfigurationDTO;
import org.eclipse.kura.rest.configuration.api.ComponentConfigurationList;
import org.eclipse.kura.rest.configuration.api.CreateFactoryComponentConfigurationsRequest;
import org.eclipse.kura.rest.configuration.api.DTOUtil;
import org.eclipse.kura.rest.configuration.api.DeleteFactoryComponentRequest;
import org.eclipse.kura.rest.configuration.api.FactoryComponentConfigurationDTO;
import org.eclipse.kura.rest.configuration.api.FailureHandler;
import org.eclipse.kura.rest.configuration.api.PidSet;
import org.eclipse.kura.rest.configuration.api.UpdateComponentConfigurationRequest;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.UserAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("networkConfiguration/v1")
public class NetworkConfigurationRestService {

    private static final Logger logger = LoggerFactory.getLogger(NetworkConfigurationRestService.class);
    private static final String KURA_PERMISSION_REST_CONFIGURATION_ROLE = "kura.permission.rest.network.configuration";
    private static final List<String> NETWORK_CONFIGURATION_PIDS = Arrays.asList(
            "org.eclipse.kura.net.admin.NetworkConfigurationService",
            "org.eclipse.kura.net.admin.FirewallConfigurationService",
            "org.eclipse.kura.net.admin.ipv6.FirewallConfigurationServiceIPv6");
    private static final String SUBTASK_SNAPSHOT_TAG = "snapshot";
    private ConfigurationService configurationService;
    private CryptoService cryptoService;

    public void setUserAdmin(UserAdmin userAdmin) {
        userAdmin.createRole(KURA_PERMISSION_REST_CONFIGURATION_ROLE, Role.GROUP);
    }

    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    public void setCryptoService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    /**
     * GET method.
     *
     * Lists the tracked network configurable component Pids
     *
     * @return a List of String objects representing the Pids of network factory components
     *         tracked by the
     *         {@link ConfigurationService}
     */
    @GET
    @RolesAllowed("network.configuration")
    @Path("/configurableComponents")
    @Produces(MediaType.APPLICATION_JSON)
    public PidSet listNetworkConfigurableComponentsPids() {
        Set<String> pids = this.configurationService.getConfigurableComponentPids().stream()
                .filter(this::isNetworkConfigurationPid).collect(Collectors.toSet());

        return new PidSet(pids);
    }

    /**
     * GET method.
     *
     * Lists all the network component configurations of all the ConfigurableComponents
     * tracked by the
     * {@link ConfigurationService}
     *
     * @return a list of {@link ComponentConfigurationDTO} that map all the
     *         configuration parameters tracked for the
     *         network configurable components tracked.
     */
    @GET
    @RolesAllowed("network.configuration")
    @Path("/configurableComponents/configurations")
    @Produces(MediaType.APPLICATION_JSON)
    public ComponentConfigurationList listNetworkConfiguration() {
        final List<ComponentConfiguration> ccs = new ArrayList<>();
        try {
            for (String config : NETWORK_CONFIGURATION_PIDS) {
                if (null != this.configurationService.getComponentConfiguration(config)) {
                    ccs.add(this.configurationService.getComponentConfiguration(config));
                }
            }
        } catch (final Exception e) {
            throw DefaultExceptionHandler.toWebApplicationException(e);
        }
        return DTOUtil.toComponentConfigurationList(ccs, this.cryptoService, false).replacePasswordsWithPlaceholder();
    }

    /**
     * POST method.
     *
     * Lists the network component configurations of all the network ConfigurableComponents tracked
     * by the
     * {@link ConfigurationService} that match the filter specified
     *
     * @param filter
     *            A String representing an OSGi filter
     * @return a list of {@link ComponentConfigurationDTO}s for the components that
     *         match the specified filter
     */
    @POST
    @RolesAllowed("network.configuration")
    @Path("/configurableComponents/configurations/byPid")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ComponentConfigurationList listNetworkComponentConfigurations(final PidSet pids) {
        pids.validate();
        final List<ComponentConfiguration> networkConfigurations = new ArrayList<>();
        pids.getPids().forEach(pid -> {
            try {
                ComponentConfiguration config = this.configurationService.getComponentConfiguration(pid);
                if (!isNull(config) && isNetworkConfigurationPid(pid)) {
                    networkConfigurations.add(config);
                }
            } catch (Exception ex) {
                throw DefaultExceptionHandler.toWebApplicationException(ex);
            }
        });
        return DTOUtil.toComponentConfigurationList(networkConfigurations, this.cryptoService, false)
                .replacePasswordsWithPlaceholder();
    }

    /**
     * POST method.
     *
     * This method provides the default network Component Configuration for the component identified by
     * the specified PID in the body request
     *
     * @param componentPid
     * @return The default {@link ComponentConfiguration} or a null object if the
     *         component is not tracked
     */
    @POST
    @RolesAllowed("network.configuration")
    @Path("/configurableComponents/configurations/byPid/_default")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ComponentConfigurationList listDefaultNetworkComponentConfiguration(final PidSet pids) {
        pids.validate();
        final List<ComponentConfigurationDTO> requestResult = new ArrayList<>();
        for (final String pid : pids.getPids()) {
            try {
                final ComponentConfiguration componentConfiguration = this.configurationService
                        .getDefaultComponentConfiguration(pid);
                if (!isNetworkConfigurationPid(pid) || componentConfiguration == null
                        || componentConfiguration.getDefinition() == null) {
                    logger.warn("cannot find default network configuration for {}", pid);
                    continue;
                }
                requestResult
                        .add(DTOUtil.toComponentConfigurationDTO(componentConfiguration, this.cryptoService, false));
            } catch (final Exception ex) {
                logger.warn("failed to get default configuration for {}", pid, ex);
            }
        }
        return new ComponentConfigurationList(requestResult);
    }

    /**
     * POST method.
     *
     * This method let the user update the configuration of multiple network configurable components
     *
     * @param request
     */
    @PUT
    @RolesAllowed("network.configuration")
    @Path("/configurableComponents/configurations/_update")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateNetworkComponentConfigurations(UpdateComponentConfigurationRequest request) {
        request.validate();
        final FailureHandler failureHandler = new FailureHandler();
        for (ComponentConfigurationDTO componentConfig : request.getComponentConfigurations()) {
            if (isNetworkConfigurationPid(componentConfig.getPid())) {
                failureHandler.runFallibleSubtask("update:" + componentConfig.getPid(), () -> {
                    final Map<String, Object> configurationProperties = DTOUtil
                            .dtosToConfigurationProperties(componentConfig.getProperties());
                    this.configurationService.updateConfiguration(componentConfig.getPid(), configurationProperties,
                            false);
                });
            }
        }
        if (request.isTakeSnapshot()) {
            failureHandler.runFallibleSubtask(SUBTASK_SNAPSHOT_TAG, () -> this.configurationService.snapshot());
        }
        failureHandler.checkStatus();
        return Response.ok().build();
    }

    /**
     * GET method.
     *
     * The method lists all the network FactoryComponents Pids tracked by
     * {@link ConfigurationService}
     *
     * @return a list of String representing the tracked FactoryComponents
     */
    @GET
    @RolesAllowed("network.configuration")
    @Path("/factoryComponents")
    @Produces(MediaType.APPLICATION_JSON)
    public PidSet listFactoryComponentsPids() {
        return new PidSet(Collections.emptySet());
    }

    /**
     * POST method.
     *
     * Creates a new network ConfigurableComponent instance by creating a new configuration
     * from a
     * Configuration Admin factory.
     * The {@link FactoryComponentConfigurationDTO} will provide all the information
     * needed to generate the instance: it
     * links the factory Pid to be used, the target instance Pid, the properties to
     * be used when creating the instance
     * and if the request should be persisted with a snapshot.
     * In case of a request error, an exception is thrown.
     *
     * @param factoryComponentConfiguration
     *            provides all the parameters needed to
     *            generate a new instance from a Factory
     *            Component
     *
     */
    @POST
    @RolesAllowed("network.configuration")
    @Path("/factoryComponents")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createNetworkFactoryComponents(CreateFactoryComponentConfigurationsRequest configs) {
        configs.validate();

        final FailureHandler handler = new FailureHandler();

        for (final FactoryComponentConfigurationDTO config : configs.getConfigs()) {
            handler.runFallibleSubtask("create:" + config.getPid(), () -> {

                throw new KuraException(KuraErrorCode.INVALID_PARAMETER,
                        "Factory pid doesn't correspond to a network component factory");

            });
        }

        if (configs.isTakeSnapshot()) {
            handler.runFallibleSubtask(SUBTASK_SNAPSHOT_TAG, () -> this.configurationService.snapshot());
        }

        handler.checkStatus();
        return Response.ok().build();
    }

    /**
     * DELETE method.
     *
     * For the specified Pid and {@link FactoryComponentDeleteRequest}, the
     * {@link ConfigurationService} instance
     * deletes the corresponding network ConfigurableComponent instance.
     *
     * @param pid
     *            A String representing the pid of the
     *            instance generated by a Factory
     *            Component that needs to be
     *            deleted
     * @param factoryComponentDeleteRequest
     *            A {@link FactoryComponentDeleteRequest}
     *            containing additional information to
     *            ease the process of
     *            instance delete
     */
    @DELETE
    @RolesAllowed("network.configuration")
    @Path("/factoryComponents/byPid")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteFactoryConfigurations(final DeleteFactoryComponentRequest request) {
        request.validate();

        final FailureHandler handler = new FailureHandler();

        for (final String pid : request.getPids()) {
            handler.runFallibleSubtask("delete:" + pid, () -> {
                throw new KuraException(KuraErrorCode.INVALID_PARAMETER,
                        "Pid doesn't correspond to a network factory component");
            });
        }

        if (request.isTakeSnapshot()) {
            handler.runFallibleSubtask(SUBTASK_SNAPSHOT_TAG, () -> this.configurationService.snapshot());
        }

        handler.checkStatus();
        return Response.ok().build();
    }

    @GET
    @RolesAllowed("network.configuration")
    @Path("/factoryComponents/ocd")
    @Produces(MediaType.APPLICATION_JSON)
    public ComponentConfigurationList getFactoryComponentOcds() {

        return DTOUtil.toComponentConfigurationList(Collections.emptyList(), this.cryptoService, false);
    }

    @POST
    @RolesAllowed("network.configuration")
    @Path("/factoryComponents/ocd/byFactoryPid")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ComponentConfigurationList getFactoryComponentOcdsByPid(final PidSet factoryPids) {
        factoryPids.validate();

        return DTOUtil.toComponentConfigurationList(Collections.emptyList(), this.cryptoService, false);
    }

    /*
     * Utils
     */

    private boolean isNetworkConfigurationPid(String pid) {
        boolean result = false;
        for (String filter : NETWORK_CONFIGURATION_PIDS) {
            if (pid.equals(filter)) {
                result = true;
            }
        }
        return result;
    }
}
