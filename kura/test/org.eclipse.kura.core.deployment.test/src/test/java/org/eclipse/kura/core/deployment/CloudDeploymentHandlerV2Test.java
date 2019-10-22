/*******************************************************************************
 * Copyright (c) 2018, 2019 Eurotech and/or its affiliates and others
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.eclipse.kura.core.deployment;

import static org.eclipse.kura.cloudconnection.request.RequestHandlerMessageConstants.ARGS_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.cloudconnection.message.KuraMessage;
import org.eclipse.kura.cloudconnection.request.RequestHandlerContext;
import org.eclipse.kura.configuration.ConfigurationService;
import org.eclipse.kura.core.deployment.download.DeploymentPackageDownloadOptions;
import org.eclipse.kura.core.deployment.hook.DeploymentHookManager;
import org.eclipse.kura.core.deployment.install.DeploymentPackageInstallOptions;
import org.eclipse.kura.core.deployment.install.InstallImpl;
import org.eclipse.kura.core.deployment.install.KuraInstallPayload;
import org.eclipse.kura.core.deployment.uninstall.UninstallImpl;
import org.eclipse.kura.core.deployment.util.FileUtilities;
import org.eclipse.kura.core.deployment.xml.XmlBundle;
import org.eclipse.kura.core.deployment.xml.XmlBundleInfo;
import org.eclipse.kura.core.deployment.xml.XmlBundles;
import org.eclipse.kura.core.deployment.xml.XmlDeploymentPackage;
import org.eclipse.kura.core.deployment.xml.XmlDeploymentPackages;
import org.eclipse.kura.core.testutil.TestUtil;
import org.eclipse.kura.data.DataTransportService;
import org.eclipse.kura.deployment.hook.DeploymentHook;
import org.eclipse.kura.deployment.hook.RequestContext;
import org.eclipse.kura.download.Download;
import org.eclipse.kura.download.DownloadParameters;
import org.eclipse.kura.download.DownloadService;
import org.eclipse.kura.download.DownloadState;
import org.eclipse.kura.download.DownloadStatus;
import org.eclipse.kura.message.KuraRequestPayload;
import org.eclipse.kura.message.KuraResponsePayload;
import org.eclipse.kura.ssl.SslManagerService;
import org.eclipse.kura.system.SystemService;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.osgi.service.deploymentadmin.BundleInfo;
import org.osgi.service.deploymentadmin.DeploymentAdmin;
import org.osgi.service.deploymentadmin.DeploymentPackage;

public class CloudDeploymentHandlerV2Test {

    private static final String COMPONENT_OPTIONS_FIELD = "componentOptions";
    private static final String TEST_XML = "testXml";

    @Test(expected = NullPointerException.class)
    public void testActivateNoDeploymentHookManagerException() throws NoSuchFieldException, KuraException {
        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        ComponentContext componentContext = mock(ComponentContext.class);

        handler.activate(componentContext, new HashMap<>());
    }

    @Test(expected = NullPointerException.class)
    public void testActivateNoSystemServiceException() throws NoSuchFieldException, KuraException {
        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        DeploymentHookManager dhmMock = mock(DeploymentHookManager.class);
        handler.setDeploymentHookManager(dhmMock);

        ComponentContext componentContext = mock(ComponentContext.class);

        handler.activate(componentContext, new HashMap<>());
    }

    @Test(expected = NullPointerException.class)
    public void testActivateNullSystemServicePropertiesException() throws NoSuchFieldException, KuraException {
        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        DeploymentHookManager dhmMock = mock(DeploymentHookManager.class);
        handler.setDeploymentHookManager(dhmMock);

        ComponentContext componentContext = mock(ComponentContext.class);
        SystemService systemService = mock(SystemService.class);

        handler.setSystemService(systemService);
        when(systemService.getProperties()).thenReturn(null);

        handler.activate(componentContext, new HashMap<>());

    }

    @Test(expected = ComponentException.class)
    @Ignore
    public void testActivateMissingDpaPathException()
            throws NoSuchFieldException, KuraException, InvalidSyntaxException {
        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        DeploymentHookManager dhmMock = mock(DeploymentHookManager.class);
        handler.setDeploymentHookManager(dhmMock);

        ComponentContext componentContext = mock(ComponentContext.class);
        SystemService systemService = mock(SystemService.class);

        handler.setSystemService(systemService);
        when(systemService.getProperties()).thenReturn(new Properties());

        BundleContext bundleContext = mock(BundleContext.class);
        Filter filter = mock(Filter.class);
        when(bundleContext.createFilter(anyString())).thenReturn(filter);
        when(componentContext.getBundleContext()).thenReturn(bundleContext);

        handler.activate(componentContext, new HashMap<>());

    }

    @Test(expected = ComponentException.class)
    @Ignore
    public void testActivateMissingPackagesPath() throws NoSuchFieldException, KuraException {
        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        DeploymentHookManager dhmMock = mock(DeploymentHookManager.class);
        handler.setDeploymentHookManager(dhmMock);

        ComponentContext componentContext = mock(ComponentContext.class);
        SystemService systemService = mock(SystemService.class);

        handler.setSystemService(systemService);
        when(systemService.getProperties()).thenReturn(new Properties());
        System.setProperty("dpa.configuration", "/opt/eclipse/kura/kura/dpa.properties");

        handler.activate(componentContext, new HashMap<>());
    }

    @Test(expected = ComponentException.class)
    @Ignore
    public void testActivateMissingKuraDataDir() throws NoSuchFieldException, KuraException {
        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        DeploymentHookManager dhmMock = mock(DeploymentHookManager.class);
        handler.setDeploymentHookManager(dhmMock);

        ComponentContext componentContext = mock(ComponentContext.class);
        SystemService systemService = mock(SystemService.class);

        Properties systemServiceProps = new Properties();
        systemServiceProps.setProperty("kura.packages", "/opt/eclipse/kura/kura/packages");

        handler.setSystemService(systemService);
        when(systemService.getProperties()).thenReturn(systemServiceProps);
        System.setProperty("dpa.configuration", "/opt/eclipse/kura/kura/dpa.properties");

        handler.activate(componentContext, new HashMap<>());
    }

    @Test
    public void testActivateOk() throws NoSuchFieldException, KuraException, InvalidSyntaxException {
        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        DeploymentHookManager dhmMock = mock(DeploymentHookManager.class);
        handler.setDeploymentHookManager(dhmMock);

        ComponentContext componentContext = mock(ComponentContext.class);
        SystemService systemService = mock(SystemService.class);

        Properties systemServiceProps = new Properties();
        systemServiceProps.setProperty("kura.packages", "/opt/eclipse/kura/kura/packages");
        systemServiceProps.setProperty("kura.data", "/opt/eclipse/kura/data");

        handler.setSystemService(systemService);
        when(systemService.getProperties()).thenReturn(systemServiceProps);
        System.setProperty("dpa.configuration", "/opt/eclipse/kura/kura/dpa.properties");

        BundleContext bundleContext = mock(BundleContext.class);
        Filter filter = mock(Filter.class);
        when(bundleContext.createFilter(anyString())).thenReturn(filter);
        when(componentContext.getBundleContext()).thenReturn(bundleContext);

        handler.activate(componentContext, new HashMap<>());

        assertNotNull(TestUtil.getFieldValue(handler, "installImplementation"));

        handler.deactivate(componentContext);
        assertNull(TestUtil.getFieldValue(handler, "bundleContext"));
    }

    @Test(expected = KuraException.class)
    public void testDoGetNoResources() throws KuraException, NoSuchFieldException {
        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = Collections.emptyList();
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);
        KuraRequestPayload reqPayload = new KuraRequestPayload();

        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doGet(null, message);
    }

    @Test(expected = KuraException.class)
    public void testDoGetOtherwise() throws KuraException, NoSuchFieldException {
        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("test");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();

        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doGet(null, message);
    }

    @Test
    public void testDoGetDownloadNoPendingRequest() throws KuraException, NoSuchFieldException {
        CloudDeploymentHandlerV2 deployment = new CloudDeploymentHandlerV2();

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("download");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        TestUtil.setFieldValue(deployment, "pendingPackageUrl", null);

        KuraMessage resMessage = deployment.doGet(null, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals(KuraResponsePayload.RESPONSE_CODE_OK, resPayload.getResponseCode());
        assertEquals(0, resPayload.getMetric(KuraNotifyPayload.METRIC_TRANSFER_SIZE));
        assertEquals(100, resPayload.getMetric(KuraNotifyPayload.METRIC_TRANSFER_PROGRESS));
        assertEquals("ALREADY DONE", resPayload.getMetric(KuraNotifyPayload.METRIC_TRANSFER_STATUS));

    }

    @Test
    public void testDoGetDownloadPendingRequest() throws KuraException, NoSuchFieldException {
        CloudDeploymentHandlerV2 deployment = new CloudDeploymentHandlerV2();
        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("download");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload request = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(request, reqResources);

        DeploymentPackageDownloadOptions dlOptions = mock(DeploymentPackageDownloadOptions.class);
        TestUtil.setFieldValue(deployment, "downloadOptions", dlOptions);
        TestUtil.setFieldValue(deployment, "pendingPackageUrl", "someValidUrl");

        final DownloadService downloadService = mock(DownloadService.class);
        final Download download = mock(Download.class);
        final DownloadState downloadState = mock(DownloadState.class);

        TestUtil.setFieldValue(deployment, "download", download);

        when(downloadService.createDownload(anyObject())).thenReturn(download);
        when(download.getState()).thenReturn(downloadState);

        when(downloadState.getTotalSize()).thenReturn(Optional.empty());
        when(downloadState.getStatus()).thenReturn(DownloadStatus.IN_PROGRESS);
        when(downloadState.getDownloadPercent()).thenReturn(10L);

        deployment.setDownloadService(downloadService);

        when(dlOptions.getJobId()).thenReturn(1234L);

        KuraMessage resMessage = deployment.doGet(null, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals(KuraResponsePayload.RESPONSE_CODE_OK, resPayload.getResponseCode());
        assertEquals(0, resPayload.getMetric(KuraNotifyPayload.METRIC_TRANSFER_SIZE));
        assertEquals(10, resPayload.getMetric(KuraNotifyPayload.METRIC_TRANSFER_PROGRESS));
        assertEquals(DownloadStatus.IN_PROGRESS.getStatusString(),
                resPayload.getMetric(KuraNotifyPayload.METRIC_TRANSFER_STATUS));
        assertEquals(1234L, resPayload.getMetric(KuraNotifyPayload.METRIC_JOB_ID));

    }

    @Test
    public void testDoGetInstallNoPendingRequest() throws KuraException, NoSuchFieldException {
        CloudDeploymentHandlerV2 deployment = new CloudDeploymentHandlerV2();

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("install");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload request = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(request, reqResources);

        InstallImpl ilMock = new InstallImpl(null, null);
        TestUtil.setFieldValue(deployment, "installImplementation", ilMock);

        KuraMessage resMessage = deployment.doGet(null, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals(KuraResponsePayload.RESPONSE_CODE_OK, resPayload.getResponseCode());
        assertNotNull(resPayload.getTimestamp());
        assertEquals(InstallStatus.IDLE.getStatusString(),
                resPayload.getMetric(KuraInstallPayload.METRIC_INSTALL_STATUS));
    }

    @Test
    public void testDoGetInstallInProgress() throws KuraException, NoSuchFieldException {
        CloudDeploymentHandlerV2 deployment = new CloudDeploymentHandlerV2();
        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("install");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload request = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(request, reqResources);

        String dpName = "heater";
        String dpVersion = "1.0.0";
        DeploymentPackageInstallOptions options = new DeploymentPackageInstallOptions(dpName, dpVersion);
        InstallImpl ilMock = new InstallImpl(null, null);
        TestUtil.setFieldValue(deployment, "installImplementation", ilMock);
        TestUtil.setFieldValue(deployment, "isInstalling", true);
        TestUtil.setFieldValue(ilMock, "options", options);

        KuraMessage resMessage = deployment.doGet(null, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals(KuraResponsePayload.RESPONSE_CODE_OK, resPayload.getResponseCode());
        assertNotNull(resPayload.getTimestamp());
        assertEquals(InstallStatus.IN_PROGRESS.getStatusString(),
                resPayload.getMetric(KuraInstallPayload.METRIC_INSTALL_STATUS));
    }

    @Test
    public void testDoGetPackagesEmptyList() throws KuraException, NoSuchFieldException {
        CloudDeploymentHandlerV2 deployment = new CloudDeploymentHandlerV2() {

            @Override
            protected String marshal(Object object) {
                XmlDeploymentPackages packages = (XmlDeploymentPackages) object;
                XmlDeploymentPackage[] packagesArray = packages.getDeploymentPackages();

                assertEquals(0, packagesArray.length);

                return TEST_XML;
            }
        };

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("packages");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload request = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(request, reqResources);

        DeploymentAdmin deploymentAdmin = mock(DeploymentAdmin.class);
        DeploymentPackage[] deployedPackages = new DeploymentPackage[0];

        TestUtil.setFieldValue(deployment, "deploymentAdmin", deploymentAdmin);

        when(deploymentAdmin.listDeploymentPackages()).thenReturn(deployedPackages);

        KuraMessage resMessage = deployment.doGet(null, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals(KuraResponsePayload.RESPONSE_CODE_OK, resPayload.getResponseCode());
        assertEquals(TEST_XML, new String(resPayload.getBody(), Charset.forName("UTF-8")));
    }

    @Test
    public void testDoGetPackagesOneElementListNoBundleInfos() throws KuraException, NoSuchFieldException {
        DeploymentAdmin deploymentAdmin = mock(DeploymentAdmin.class);
        DeploymentPackage[] deployedPackages = new DeploymentPackage[1];
        DeploymentPackage dp = mock(DeploymentPackage.class);
        deployedPackages[0] = dp;

        CloudDeploymentHandlerV2 deployment = new CloudDeploymentHandlerV2() {

            @Override
            protected String marshal(Object object) {
                XmlDeploymentPackages packages = (XmlDeploymentPackages) object;
                XmlDeploymentPackage[] packagesArray = packages.getDeploymentPackages();

                assertEquals(1, packagesArray.length);
                assertEquals(dp.getName(), packagesArray[0].getName());
                assertEquals(dp.getVersion().toString(), packagesArray[0].getVersion());

                assertEquals(0, packagesArray[0].getBundleInfos().length);

                return TEST_XML;
            }
        };

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("packages");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload request = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(request, reqResources);

        TestUtil.setFieldValue(deployment, "deploymentAdmin", deploymentAdmin);

        when(deploymentAdmin.listDeploymentPackages()).thenReturn(deployedPackages);
        when(dp.getName()).thenReturn("heater");
        when(dp.getVersion()).thenReturn(new Version("1.0.0"));
        when(dp.getBundleInfos()).thenReturn(new BundleInfo[0]);

        KuraMessage resMessage = deployment.doGet(null, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals(KuraResponsePayload.RESPONSE_CODE_OK, resPayload.getResponseCode());
        assertEquals(TEST_XML, new String(resPayload.getBody(), Charset.forName("UTF-8")));
    }

    @Test
    public void testDoGetPackagesOneElementList() throws KuraException, NoSuchFieldException {
        DeploymentAdmin deploymentAdmin = mock(DeploymentAdmin.class);
        DeploymentPackage[] deployedPackages = new DeploymentPackage[1];
        DeploymentPackage dp = mock(DeploymentPackage.class);
        deployedPackages[0] = dp;

        BundleInfo[] bundleInfos = new BundleInfo[1];
        BundleInfo bundleInfo = mock(BundleInfo.class);
        bundleInfos[0] = bundleInfo;

        CloudDeploymentHandlerV2 deployment = new CloudDeploymentHandlerV2() {

            @Override
            protected String marshal(Object object) {
                XmlDeploymentPackages packages = (XmlDeploymentPackages) object;
                XmlDeploymentPackage[] packagesArray = packages.getDeploymentPackages();

                assertEquals(1, packagesArray.length);
                assertEquals(dp.getName(), packagesArray[0].getName());
                assertEquals(dp.getVersion().toString(), packagesArray[0].getVersion());

                XmlBundleInfo[] bis = packagesArray[0].getBundleInfos();
                assertEquals(1, bis.length);
                assertEquals(bundleInfo.getSymbolicName(), bis[0].getName());
                assertEquals(bundleInfo.getVersion().toString(), bis[0].getVersion());

                return TEST_XML;
            }
        };

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("packages");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload request = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(request, reqResources);

        TestUtil.setFieldValue(deployment, "deploymentAdmin", deploymentAdmin);

        when(deploymentAdmin.listDeploymentPackages()).thenReturn(deployedPackages);
        when(dp.getName()).thenReturn("heater");
        when(dp.getVersion()).thenReturn(new Version("1.0.0"));
        when(dp.getBundleInfos()).thenReturn(bundleInfos);
        when(bundleInfo.getSymbolicName()).thenReturn("org.eclipse.kura.demo.heater");
        when(bundleInfo.getVersion()).thenReturn(new Version("1.0.0"));

        KuraMessage resMessage = deployment.doGet(null, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals(KuraResponsePayload.RESPONSE_CODE_OK, resPayload.getResponseCode());
        assertEquals(TEST_XML, new String(resPayload.getBody(), Charset.forName("UTF-8")));
    }

    @Test
    public void testDoGetBundlesNoBundleInstalled() throws KuraException, NoSuchFieldException {
        String xml = TEST_XML;

        CloudDeploymentHandlerV2 deployment = new CloudDeploymentHandlerV2() {

            @Override
            protected String marshal(Object object) {
                XmlBundles bundles = (XmlBundles) object;
                XmlBundle[] bundleArray = bundles.getBundles();
                assertEquals(0, bundleArray.length);

                return xml;
            }
        };

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("bundles");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload request = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(request, reqResources);

        BundleContext context = mock(BundleContext.class);
        TestUtil.setFieldValue(deployment, "bundleContext", context);

        when(context.getBundles()).thenReturn(new Bundle[0]);

        KuraMessage resMessage = deployment.doGet(null, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals(KuraResponsePayload.RESPONSE_CODE_OK, resPayload.getResponseCode());
        assertEquals(xml, new String(resPayload.getBody(), Charset.forName("UTF-8")));
    }

    @Test
    public void testDoGetBundlesBundleUninstalled() throws KuraException, NoSuchFieldException {
        Bundle[] bundles = new Bundle[1];
        Bundle bundle = mock(Bundle.class);
        bundles[0] = bundle;

        CloudDeploymentHandlerV2 deployment = new CloudDeploymentHandlerV2() {

            @Override
            protected String marshal(Object object) {
                XmlBundles bundles = (XmlBundles) object;
                XmlBundle[] bundleArray = bundles.getBundles();
                assertEquals(1, bundleArray.length);

                assertEquals(bundle.getSymbolicName(), bundleArray[0].getName());
                assertEquals(bundle.getVersion().toString(), bundleArray[0].getVersion());
                assertEquals(bundle.getBundleId(), bundleArray[0].getId());
                assertEquals("UNINSTALLED", bundleArray[0].getState());

                return TEST_XML;
            }
        };

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("bundles");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload request = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(request, reqResources);

        BundleContext context = mock(BundleContext.class);
        TestUtil.setFieldValue(deployment, "bundleContext", context);

        when(context.getBundles()).thenReturn(bundles);
        when(bundle.getSymbolicName()).thenReturn("org.eclipse.kura.demo.heater");
        when(bundle.getVersion()).thenReturn(new Version("1.0.0"));
        when(bundle.getBundleId()).thenReturn(1L);
        when(bundle.getState()).thenReturn(Bundle.UNINSTALLED);

        KuraMessage resMessage = deployment.doGet(null, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals(KuraResponsePayload.RESPONSE_CODE_OK, resPayload.getResponseCode());
        assertEquals(TEST_XML, new String(resPayload.getBody(), Charset.forName("UTF-8")));
    }

    @Test
    public void testDoGetBundlesBundleInstalled() throws KuraException, NoSuchFieldException {
        Bundle[] bundles = new Bundle[1];
        Bundle bundle = mock(Bundle.class);
        bundles[0] = bundle;

        CloudDeploymentHandlerV2 deployment = new CloudDeploymentHandlerV2() {

            @Override
            protected String marshal(Object object) {
                XmlBundles bundles = (XmlBundles) object;
                XmlBundle[] bundleArray = bundles.getBundles();
                assertEquals(1, bundleArray.length);

                assertEquals(bundle.getSymbolicName(), bundleArray[0].getName());
                assertEquals(bundle.getVersion().toString(), bundleArray[0].getVersion());
                assertEquals(bundle.getBundleId(), bundleArray[0].getId());
                assertEquals("INSTALLED", bundleArray[0].getState());

                return TEST_XML;
            }
        };

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("bundles");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload request = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(request, reqResources);

        BundleContext context = mock(BundleContext.class);
        TestUtil.setFieldValue(deployment, "bundleContext", context);

        when(context.getBundles()).thenReturn(bundles);
        when(bundle.getSymbolicName()).thenReturn("org.eclipse.kura.demo.heater");
        when(bundle.getVersion()).thenReturn(new Version("1.0.0"));
        when(bundle.getBundleId()).thenReturn(1L);
        when(bundle.getState()).thenReturn(Bundle.INSTALLED);

        KuraMessage resMessage = deployment.doGet(null, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals(KuraResponsePayload.RESPONSE_CODE_OK, resPayload.getResponseCode());
        assertEquals(TEST_XML, new String(resPayload.getBody(), Charset.forName("UTF-8")));
    }

    @Test
    public void testDoGetBundlesBundleResolved() throws KuraException, NoSuchFieldException {
        Bundle[] bundles = new Bundle[1];
        Bundle bundle = mock(Bundle.class);
        bundles[0] = bundle;

        CloudDeploymentHandlerV2 deployment = new CloudDeploymentHandlerV2() {

            @Override
            protected String marshal(Object object) {
                XmlBundles bundles = (XmlBundles) object;
                XmlBundle[] bundleArray = bundles.getBundles();
                assertEquals(1, bundleArray.length);

                assertEquals(bundle.getSymbolicName(), bundleArray[0].getName());
                assertEquals(bundle.getVersion().toString(), bundleArray[0].getVersion());
                assertEquals(bundle.getBundleId(), bundleArray[0].getId());
                assertEquals("RESOLVED", bundleArray[0].getState());

                return TEST_XML;
            }
        };

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("bundles");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload request = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(request, reqResources);

        BundleContext context = mock(BundleContext.class);
        TestUtil.setFieldValue(deployment, "bundleContext", context);

        when(context.getBundles()).thenReturn(bundles);
        when(bundle.getSymbolicName()).thenReturn("org.eclipse.kura.demo.heater");
        when(bundle.getVersion()).thenReturn(new Version("1.0.0"));
        when(bundle.getBundleId()).thenReturn(1L);
        when(bundle.getState()).thenReturn(Bundle.RESOLVED);

        KuraMessage resMessage = deployment.doGet(null, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals(KuraResponsePayload.RESPONSE_CODE_OK, resPayload.getResponseCode());
        assertEquals(TEST_XML, new String(resPayload.getBody(), Charset.forName("UTF-8")));
    }

    @Test
    public void testDoGetBundlesBundleStarting() throws KuraException, NoSuchFieldException {
        Bundle[] bundles = new Bundle[1];
        Bundle bundle = mock(Bundle.class);
        bundles[0] = bundle;

        CloudDeploymentHandlerV2 deployment = new CloudDeploymentHandlerV2() {

            @Override
            protected String marshal(Object object) {
                XmlBundles bundles = (XmlBundles) object;
                XmlBundle[] bundleArray = bundles.getBundles();
                assertEquals(1, bundleArray.length);

                assertEquals(bundle.getSymbolicName(), bundleArray[0].getName());
                assertEquals(bundle.getVersion().toString(), bundleArray[0].getVersion());
                assertEquals(bundle.getBundleId(), bundleArray[0].getId());
                assertEquals("STARTING", bundleArray[0].getState());

                return TEST_XML;
            }
        };

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("bundles");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload request = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(request, reqResources);

        BundleContext context = mock(BundleContext.class);
        TestUtil.setFieldValue(deployment, "bundleContext", context);

        when(context.getBundles()).thenReturn(bundles);
        when(bundle.getSymbolicName()).thenReturn("org.eclipse.kura.demo.heater");
        when(bundle.getVersion()).thenReturn(new Version("1.0.0"));
        when(bundle.getBundleId()).thenReturn(1L);
        when(bundle.getState()).thenReturn(Bundle.STARTING);

        KuraMessage resMessage = deployment.doGet(null, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals(KuraResponsePayload.RESPONSE_CODE_OK, resPayload.getResponseCode());
        assertEquals(TEST_XML, new String(resPayload.getBody(), Charset.forName("UTF-8")));
    }

    @Test
    public void testDoGetBundlesBundleStopping() throws KuraException, NoSuchFieldException {
        Bundle[] bundles = new Bundle[1];
        Bundle bundle = mock(Bundle.class);
        bundles[0] = bundle;

        CloudDeploymentHandlerV2 deployment = new CloudDeploymentHandlerV2() {

            @Override
            protected String marshal(Object object) {
                XmlBundles bundles = (XmlBundles) object;
                XmlBundle[] bundleArray = bundles.getBundles();
                assertEquals(1, bundleArray.length);

                assertEquals(bundle.getSymbolicName(), bundleArray[0].getName());
                assertEquals(bundle.getVersion().toString(), bundleArray[0].getVersion());
                assertEquals(bundle.getBundleId(), bundleArray[0].getId());
                assertEquals("STOPPING", bundleArray[0].getState());

                return TEST_XML;
            }
        };

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("bundles");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload request = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(request, reqResources);

        BundleContext context = mock(BundleContext.class);
        TestUtil.setFieldValue(deployment, "bundleContext", context);

        when(context.getBundles()).thenReturn(bundles);
        when(bundle.getSymbolicName()).thenReturn("org.eclipse.kura.demo.heater");
        when(bundle.getVersion()).thenReturn(new Version("1.0.0"));
        when(bundle.getBundleId()).thenReturn(1L);
        when(bundle.getState()).thenReturn(Bundle.STOPPING);

        KuraMessage resMessage = deployment.doGet(null, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals(KuraResponsePayload.RESPONSE_CODE_OK, resPayload.getResponseCode());
        assertEquals(TEST_XML, new String(resPayload.getBody(), Charset.forName("UTF-8")));
    }

    @Test
    public void testDoGetBundlesBundleActive() throws KuraException, NoSuchFieldException {
        Bundle[] bundles = new Bundle[1];
        Bundle bundle = mock(Bundle.class);
        bundles[0] = bundle;

        CloudDeploymentHandlerV2 deployment = new CloudDeploymentHandlerV2() {

            @Override
            protected String marshal(Object object) {
                XmlBundles bundles = (XmlBundles) object;
                XmlBundle[] bundleArray = bundles.getBundles();
                assertEquals(1, bundleArray.length);

                assertEquals(bundle.getSymbolicName(), bundleArray[0].getName());
                assertEquals(bundle.getVersion().toString(), bundleArray[0].getVersion());
                assertEquals(bundle.getBundleId(), bundleArray[0].getId());
                assertEquals("ACTIVE", bundleArray[0].getState());

                return TEST_XML;
            }
        };

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("bundles");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload request = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(request, reqResources);

        BundleContext context = mock(BundleContext.class);
        TestUtil.setFieldValue(deployment, "bundleContext", context);

        when(context.getBundles()).thenReturn(bundles);
        when(bundle.getSymbolicName()).thenReturn("org.eclipse.kura.demo.heater");
        when(bundle.getVersion()).thenReturn(new Version("1.0.0"));
        when(bundle.getBundleId()).thenReturn(1L);
        when(bundle.getState()).thenReturn(Bundle.ACTIVE);

        KuraMessage resMessage = deployment.doGet(null, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals(KuraResponsePayload.RESPONSE_CODE_OK, resPayload.getResponseCode());
        assertEquals(TEST_XML, new String(resPayload.getBody(), Charset.forName("UTF-8")));
    }

    @Test(expected = KuraException.class)
    public void testDoDelNoResources() throws KuraException, NoSuchFieldException {
        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = Collections.emptyList();
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doDel(null, message);
    }

    @Test(expected = KuraException.class)
    public void testDoDelOtherwise() throws KuraException, NoSuchFieldException {
        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("test");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doDel(null, message);
    }

    @Test
    public void testDoDelNormal() throws Exception {
        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        final Download download = mock(Download.class);
        final DownloadParameters params = mock(DownloadParameters.class);
        when(download.getParameters()).thenReturn(params);
        when(params.getDestination()).thenReturn(new File("/foo/bar"));

        @SuppressWarnings("unchecked")
        final CompletableFuture<Void> future = mock(CompletableFuture.class);

        when(download.future()).thenReturn(future);

        TestUtil.setFieldValue(handler, "download", download);

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_DOWNLOAD);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doDel(null, message);

        verify(future).cancel(true);
        verify(download).getParameters();
        verify(params).getDestination();
    }

    @Test(expected = KuraException.class)
    public void testDoDelException() throws NoSuchFieldException, KuraException {
        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        final Download download = mock(Download.class);
        TestUtil.setFieldValue(handler, "download", download);

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_DOWNLOAD);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doDel(null, message);

        verify(download).future();
    }

    @Test(expected = KuraException.class)
    public void testDoExecNoResources() throws KuraException, NoSuchFieldException {
        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = Collections.emptyList();
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doExec(null, message);
    }

    @Test(expected = KuraException.class)
    public void testDoExecOtherwise() throws KuraException, NoSuchFieldException {
        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add("test");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doExec(null, message);
    }

    @Test(expected = KuraException.class)
    public void testDoExecInstallDeploymentOptionsException() throws KuraException, NoSuchFieldException {
        // fail immediately after calling doExecInstall

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_INSTALL);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doExec(null, message);
    }

    @Test(expected = KuraException.class)
    public void testDoExecInstallPendingPackage() throws KuraException, NoSuchFieldException {
        // fail at pending test

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_INSTALL);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_NAME, "");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_VERSION, "");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_JOB_ID, 1234L);
        reqPayload.addMetric(DeploymentPackageInstallOptions.METRIC_DP_INSTALL_SYSTEM_UPDATE, false);

        DataTransportService dtsMock = mock(DataTransportService.class);
        handler.setDataTransportService(dtsMock);

        final DownloadService downloadService = mock(DownloadService.class);

        KuraException ex = new KuraException(KuraErrorCode.OPERATION_NOT_SUPPORTED);
        when(downloadService.createDownload(anyObject(), anyObject())).thenThrow(ex);
        TestUtil.setFieldValue(handler, "downloadService", downloadService);

        handler.doExec(null, message);
    }

    @Test(expected = KuraException.class)
    public void testDoExecInstallNotDownloaded() throws KuraException, NoSuchFieldException {
        // fail because not downloaded

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_INSTALL);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);
        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_NAME, "");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_VERSION, "");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_JOB_ID, 1234L);
        reqPayload.addMetric(DeploymentPackageInstallOptions.METRIC_DP_INSTALL_SYSTEM_UPDATE, false);

        DataTransportService dtsMock = mock(DataTransportService.class);
        handler.setDataTransportService(dtsMock);

        final Download download = mock(Download.class);
        final DownloadState downloadState = mock(DownloadState.class);
        when(downloadState.getStatus()).thenReturn(DownloadStatus.IN_PROGRESS);
        when(download.getState()).thenReturn(downloadState);

        TestUtil.setFieldValue(handler, "download", download);

        handler.doExec(null, message);
    }

    @Test(expected = KuraException.class)
    public void testDoExecInstallDownloadedExceptionInstalling() throws KuraException, NoSuchFieldException {
        // fail without file

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2() {

            @Override
            protected File getDpDownloadFile(DeploymentPackageInstallOptions options) throws IOException {
                throw new IOException("test");
            }
        };
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_INSTALL);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);
        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_NAME, "");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_VERSION, "");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_JOB_ID, 1234L);
        reqPayload.addMetric(DeploymentPackageInstallOptions.METRIC_DP_INSTALL_SYSTEM_UPDATE, false);

        DataTransportService dtsMock = mock(DataTransportService.class);
        handler.setDataTransportService(dtsMock);

        final Download download = mock(Download.class);
        final DownloadState downloadState = mock(DownloadState.class);
        when(downloadState.getStatus()).thenReturn(DownloadStatus.COMPLETED);
        when(download.getState()).thenReturn(downloadState);

        TestUtil.setFieldValue(handler, "download", download);
        TestUtil.setFieldValue(handler, "isInstalling", false);

        handler.doExec(null, message);
    }

    @Test
    public void testDoExecInstallDownloaded() throws KuraException, NoSuchFieldException, InterruptedException {

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2() {

            @Override
            protected File getDpDownloadFile(final DeploymentPackageInstallOptions options) throws IOException {
                String dpName = FileUtilities.getFileName(options.getDpName(), options.getDpVersion(), ".dp");
                String packageFilename = new StringBuilder().append("/tmp").append(File.separator).append(dpName)
                        .toString();
                return new File(packageFilename);
            }
        };

        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        DeploymentHookManager dhmMock = mock(DeploymentHookManager.class);
        handler.setDeploymentHookManager(dhmMock);

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_INSTALL);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);
        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_NAME, "heater");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_VERSION, "1.0.0");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_JOB_ID, 1234L);
        reqPayload.addMetric(DeploymentPackageInstallOptions.METRIC_DP_INSTALL_SYSTEM_UPDATE, false);

        DataTransportService dtsMock = mock(DataTransportService.class);
        handler.setDataTransportService(dtsMock);

        final Download download = mock(Download.class);
        final DownloadState downloadState = mock(DownloadState.class);
        when(downloadState.getStatus()).thenReturn(DownloadStatus.COMPLETED);
        when(download.getState()).thenReturn(downloadState);
        TestUtil.setFieldValue(handler, "download", download);

        TestUtil.setFieldValue(handler, "isInstalling", false);

        InstallImpl installImpl = mock(InstallImpl.class);
        TestUtil.setFieldValue(handler, "installImplementation", installImpl);

        RequestHandlerContext requestContext = new RequestHandlerContext(null, Collections.emptyMap());
        KuraMessage resMessage = handler.doExec(requestContext, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals("Response code should match expected", KuraResponsePayload.RESPONSE_CODE_OK,
                resPayload.getResponseCode());

        Thread.sleep(500);
        assertNull(TestUtil.getFieldValue(handler, "installOptions"));
        assertFalse((boolean) TestUtil.getFieldValue(handler, "isInstalling"));
    }

    @Test(expected = KuraException.class)
    public void testDoExecDownloadOptionsException() throws KuraException, NoSuchFieldException {
        // fail soon after calling doExecDownload

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_DOWNLOAD);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);
        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doExec(null, message);
    }

    @Test
    public void testDoExecDownloadPendingUrl() throws KuraException, NoSuchFieldException {
        // fail at pending url

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_DOWNLOAD);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        reqPayload.addMetric(DeploymentPackageDownloadOptions.METRIC_DP_DOWNLOAD_URI, "");
        reqPayload.addMetric(DeploymentPackageDownloadOptions.METRIC_DP_DOWNLOAD_PROTOCOL, "");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_NAME, "");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_VERSION, "");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_JOB_ID, 1234L);
        reqPayload.addMetric(DeploymentPackageInstallOptions.METRIC_DP_INSTALL_SYSTEM_UPDATE, false);

        DataTransportService dtsMock = mock(DataTransportService.class);
        handler.setDataTransportService(dtsMock);

        when(dtsMock.getClientId()).thenReturn("ClientId");

        handler.setDownloadService(mock(DownloadService.class));
        TestUtil.setFieldValue(handler, "pendingPackageUrl", "url");

        KuraMessage resMessage = handler.doExec(null, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        TestUtil.setFieldValue(handler, "pendingPackageUrl", null);

        assertEquals("Response code should match expected", KuraResponsePayload.RESPONSE_CODE_ERROR,
                resPayload.getResponseCode());
        assertNotNull("Response timestamp should be set", resPayload.getTimestamp());
        assertEquals("Body should match", "Another resource is already in download",
                new String(resPayload.getBody(), Charset.forName("UTF-8")));
        assertEquals(DownloadStatus.IN_PROGRESS.getStatusString(),
                resPayload.getMetric(CloudDeploymentHandlerV2.METRIC_DOWNLOAD_STATUS));
    }

    @Test
    public void testDoExecDownloadSuccessfulNoInstall() throws KuraException, NoSuchFieldException {

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();

        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        DeploymentHookManager dhmMock = mock(DeploymentHookManager.class);
        handler.setDeploymentHookManager(dhmMock);

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_DOWNLOAD);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        reqPayload.addMetric(DeploymentPackageDownloadOptions.METRIC_DP_DOWNLOAD_URI,
                "https://s3-us-west-2.amazonaws.com/kura-repo/drivers/3.0.0-RELEASE/org.eclipse.kura.demo.heater_1.0.100.dp");
        reqPayload.addMetric(DeploymentPackageDownloadOptions.METRIC_DP_DOWNLOAD_PROTOCOL, "http");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_NAME, "heater");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_VERSION, "1.0.0");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_JOB_ID, 1234L);
        reqPayload.addMetric(DeploymentPackageInstallOptions.METRIC_DP_INSTALL_SYSTEM_UPDATE, false);

        DataTransportService dtsMock = mock(DataTransportService.class);
        handler.setDataTransportService(dtsMock);

        SslManagerService sslManagerService = mock(SslManagerService.class);
        handler.setSslManagerService(sslManagerService);

        final DownloadService downloadService = mock(DownloadService.class);
        final Download download = mock(Download.class);

        when(downloadService.createDownload(anyObject(), anyObject())).thenReturn(download);
        when(download.future()).thenReturn(new CompletableFuture<>());

        handler.setDownloadService(downloadService);

        when(dtsMock.getClientId()).thenReturn("ClientId");
        TestUtil.setFieldValue(handler, "pendingPackageUrl", null);

        RequestHandlerContext requestContext = new RequestHandlerContext(null, Collections.emptyMap());
        KuraMessage resMessage = handler.doExec(requestContext, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals(KuraResponsePayload.RESPONSE_CODE_OK, resPayload.getResponseCode());
        verify(download).start();
    }

    @Test
    public void testDoExecDownloadFailure()
            throws KuraException, NoSuchFieldException, IOException, InterruptedException {

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();

        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        DeploymentHookManager dhmMock = mock(DeploymentHookManager.class);
        handler.setDeploymentHookManager(dhmMock);

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_DOWNLOAD);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        reqPayload.addMetric(DeploymentPackageDownloadOptions.METRIC_DP_DOWNLOAD_URI, "http://heater.value");
        reqPayload.addMetric(DeploymentPackageDownloadOptions.METRIC_DP_DOWNLOAD_PROTOCOL, "http");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_NAME, "heater");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_VERSION, "1.0.0");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_JOB_ID, 1234L);
        reqPayload.addMetric(DeploymentPackageInstallOptions.METRIC_DP_INSTALL_SYSTEM_UPDATE, false);

        DataTransportService dtsMock = mock(DataTransportService.class);
        handler.setDataTransportService(dtsMock);

        SslManagerService sslManagerService = mock(SslManagerService.class);
        handler.setSslManagerService(sslManagerService);

        final DownloadService downloadService = mock(DownloadService.class);
        final Download download = mock(Download.class);

        when(downloadService.createDownload(anyObject(), anyObject())).thenReturn(download);

        final CompletableFuture<Void> future = new CompletableFuture<>();
        when(download.future()).thenReturn(future);

        handler.setDownloadService(downloadService);

        when(dtsMock.getClientId()).thenReturn("ClientId");

        RequestHandlerContext requestContext = new RequestHandlerContext(null, Collections.emptyMap());
        KuraMessage resMessage = handler.doExec(requestContext, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals(KuraResponsePayload.RESPONSE_CODE_OK, resPayload.getResponseCode());

        future.completeExceptionally(new RuntimeException("failed"));

        Thread.sleep(500);
        assertNull(TestUtil.getFieldValue(handler, "pendingPackageUrl"));
        verify(download).start();
    }

    @Test(expected = KuraException.class)
    public void testDoExecUninstallOptionsException() throws KuraException, NoSuchFieldException {
        // fail soon after calling doExecUninstall

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_UNINSTALL);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doExec(null, message);
    }

    @Test(expected = KuraException.class)
    public void testDoExecUninstallPendingUrl() throws KuraException, NoSuchFieldException {
        // fail at installing/pending package name

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_UNINSTALL);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_NAME, "");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_JOB_ID, 1234L);

        DataTransportService dtsMock = mock(DataTransportService.class);
        handler.setDataTransportService(dtsMock);

        when(dtsMock.getClientId()).thenReturn("ClientId");

        TestUtil.setFieldValue(handler, "isInstalling", false);
        TestUtil.setFieldValue(handler, "pendingUninstPackageName", "");

        handler.doExec(null, message);
    }

    @Test(expected = KuraException.class)
    public void testDoExecUninstallIsDownloadedException() throws KuraException, NoSuchFieldException {
        // fail with UninstallImpl exception
        String testMesg = "testMesg";

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2() {

            @Override
            protected UninstallImpl createUninstallImpl() {
                throw new RuntimeException(testMesg);
            }
        };
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_UNINSTALL);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        reqPayload.addMetric(DeploymentPackageDownloadOptions.METRIC_DP_DOWNLOAD_URI, "");
        reqPayload.addMetric(DeploymentPackageDownloadOptions.METRIC_DP_DOWNLOAD_PROTOCOL, "");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_NAME, "");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_VERSION, "");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_JOB_ID, 1234L);
        reqPayload.addMetric(DeploymentPackageInstallOptions.METRIC_DP_INSTALL_SYSTEM_UPDATE, false);

        DataTransportService dtsMock = mock(DataTransportService.class);
        handler.setDataTransportService(dtsMock);

        when(dtsMock.getClientId()).thenReturn("ClientId");

        TestUtil.setFieldValue(handler, "isInstalling", true);

        handler.doExec(null, message);
    }

    @Test(expected = KuraException.class)
    public void testDoExecStopNoBundleId() throws KuraException, NoSuchFieldException {
        // don't fail before the actual call

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_STOP);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doExec(null, message);
    }

    @Test(expected = KuraException.class)
    public void testDoExecStartNoBundleId() throws KuraException, NoSuchFieldException {
        // fail because of missing/null bundle id

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_START);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doExec(null, message);
    }

    @Test(expected = KuraException.class)
    public void testDoExecStartNFE() throws KuraException, NoSuchFieldException {
        // fail because of String bundle id

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_START);
        resourcesList.add("aa");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doExec(null, message);
    }

    @Test(expected = KuraException.class)
    public void testDoExecStartNullBundle() throws KuraException, NoSuchFieldException {
        // fail because of null bundle

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_START);
        resourcesList.add("99");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        BundleContext ctxMock = mock(BundleContext.class);
        TestUtil.setFieldValue(handler, "bundleContext", ctxMock);

        when(ctxMock.getBundle(99)).thenReturn(null);

        handler.doExec(null, message);

        verify(ctxMock).getBundle(99);
    }

    @Test(expected = KuraException.class)
    public void testDoExecStartBundleExc() throws KuraException, NoSuchFieldException, BundleException {
        // fail because of start exception

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_START);
        resourcesList.add("99");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        BundleContext ctxMock = mock(BundleContext.class);
        TestUtil.setFieldValue(handler, "bundleContext", ctxMock);

        Bundle bundleMock = mock(Bundle.class);
        when(ctxMock.getBundle(99)).thenReturn(bundleMock);

        BundleException ex = new BundleException("test");
        doThrow(ex).when(bundleMock).start();

        handler.doExec(null, message);

        verify(ctxMock).getBundle(99);
        verify(bundleMock).start();
    }

    @Test
    public void testDoExecStopBundleExc() throws KuraException, NoSuchFieldException, BundleException {
        // OK

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_STOP);
        resourcesList.add("99");
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();
        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        BundleContext ctxMock = mock(BundleContext.class);
        TestUtil.setFieldValue(handler, "bundleContext", ctxMock);

        Bundle bundleMock = mock(Bundle.class);
        when(ctxMock.getBundle(99)).thenReturn(bundleMock);

        KuraMessage resMessage = handler.doExec(null, message);

        KuraResponsePayload resPayload = (KuraResponsePayload) resMessage.getPayload();

        assertEquals("Response code should match expected", KuraResponsePayload.RESPONSE_CODE_OK,
                resPayload.getResponseCode());

        verify(ctxMock).getBundle(99);
        verify(bundleMock).stop();
    }

    private ServiceReference<DeploymentHook> mockHookReference(String kuraServicePid, DeploymentHook deploymentHook) {
        @SuppressWarnings("unchecked")
        final ServiceReference<DeploymentHook> reference = mock(ServiceReference.class);
        when(reference.getProperty(anyString())).then(invocation -> {
            String arg = invocation.getArgumentAt(0, String.class);
            if (ConfigurationService.KURA_SERVICE_PID.equals(arg)) {
                return kuraServicePid;
            }
            return deploymentHook;
        });
        return reference;
    }

    private DeploymentHookManager getDeploymentHookManager() throws NoSuchFieldException {
        final DeploymentHookManager result = new DeploymentHookManager();
        final BundleContext mockBundleContext = mock(BundleContext.class);
        when(mockBundleContext.getService(anyObject())).then(invocation -> {
            final ServiceReference<?> ref = invocation.getArgumentAt(0, ServiceReference.class);
            return ref.getProperty("service");
        });
        TestUtil.setFieldValue(result, "bundleContext", mockBundleContext);
        return result;
    }

    @Test(expected = KuraException.class)
    public void testDoExecDownloadNoRegisteredHookException() throws KuraException, NoSuchFieldException {

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        final DeploymentHookManager manager = getDeploymentHookManager();
        handler.setDeploymentHookManager(manager);
        handler.setDataTransportService(mock(DataTransportService.class));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_DOWNLOAD);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);
        KuraRequestPayload reqPayload = new KuraRequestPayload();

        reqPayload.addMetric(DeploymentPackageDownloadOptions.METRIC_DP_DOWNLOAD_URI, "http://localhost:1234");
        reqPayload.addMetric(DeploymentPackageDownloadOptions.METRIC_DP_DOWNLOAD_PROTOCOL, "HTTP");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_NAME, "test");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_VERSION, "1.0");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_JOB_ID, 1234L);
        reqPayload.addMetric(DeploymentPackageInstallOptions.METRIC_DP_INSTALL_SYSTEM_UPDATE, false);
        reqPayload.addMetric(DeploymentPackageInstallOptions.METRIC_REQUEST_TYPE, "someRequestType");

        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doExec(null, message);
    }

    @Test(expected = KuraException.class)
    public void testDoExecInstallNoRegisteredHookException() throws KuraException, NoSuchFieldException {

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        final DeploymentHookManager manager = getDeploymentHookManager();
        handler.setDeploymentHookManager(manager);
        handler.setDataTransportService(mock(DataTransportService.class));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_INSTALL);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);
        KuraRequestPayload reqPayload = new KuraRequestPayload();

        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_NAME, "test");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_VERSION, "1.0");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_JOB_ID, 1234L);
        reqPayload.addMetric(DeploymentPackageInstallOptions.METRIC_DP_INSTALL_SYSTEM_UPDATE, false);
        reqPayload.addMetric(DeploymentPackageInstallOptions.METRIC_REQUEST_TYPE, "someRequestType");

        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doExec(null, message);
    }

    @Test(expected = KuraException.class)
    public void testDoExecDownloadShouldAbortOnPreDownload() throws KuraException, NoSuchFieldException {

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        final DeploymentHookManager manager = getDeploymentHookManager();
        manager.bindHook(mockHookReference("testHook", new DeploymentHook() {

            @Override
            public void preDownload(RequestContext context, Map<String, Object> properties) throws KuraException {
                throw new IllegalStateException("Aborted by hook");
            }

            @Override
            public void postInstall(RequestContext context, Map<String, Object> properties) throws KuraException {
            }

            @Override
            public void postDownload(RequestContext context, Map<String, Object> properties) throws KuraException {
            }
        }));
        final Properties associations = new Properties();
        associations.setProperty("testRequest", "testHook");
        manager.updateAssociations(associations);

        handler.setDeploymentHookManager(manager);
        handler.setDataTransportService(mock(DataTransportService.class));

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_DOWNLOAD);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();

        reqPayload.addMetric(DeploymentPackageDownloadOptions.METRIC_DP_DOWNLOAD_URI, "");
        reqPayload.addMetric(DeploymentPackageDownloadOptions.METRIC_DP_DOWNLOAD_PROTOCOL, "");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_NAME, "");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_VERSION, "");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_JOB_ID, 1234L);
        reqPayload.addMetric(DeploymentPackageInstallOptions.METRIC_DP_INSTALL_SYSTEM_UPDATE, true);
        reqPayload.addMetric(DeploymentPackageInstallOptions.METRIC_REQUEST_TYPE, "testRequest");

        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doExec(null, message);
    }

    @Test(expected = KuraException.class)
    public void testDoExecInstallShouldAbortOnPostDownload() throws KuraException, NoSuchFieldException {

        CloudDeploymentHandlerV2 handler = new CloudDeploymentHandlerV2();
        TestUtil.setFieldValue(handler, COMPONENT_OPTIONS_FIELD, new CloudDeploymentHandlerV2Options(new HashMap<>()));

        final DeploymentHookManager manager = getDeploymentHookManager();
        manager.bindHook(mockHookReference("testHook", new DeploymentHook() {

            @Override
            public void preDownload(RequestContext context, Map<String, Object> properties) throws KuraException {
            }

            @Override
            public void postInstall(RequestContext context, Map<String, Object> properties) throws KuraException {
            }

            @Override
            public void postDownload(RequestContext context, Map<String, Object> properties) throws KuraException {
                throw new IllegalStateException("Aborted by hook");
            }
        }));
        final Properties associations = new Properties();
        associations.setProperty("testRequest", "testHook");
        manager.updateAssociations(associations);
        assertTrue(manager.getHook("testRequest") != null);

        handler.setDeploymentHookManager(manager);
        handler.setDataTransportService(mock(DataTransportService.class));

        final DownloadService downloadService = mock(DownloadService.class);
        final Download download = mock(Download.class);

        when(downloadService.createDownload(anyObject(), anyObject())).thenReturn(download);
        when(download.future()).thenReturn(CompletableFuture.completedFuture(null));

        handler.setDownloadService(downloadService);

        List<String> resourcesList = new ArrayList<>();
        resourcesList.add(CloudDeploymentHandlerV2.RESOURCE_INSTALL);
        Map<String, Object> reqResources = new HashMap<>();
        reqResources.put(ARGS_KEY.value(), resourcesList);

        KuraRequestPayload reqPayload = new KuraRequestPayload();

        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_NAME, "test");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_DP_VERSION, "1.0");
        reqPayload.addMetric(DeploymentPackageOptions.METRIC_JOB_ID, 1237L);
        reqPayload.addMetric(DeploymentPackageInstallOptions.METRIC_DP_INSTALL_SYSTEM_UPDATE, true);
        reqPayload.addMetric(DeploymentPackageInstallOptions.METRIC_REQUEST_TYPE, "testRequest");

        KuraMessage message = new KuraMessage(reqPayload, reqResources);

        handler.doExec(null, message);
    }

    public void testPublishMessage() {
        fail("Not yet implemented");
    }

    public void testInstallDownloadedFile() {
        fail("Not yet implemented");
    }

}
