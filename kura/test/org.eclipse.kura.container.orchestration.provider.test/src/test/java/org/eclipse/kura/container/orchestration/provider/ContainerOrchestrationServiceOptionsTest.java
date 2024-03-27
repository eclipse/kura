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
import static org.junit.Assert.assertNotEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.kura.container.orchestration.provider.impl.ContainerOrchestrationServiceOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ContainerOrchestrationServiceOptionsTest {

    private static final String DOCKER_HOST_URL = "container.engine.host";
    private static final String IS_ENABLED = "enabled";
    private static final String ALLOWLIST_ENABLED = "allowlist.enabled";
    private static final String ALLOWLIST_CONTENT = "allowlist.content";

    private static final String DEFAULT_DOCKER_HOST_URL = "unix:///var/run/docker.sock";
    private static final boolean DEFAULT_IS_ENABLED = false;
    private static final String DEFAULT_ALLOWLIST_CONTENT = "";
    private static final boolean DEFAULT_ALLOWLIST_ENABLED = false;

    private static final String REPOSITORY_ENABLED = "repository.enabled";
    private static final String REPOSITORY_URL = "repository.hostname";
    private static final String REPOSITORY_USERNAME = "repository.username";
    private static final String REPOSITORY_PASSWORD = "repository.password";

    private static final boolean DEFAULT_REPOSITORY_ENABLED = false;
    private static final String DEFAULT_REPOSITORY_URL = "";
    private static final String DEFAULT_REPOSITORY_USERNAME = "";
    private static final String DEFAULT_REPOSITORY_PASSWORD = "";

    private String host_url = "";
    private boolean is_enabled = false;
    private String allowlist_content = "";
    private boolean enforcement_enabled = false;

    private int hash;

    private Map<String, Object> properties = new HashMap<>();
    private Map<String, Object> newProperties = new HashMap<>();
    private ContainerOrchestrationServiceOptions dso = new ContainerOrchestrationServiceOptions(this.properties);
    private ContainerOrchestrationServiceOptions ddso = new ContainerOrchestrationServiceOptions(this.properties);

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testDisabledDefault() {
        givenEmptyProperties();
        givenDockerServiceOptions();

        whenIsEnabled();

        thenEnabledStateIs(false);
    }

    @Test
    public void testEnabledDefault() {
        givenEmptyProperties();
        givenDockerServiceEnabled(true);
        givenDockerServiceOptions();

        whenIsEnabled();

        thenEnabledStateIs(true);
    }

    @Test
    public void testDefaultHostString() {
        givenEmptyProperties();
        givenDockerServiceOptions();

        whenHostStringSet();

        thenHostStringIs(DEFAULT_DOCKER_HOST_URL);
    }

    @Test
    public void testCustomHostString() {
        givenEmptyProperties();
        givenDockerHostString("unix://Test/Docker");
        givenDockerServiceOptions();

        whenHostStringSet();

        thenHostStringIs("unix://Test/Docker");
    }

    @Test
    public void testHash() {
        givenEmptyProperties();
        givenDockerServiceOptions();

        whenHostStringSet();
        whenIsEnabled();
        whenAllowlistContentSet();
        whenIsEnforcementEnabled();

        whenHashIsCalculated();

        thenHashIsEqual();
    }

    @Test
    public void testDockerServiceDoeNotEqual() {
        givenEmptyProperties();
        givenDockerServiceOptions();
        givenDiffrentProperties();
        givenDifferentDockerServiceOptions();

        thenDockerServiceObjectDoesntEqualsDifferent();
    }

    @Test
    public void testDockerServiceEquals() {
        givenEmptyProperties();
        givenDockerServiceOptions();

        thenDockerServiceObjectEquals();
    }

    @Test
    public void testDockerServiceEqualsWhenNull() {
        givenEmptyProperties();
        givenDockerServiceOptions();

        thenDockerServiceObjectDoesntEqualsWhenNull();
    }

    @Test
    public void testDockerServiceEqualsWhenWrongObjectPasssed() {
        givenEmptyProperties();
        givenDockerServiceOptions();

        thenDockerServiceObjectDoesntEqualsWhenWrongComparison();
    }

    /**
     * Givens
     */
    private void givenEmptyProperties() {
        this.properties = new HashMap<>();
        this.properties.put(DOCKER_HOST_URL, DEFAULT_DOCKER_HOST_URL);
        this.properties.put(IS_ENABLED, DEFAULT_IS_ENABLED);
        this.properties.put(ALLOWLIST_ENABLED, DEFAULT_ALLOWLIST_ENABLED);
        this.properties.put(ALLOWLIST_CONTENT, DEFAULT_ALLOWLIST_CONTENT);
        this.properties.put(REPOSITORY_ENABLED, DEFAULT_REPOSITORY_ENABLED);
        this.properties.put(REPOSITORY_URL, DEFAULT_REPOSITORY_URL);
        this.properties.put(REPOSITORY_USERNAME, DEFAULT_REPOSITORY_USERNAME);
        this.properties.put(REPOSITORY_PASSWORD, DEFAULT_REPOSITORY_PASSWORD);
    }

    private void givenDiffrentProperties() {
        this.newProperties = new HashMap<>();
        this.newProperties.put(DOCKER_HOST_URL, "http://docker.local");
        this.newProperties.put(IS_ENABLED, true);
        this.properties.put(ALLOWLIST_ENABLED, DEFAULT_ALLOWLIST_ENABLED);
        this.properties.put(ALLOWLIST_CONTENT, DEFAULT_ALLOWLIST_CONTENT);
        this.properties.put(REPOSITORY_ENABLED, DEFAULT_REPOSITORY_ENABLED);
        this.properties.put(REPOSITORY_URL, DEFAULT_REPOSITORY_URL);
        this.properties.put(REPOSITORY_USERNAME, DEFAULT_REPOSITORY_USERNAME);
        this.properties.put(REPOSITORY_PASSWORD, DEFAULT_REPOSITORY_PASSWORD);
    }

    private void givenDockerServiceOptions() {
        this.dso = new ContainerOrchestrationServiceOptions(this.properties);
    }

    private void givenDifferentDockerServiceOptions() {
        this.ddso = new ContainerOrchestrationServiceOptions(this.newProperties);
    }

    private void givenDockerServiceEnabled(boolean b) {
        this.properties.put(IS_ENABLED, b);
    }

    private void givenDockerHostString(String s) {
        this.properties.put(DOCKER_HOST_URL, s);
    }

    /**
     * Whens
     */

    private void whenIsEnabled() {
        this.is_enabled = this.dso.isEnabled();
    }

    private void whenHostStringSet() {
        this.host_url = this.dso.getHostUrl();
    }

    private void whenAllowlistContentSet() {
        this.allowlist_content = this.dso.getEnforcementAllowlist();
    }

    private void whenIsEnforcementEnabled() {
        this.enforcement_enabled = this.dso.isEnforcementEnabled();
    }

    private void whenHashIsCalculated() {
        this.hash = Objects.hash(this.allowlist_content, this.enforcement_enabled, this.is_enabled, this.host_url);
    }

    /**
     * Then
     */
    private void thenEnabledStateIs(boolean b) {
        assertEquals(b, this.is_enabled);
    }

    private void thenHostStringIs(String s) {
        assertEquals(s, this.host_url);
    }

    private void thenHashIsEqual() {
        assertEquals(this.hash, this.dso.hashCode());
    }

    private void thenDockerServiceObjectEquals() {
        assertEquals(this.dso, this.dso);
    }

    private void thenDockerServiceObjectDoesntEqualsDifferent() {
        assertNotEquals(this.dso, this.ddso);
    }

    private void thenDockerServiceObjectDoesntEqualsWhenNull() {
        assertNotEquals(this.dso, null);
    }

    private void thenDockerServiceObjectDoesntEqualsWhenWrongComparison() {
        assertNotEquals(this.dso, "String that represents a wrong object passed");
    }

}
