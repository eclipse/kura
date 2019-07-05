/*******************************************************************************
 * Copyright (c) 2011, 2019 Eurotech and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech
 *     Red Hat Inc - Clean up kura properties handling
 *******************************************************************************/
package org.eclipse.kura.core.deployment;

import static java.util.Objects.nonNull;
import static org.eclipse.kura.cloudconnection.request.RequestHandlerContextConstants.NOTIFICATION_PUBLISHER_PID;
import static org.eclipse.kura.cloudconnection.request.RequestHandlerMessageConstants.ARGS_KEY;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.cloudconnection.message.KuraMessage;
import org.eclipse.kura.cloudconnection.publisher.CloudNotificationPublisher;
import org.eclipse.kura.cloudconnection.request.RequestHandler;
import org.eclipse.kura.cloudconnection.request.RequestHandlerContext;
import org.eclipse.kura.cloudconnection.request.RequestHandlerRegistry;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.core.deployment.download.DeploymentPackageDownloadOptions;
import org.eclipse.kura.core.deployment.download.DownloadCountingOutputStream;
import org.eclipse.kura.core.deployment.download.DownloadFileUtilities;
import org.eclipse.kura.core.deployment.download.impl.DownloadImpl;
import org.eclipse.kura.core.deployment.hook.DeploymentHookManager;
import org.eclipse.kura.core.deployment.install.DeploymentPackageInstallOptions;
import org.eclipse.kura.core.deployment.install.InstallImpl;
import org.eclipse.kura.core.deployment.request.PersistedRequestServiceImpl;
import org.eclipse.kura.core.deployment.uninstall.DeploymentPackageUninstallOptions;
import org.eclipse.kura.core.deployment.uninstall.UninstallImpl;
import org.eclipse.kura.core.deployment.xml.XmlBundle;
import org.eclipse.kura.core.deployment.xml.XmlBundleInfo;
import org.eclipse.kura.core.deployment.xml.XmlBundles;
import org.eclipse.kura.core.deployment.xml.XmlDeploymentPackage;
import org.eclipse.kura.core.deployment.xml.XmlDeploymentPackages;
import org.eclipse.kura.data.DataTransportService;
import org.eclipse.kura.deployment.hook.DeploymentHook;
import org.eclipse.kura.deployment.hook.PersistedRequestService;
import org.eclipse.kura.deployment.hook.Request;
import org.eclipse.kura.deployment.hook.RequestEventStream;
import org.eclipse.kura.marshalling.Marshaller;
import org.eclipse.kura.message.KuraPayload;
import org.eclipse.kura.message.KuraResponsePayload;
import org.eclipse.kura.ssl.SslManagerService;
import org.eclipse.kura.system.SystemService;
import org.eclipse.kura.util.service.ServiceUtil;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.deploymentadmin.BundleInfo;
import org.osgi.service.deploymentadmin.DeploymentAdmin;
import org.osgi.service.deploymentadmin.DeploymentPackage;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudDeploymentHandlerV2 implements ConfigurableComponent, RequestHandler, PersistedRequestService {

    private final class CloudNotificationPublisherTrackerCustomizer
            implements ServiceTrackerCustomizer<CloudNotificationPublisher, CloudNotificationPublisher> {

        @Override
        public CloudNotificationPublisher addingService(final ServiceReference<CloudNotificationPublisher> reference) {
            CloudDeploymentHandlerV2.this.cloudNotificationPublisher = CloudDeploymentHandlerV2.this.bundleContext
                    .getService(reference);
            String notificationPublisherPid = (String) reference.getProperty("kura.service.pid");
            CloudDeploymentHandlerV2.this.cloudNotificationPublisherPid = notificationPublisherPid;

            installImplementation.sendInstallConfirmations(notificationPublisherPid,
                    CloudDeploymentHandlerV2.this.cloudNotificationPublisher);

            persistedRequestServiceImpl.addNotificationPublisher(notificationPublisherPid,
                    CloudDeploymentHandlerV2.this.cloudNotificationPublisher);

            return CloudDeploymentHandlerV2.this.cloudNotificationPublisher;
        }

        @Override
        public void modifiedService(final ServiceReference<CloudNotificationPublisher> reference,
                final CloudNotificationPublisher service) {
            // Not needed
        }

        @Override
        public void removedService(final ServiceReference<CloudNotificationPublisher> reference,
                final CloudNotificationPublisher service) {
            String notificationPublisherPid = (String) reference.getProperty("kura.service.pid");

            persistedRequestServiceImpl.removeNotificationPublisher(notificationPublisherPid);
            CloudDeploymentHandlerV2.this.cloudNotificationPublisher = null;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(CloudDeploymentHandlerV2.class);
    public static final String APP_ID = "DEPLOY-V2";

    public static final String RESOURCE_PACKAGES = "packages";
    public static final String RESOURCE_BUNDLES = "bundles";

    /* EXEC */
    public static final String RESOURCE_DOWNLOAD = "download";
    public static final String RESOURCE_INSTALL = "install";
    public static final String RESOURCE_UNINSTALL = "uninstall";
    public static final String RESOURCE_CANCEL = "cancel";
    public static final String RESOURCE_START = "start";
    public static final String RESOURCE_STOP = "stop";

    /* Metrics in the REPLY to RESOURCE_DOWNLOAD */
    public static final String METRIC_DOWNLOAD_STATUS = "download.status";
    public static final String METRIC_REQUESTER_CLIENT_ID = "requester.client.id";

    private static final String MESSAGE_TYPE_KEY = "messageType";

    private static final String REQUESTOR_CLIENT_ID_KEY = "requestorClientId";

    private static final String APP_ID_KEY = "appId";

    private static String pendingPackageUrl = null;
    private static DownloadImpl downloadImplementation;
    private static UninstallImpl uninstallImplementation;
    public static InstallImpl installImplementation;

    private CloudDeploymentHandlerV2Options componentOptions;

    private SslManagerService sslManagerService;
    private DeploymentAdmin deploymentAdmin;
    private SystemService systemService;
    private DeploymentHookManager deploymentHookManager;

    private static ExecutorService executor = Executors.newSingleThreadExecutor();

    private Future<?> downloaderFuture;
    private Future<?> installerFuture;

    private BundleContext bundleContext;

    private DataTransportService dataTransportService;

    private DeploymentPackageDownloadOptions downloadOptions;

    private boolean isInstalling = false;
    private DeploymentPackageInstallOptions installOptions;

    private String pendingUninstPackageName;
    private String installVerificationDir;

    private String cloudNotificationPublisherPid;
    private CloudNotificationPublisher cloudNotificationPublisher;

    private ServiceTrackerCustomizer<CloudNotificationPublisher, CloudNotificationPublisher> cloudPublisherTrackerCustomizer;

    private ServiceTracker<CloudNotificationPublisher, CloudNotificationPublisher> cloudPublisherTracker;

    private final PersistedRequestServiceImpl persistedRequestServiceImpl = new PersistedRequestServiceImpl();

    // ----------------------------------------------------------------
    //
    // Dependencies
    //
    // ----------------------------------------------------------------

    public void setSslManagerService(SslManagerService sslManagerService) {
        this.sslManagerService = sslManagerService;
    }

    public void unsetSslManagerService(SslManagerService sslManagerService) {
        this.sslManagerService = null;
    }

    protected void setDeploymentAdmin(DeploymentAdmin deploymentAdmin) {
        this.deploymentAdmin = deploymentAdmin;
    }

    protected void unsetDeploymentAdmin(DeploymentAdmin deploymentAdmin) {
        this.deploymentAdmin = null;
    }

    public void setDataTransportService(DataTransportService dataTransportService) {
        this.dataTransportService = dataTransportService;
    }

    public void unsetDataTransportService(DataTransportService dataTransportService) {
        this.dataTransportService = null;
    }

    public void setSystemService(SystemService systemService) {
        this.systemService = systemService;
    }

    public void unsetSystemService(SystemService systemService) {
        this.systemService = null;
    }

    public void setDeploymentHookManager(DeploymentHookManager deploymentHookManager) {
        this.deploymentHookManager = deploymentHookManager;
    }

    public void unsetDeploymentHookManager(DeploymentHookManager deploymentHookManager) {
        this.deploymentHookManager = null;
    }

    public void setRequestHandlerRegistry(RequestHandlerRegistry requestHandlerRegistry) {
        try {
            requestHandlerRegistry.registerRequestHandler(APP_ID, this);
        } catch (KuraException e) {
            logger.info("Unable to register cloudlet {} in {}", APP_ID, requestHandlerRegistry.getClass().getName());
        }
    }

    public void unsetRequestHandlerRegistry(RequestHandlerRegistry requestHandlerRegistry) {
        try {
            requestHandlerRegistry.unregister(APP_ID);
        } catch (KuraException e) {
            logger.info("Unable to register cloudlet {} in {}", APP_ID, requestHandlerRegistry.getClass().getName());
        }
    }

    // ----------------------------------------------------------------
    //
    // Activation APIs
    //
    // ----------------------------------------------------------------

    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        logger.info("Cloud Deployment v2 is starting");
        this.bundleContext = componentContext.getBundleContext();

        CloudDeploymentHandlerOptions options = new CloudDeploymentHandlerOptions(
                CloudDeploymentHandlerV2.this.systemService.getProperties());

        String dpaConfPath = options.getDpaConfigurationFilePath();
        String packagesPath = options.getPackagesPath();
        String kuraDataDir = options.getKuraDataDir();

        installImplementation = new InstallImpl(CloudDeploymentHandlerV2.this, kuraDataDir);
        installImplementation.setPackagesPath(packagesPath);
        installImplementation.setDpaConfPath(dpaConfPath);
        installImplementation.setDeploymentAdmin(CloudDeploymentHandlerV2.this.deploymentAdmin);

        this.cloudPublisherTrackerCustomizer = new CloudNotificationPublisherTrackerCustomizer();
        initCloudPublisherTracking();

        persistedRequestServiceImpl.activate(properties);

        updated(properties);
    }

    protected void updated(Map<String, Object> properties) {
        this.componentOptions = new CloudDeploymentHandlerV2Options(properties);
        final Properties associations = new Properties();
        try {
            associations.load(new StringReader(this.componentOptions.getHookAssociations()));
        } catch (Exception e) {
            logger.warn("failed to parse hook associations from configuration", e);
        }
        this.deploymentHookManager.updateAssociations(associations);

        this.installVerificationDir = installImplementation.getVerificationDirectory();
    }

    protected void deactivate(ComponentContext componentContext) {
        logger.info("Bundle {} is deactivating!", APP_ID);
        if (this.downloaderFuture != null) {
            this.downloaderFuture.cancel(true);
        }

        if (this.installerFuture != null) {
            this.installerFuture.cancel(true);
        }

        if (nonNull(this.cloudPublisherTracker)) {
            this.cloudPublisherTracker.close();
        }

        this.bundleContext = null;
    }

    // ----------------------------------------------------------------
    //
    // Public methods
    //
    // ----------------------------------------------------------------

    public void publishMessage(DeploymentPackageOptions options, KuraPayload messagePayload, String messageType) {
        try {
            Map<String, Object> properties = new HashMap<>();
            properties.put(APP_ID_KEY, APP_ID);
            properties.put(MESSAGE_TYPE_KEY, messageType);
            properties.put(REQUESTOR_CLIENT_ID_KEY, options.getRequestClientId());

            KuraMessage message = new KuraMessage(messagePayload, properties);

            CloudNotificationPublisher notificationPublisher = options.getNotificationPublisher();
            if (notificationPublisher != null) {
                notificationPublisher.publish(message);
            }
        } catch (KuraException e) {
            logger.error("Error publishing response for command {}", messageType, e);
        }
    }

    // ----------------------------------------------------------------
    //
    // Protected methods
    //
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Override
    public KuraMessage doGet(RequestHandlerContext requestContext, KuraMessage reqMessage) throws KuraException {

        Object requestObject = reqMessage.getProperties().get(ARGS_KEY.value());
        List<String> resources;
        if (requestObject instanceof List) {
            resources = (List<String>) requestObject;
        } else {
            throw new KuraException(KuraErrorCode.BAD_REQUEST);
        }

        if (resources.isEmpty()) {
            logger.error("Bad request topic: {}", resources);
            logger.error("Expected one resource but found none");
            throw new KuraException(KuraErrorCode.BAD_REQUEST);
        }

        KuraPayload resPayload;
        if (resources.get(0).equals(RESOURCE_DOWNLOAD)) {
            resPayload = doGetDownload();
        } else if (resources.get(0).equals(RESOURCE_INSTALL)) {
            resPayload = doGetInstall();
        } else if (resources.get(0).equals(RESOURCE_PACKAGES)) {
            resPayload = doGetPackages();
        } else if (resources.get(0).equals(RESOURCE_BUNDLES)) {
            resPayload = doGetBundles();
        } else {
            logger.error("Bad request topic: {}", resources);
            logger.error("Cannot find resource with name: {}", resources.get(0));
            throw new KuraException(KuraErrorCode.NOT_FOUND);
        }

        return new KuraMessage(resPayload);
    }

    @SuppressWarnings("unchecked")
    @Override
    public KuraMessage doExec(RequestHandlerContext requestContext, KuraMessage reqMessage) throws KuraException {

        Object requestObject = reqMessage.getProperties().get(ARGS_KEY.value());
        List<String> resources;
        if (requestObject instanceof List) {
            resources = (List<String>) requestObject;
        } else {
            throw new KuraException(KuraErrorCode.BAD_REQUEST);
        }

        if (resources.isEmpty()) {
            logger.error("Bad request topic: {}", resources);
            logger.error("Expected one resource but found none");
            throw new KuraException(KuraErrorCode.BAD_REQUEST);
        }

        KuraPayload reqPayload = reqMessage.getPayload();
        KuraPayload resPayload;
        if (resources.get(0).equals(RESOURCE_DOWNLOAD)) {
            resPayload = doExecDownload(requestContext, reqPayload);
        } else if (resources.get(0).equals(RESOURCE_INSTALL)) {
            resPayload = doExecInstall(requestContext, reqPayload);
        } else if (resources.get(0).equals(RESOURCE_UNINSTALL)) {
            resPayload = doExecUninstall(requestContext, reqPayload);
        } else if (resources.get(0).equals(RESOURCE_START)) {
            String bundleId = resources.size() >= 2 ? resources.get(1) : null; // no checking is done before
            resPayload = doExecStartStopBundle(true, bundleId);
        } else if (resources.get(0).equals(RESOURCE_STOP)) {
            String bundleId = resources.size() >= 2 ? resources.get(1) : null; // no checking is done before
            resPayload = doExecStartStopBundle(false, bundleId);
        } else {
            logger.error("Bad request topic: {}", resources);
            logger.error("Cannot find resource with name: {}", resources.get(0));
            throw new KuraException(KuraErrorCode.NOT_FOUND);
        }
        return new KuraMessage(resPayload);
    }

    @SuppressWarnings("unchecked")
    @Override
    public KuraMessage doDel(RequestHandlerContext requestContext, KuraMessage reqMessage) throws KuraException {

        Object requestObject = reqMessage.getProperties().get(ARGS_KEY.value());
        List<String> resources;
        if (requestObject instanceof List) {
            resources = (List<String>) requestObject;
        } else {
            throw new KuraException(KuraErrorCode.BAD_REQUEST);
        }

        if (resources.isEmpty()) {
            logger.error("Bad request topic: {}", resources);
            logger.error("Expected one resource but found none");
            throw new KuraException(KuraErrorCode.BAD_REQUEST);
        }

        KuraPayload resPayload;
        if (resources.get(0).equals(RESOURCE_DOWNLOAD)) {
            resPayload = doDelDownload();
        } else {
            logger.error("Bad request topic: {}", resources);
            logger.error("Cannot find resource with name: {}", resources.get(0));
            throw new KuraException(KuraErrorCode.NOT_FOUND);
        }
        return new KuraMessage(resPayload);
    }

    protected DownloadImpl createDownloadImpl(final DeploymentPackageDownloadOptions options) {
        DownloadImpl downloadImplementation = new DownloadImpl(options, this);
        return downloadImplementation;
    }

    protected UninstallImpl createUninstallImpl() {
        return new UninstallImpl(this, this.deploymentAdmin);
    }

    protected File getDpDownloadFile(final DeploymentPackageInstallOptions options) throws IOException {
        return DownloadFileUtilities.getDpDownloadFile(options);
    }

    // ----------------------------------------------------------------
    //
    // Private methods
    //
    // ----------------------------------------------------------------

    private void initCloudPublisherTracking() {
        String filterString = String.format("(%s=%s)", Constants.OBJECTCLASS,
                CloudNotificationPublisher.class.getName());
        Filter filter = null;
        try {
            filter = this.bundleContext.createFilter(filterString);
        } catch (InvalidSyntaxException e) {
            logger.error("Filter setup exception ", e);
        }
        this.cloudPublisherTracker = new ServiceTracker<>(this.bundleContext, filter,
                this.cloudPublisherTrackerCustomizer);
        this.cloudPublisherTracker.open();
    }

    private KuraPayload doDelDownload() throws KuraException {

        try {
            DownloadCountingOutputStream downloadHelper = downloadImplementation.getDownloadHelper();
            if (downloadHelper != null) {
                downloadHelper.cancelDownload();
                downloadImplementation.deleteDownloadedFile();
            }
        } catch (Exception ex) {
            String errMsg = "Error cancelling download!";
            logger.warn(errMsg, ex);
            throw new KuraException(KuraErrorCode.PROCESS_EXECUTION_ERROR); // TODO:review exception code
        }

        return new KuraResponsePayload(KuraResponsePayload.RESPONSE_CODE_OK);
    }

    private void checkHook(DeploymentPackageInstallOptions options) {
        if (options.getRequestType() != null && options.getDeploymentHook() == null) {
            throw new IllegalStateException("No DeploymentHook is currently associated to request type "
                    + options.getRequestType() + ", aborting operation");
        }
    }

    private KuraPayload doExecDownload(RequestHandlerContext requestContext, KuraPayload request) throws KuraException {

        KuraResponsePayload response = new KuraResponsePayload(KuraResponsePayload.RESPONSE_CODE_OK);

        final DeploymentPackageDownloadOptions options;
        try {
            options = new DeploymentPackageDownloadOptions(request, this.deploymentHookManager,
                    this.componentOptions.getDownloadsDirectory(), persistedRequestServiceImpl,
                    cloudNotificationPublisherPid, cloudNotificationPublisher);
            options.setClientId(this.dataTransportService.getClientId());
        } catch (Exception ex) {
            logger.info("Malformed download request!");
            throw new KuraException(KuraErrorCode.BAD_REQUEST);
        }
        this.downloadOptions = options;

        try {
            checkHook(this.downloadOptions);
        } catch (Exception ex) {
            logger.warn(ex.getMessage());
            throw new KuraException(KuraErrorCode.BAD_REQUEST);
        }

        if (pendingPackageUrl != null) {
            logger.info("Another request seems for the same URL is pending: {}.", pendingPackageUrl);

            response.setResponseCode(KuraResponsePayload.RESPONSE_CODE_ERROR);
            response.setTimestamp(new Date());
            response.addMetric(METRIC_DOWNLOAD_STATUS, DownloadStatus.IN_PROGRESS.getStatusString());
            try {
                response.setBody("Another resource is already in download".getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
            }
            return response;
        }

        downloadImplementation = createDownloadImpl(options);

        boolean alreadyDownloaded = false;

        try {
            alreadyDownloaded = downloadImplementation.isAlreadyDownloaded();
        } catch (KuraException ex) {
            response.setResponseCode(KuraResponsePayload.RESPONSE_CODE_ERROR);
            response.setException(ex);
            response.setTimestamp(new Date());
            try {
                response.setBody("Error checking download status".getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
            }
            return response;
        }

        logger.info("About to download and install package at URL {}", options.getDeployUri());

        try {
            final DeploymentHook deploymentHook = options.getDeploymentHook();
            if (deploymentHook != null) {
                try {
                    deploymentHook.preDownload(options.getHookRequestContext(), options.getHookProperties());
                } catch (Exception e) {
                    logger.warn("DeploymentHook cancelled operation at preDownload phase");
                    throw e;
                }
            }

            pendingPackageUrl = options.getDeployUri();

            downloadImplementation.setSslManager(this.sslManagerService);
            downloadImplementation.setAlreadyDownloadedFlag(alreadyDownloaded);
            downloadImplementation.setVerificationDirectory(this.installVerificationDir);

            Map<String, String> requestProperties = requestContext.getContextProperties();
            String notificationPublisherPid = requestProperties.get(NOTIFICATION_PUBLISHER_PID.name());

            options.setNotificationPublisher(requestContext.getNotificationPublisher());
            options.setNotificationPublisherPid(notificationPublisherPid);

            logger.info("Downloading package from URL: {}", options.getDeployUri());

            this.downloaderFuture = executor.submit(new Runnable() {

                @Override
                public void run() {
                    try {

                        downloadImplementation.downloadDeploymentPackageInternal();
                    } catch (KuraException e) {
                        logger.warn("deployment package download failed", e);

                        try {
                            File dpFile = getDpDownloadFile(options);
                            if (dpFile != null) {
                                dpFile.delete();
                            }
                        } catch (IOException e1) {
                        }
                    } finally {
                        pendingPackageUrl = null;
                    }
                }
            });

        } catch (Exception e) {
            logger.error("Failed to download and install package at URL {}: {}", options.getDeployUri(), e);

            pendingPackageUrl = null;

            throw new KuraException(KuraErrorCode.PROCESS_EXECUTION_ERROR);
        }

        return response;
    }

    private KuraPayload doExecInstall(RequestHandlerContext requestContext, KuraPayload request) throws KuraException {
        final DeploymentPackageInstallOptions options;
        try {
            options = new DeploymentPackageInstallOptions(request, this.deploymentHookManager,
                    this.componentOptions.getDownloadsDirectory(), persistedRequestServiceImpl,
                    cloudNotificationPublisherPid, cloudNotificationPublisher);
            options.setClientId(this.dataTransportService.getClientId());
        } catch (Exception ex) {
            logger.error("Malformed install request!");
            throw new KuraException(KuraErrorCode.BAD_REQUEST);
        }

        this.installOptions = options;

        try {
            checkHook(this.installOptions);
        } catch (Exception ex) {
            logger.warn(ex.getMessage());
            throw new KuraException(KuraErrorCode.BAD_REQUEST);
        }

        boolean alreadyDownloaded = false;

        try {
            alreadyDownloaded = downloadImplementation.isAlreadyDownloaded();
        } catch (KuraException ex) {
            throw new KuraException(KuraErrorCode.BAD_REQUEST);
        }

        if (alreadyDownloaded && !this.isInstalling) {
            // Check if file exists

            try {

                // if yes, install

                final DeploymentHook hook = options.getDeploymentHook();
                if (hook != null) {
                    try {
                        hook.postDownload(options.getHookRequestContext(), options.getHookProperties());
                    } catch (Exception e) {
                        logger.warn("DeploymentHook cancelled operation at postDownload phase");
                        throw e;
                    }
                }

                this.isInstalling = true;
                final File dpFile = getDpDownloadFile(options);

                installImplementation.setOptions(options);

                Map<String, String> requestProperties = requestContext.getContextProperties();
                String notificationPublisherPid = requestProperties.get(NOTIFICATION_PUBLISHER_PID.name());

                options.setNotificationPublisher(requestContext.getNotificationPublisher());
                options.setNotificationPublisherPid(notificationPublisherPid);

                this.installerFuture = executor.submit(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            installDownloadedFile(dpFile, CloudDeploymentHandlerV2.this.installOptions);
                        } catch (KuraException e) {
                            logger.error("Impossible to send an exception message to the cloud platform");
                            if (dpFile != null) {
                                dpFile.delete();
                            }
                        } finally {
                            CloudDeploymentHandlerV2.this.installOptions = null;
                            CloudDeploymentHandlerV2.this.isInstalling = false;
                        }
                    }
                });
            } catch (Exception e) {
                throw new KuraException(KuraErrorCode.BAD_REQUEST);
            }
        } else {
            throw new KuraException(KuraErrorCode.BAD_REQUEST);
        }
        return new KuraResponsePayload(KuraResponsePayload.RESPONSE_CODE_OK);
    }

    private KuraPayload doExecUninstall(RequestHandlerContext requestContext, KuraPayload request)
            throws KuraException {
        final DeploymentPackageUninstallOptions options;
        try {
            options = new DeploymentPackageUninstallOptions(request);
            options.setClientId(this.dataTransportService.getClientId());
        } catch (Exception ex) {
            logger.error("Malformed uninstall request!");
            throw new KuraException(KuraErrorCode.BAD_REQUEST);
        }

        final String packageName = options.getDpName();

        //
        // We only allow one request at a time
        if (!this.isInstalling && this.pendingUninstPackageName != null) {
            logger.info("Another request seems still pending: {}. Checking if stale...", this.pendingUninstPackageName);

            throw new KuraException(KuraErrorCode.BAD_REQUEST);
        } else {
            logger.info("About to uninstall package {}", packageName);

            try {
                this.isInstalling = true;
                this.pendingUninstPackageName = packageName;
                uninstallImplementation = createUninstallImpl();

                Map<String, String> requestProperties = requestContext.getContextProperties();
                String notificationPublisherPid = requestProperties.get(NOTIFICATION_PUBLISHER_PID.name());

                options.setNotificationPublisher(requestContext.getNotificationPublisher());
                options.setNotificationPublisherPid(notificationPublisherPid);

                logger.info("Uninstalling package...");
                this.installerFuture = executor.submit(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            uninstallImplementation.uninstaller(options, packageName);
                        } catch (Exception e) {
                            try {
                                uninstallImplementation.uninstallFailedAsync(options, packageName, e);
                            } catch (KuraException e1) {

                            }
                        } finally {
                            CloudDeploymentHandlerV2.this.installOptions = null;
                            CloudDeploymentHandlerV2.this.isInstalling = false;
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("Failed to uninstall package {}: {}", packageName, e);

                throw new KuraException(KuraErrorCode.BAD_REQUEST);
            } finally {
                this.isInstalling = false;
                this.pendingUninstPackageName = null;
            }
        }
        return new KuraResponsePayload(KuraResponsePayload.RESPONSE_CODE_OK);
    }

    private KuraPayload doExecStartStopBundle(boolean start, String bundleId) throws KuraException {
        if (bundleId == null) {
            logger.info("EXEC start/stop bundle: null bundle ID");

            throw new KuraException(KuraErrorCode.BAD_REQUEST);
        } else {
            Long id = null;
            try {
                id = Long.valueOf(bundleId);
            } catch (NumberFormatException e) {

                logger.error("EXEC start/stop bundle: bad bundle ID format: {}", e);
                throw new KuraException(KuraErrorCode.BAD_REQUEST);
            }

            if (id != null) {

                logger.info("Executing command {}", start ? RESOURCE_START : RESOURCE_STOP);

                Bundle bundle = this.bundleContext.getBundle(id);
                if (bundle == null) {
                    logger.error("Bundle ID {} not found", id);
                    throw new KuraException(KuraErrorCode.BAD_REQUEST);
                } else {
                    try {
                        if (start) {
                            bundle.start();
                        } else {
                            bundle.stop();
                        }
                        logger.info("{} bundle ID {} ({})",
                                new Object[] { start ? "Started" : "Stopped", id, bundle.getSymbolicName() });
                    } catch (BundleException e) {
                        logger.error("Failed to {} bundle {}: {}", new Object[] { start ? "start" : "stop", id, e });
                        throw new KuraException(KuraErrorCode.BAD_REQUEST);
                    }
                }
            } // TODO:review this side
        }
        return new KuraResponsePayload(KuraResponsePayload.RESPONSE_CODE_OK);
    }

    private KuraPayload doGetInstall() {
        KuraResponsePayload respPayload = new KuraResponsePayload(KuraResponsePayload.RESPONSE_CODE_OK);
        if (this.isInstalling) {
            installImplementation.installInProgressSyncMessage(respPayload);
        } else {
            installImplementation.installIdleSyncMessage(respPayload);
        }
        return respPayload;
    }

    private KuraPayload doGetDownload() {
        KuraResponsePayload respPayload = new KuraResponsePayload(KuraResponsePayload.RESPONSE_CODE_OK);
        if (pendingPackageUrl != null) { // A download is pending
            DownloadCountingOutputStream downloadHelper = downloadImplementation.getDownloadHelper();
            DownloadImpl.downloadInProgressSyncMessage(respPayload, downloadHelper, this.downloadOptions);
        } else { // No pending downloads
            DownloadImpl.downloadAlreadyDoneSyncMessage(respPayload); // is it right? Do we remove the last object
        }

        return respPayload;
    }

    private KuraPayload doGetPackages() {
        DeploymentPackage[] dps = this.deploymentAdmin.listDeploymentPackages();
        XmlDeploymentPackages xdps = new XmlDeploymentPackages();
        XmlDeploymentPackage[] axdp = new XmlDeploymentPackage[dps.length];

        for (int i = 0; i < dps.length; i++) {
            DeploymentPackage dp = dps[i];

            XmlDeploymentPackage xdp = new XmlDeploymentPackage();
            xdp.setName(dp.getName());
            xdp.setVersion(dp.getVersion().toString());

            BundleInfo[] bis = dp.getBundleInfos();
            XmlBundleInfo[] axbi = new XmlBundleInfo[bis.length];

            for (int j = 0; j < bis.length; j++) {

                BundleInfo bi = bis[j];
                XmlBundleInfo xbi = new XmlBundleInfo();
                xbi.setName(bi.getSymbolicName());
                xbi.setVersion(bi.getVersion().toString());

                axbi[j] = xbi;
            }

            xdp.setBundleInfos(axbi);

            axdp[i] = xdp;
        }

        xdps.setDeploymentPackages(axdp);

        KuraResponsePayload respPayload = new KuraResponsePayload(KuraResponsePayload.RESPONSE_CODE_OK);
        try {
            String s = marshal(xdps);
            respPayload.setTimestamp(new Date());
            respPayload.setBody(s.getBytes("UTF-8"));
        } catch (Exception e) {
            logger.error("Error getting resource {}: {}", RESOURCE_PACKAGES, e);
        }
        return respPayload;
    }

    private KuraPayload doGetBundles() {
        Bundle[] bundles = this.bundleContext.getBundles();
        XmlBundles xmlBundles = new XmlBundles();
        XmlBundle[] axb = new XmlBundle[bundles.length];

        for (int i = 0; i < bundles.length; i++) {

            Bundle bundle = bundles[i];
            XmlBundle xmlBundle = new XmlBundle();

            xmlBundle.setName(bundle.getSymbolicName());
            xmlBundle.setVersion(bundle.getVersion().toString());
            xmlBundle.setId(bundle.getBundleId());

            int state = bundle.getState();

            switch (state) {
            case Bundle.UNINSTALLED:
                xmlBundle.setState("UNINSTALLED");
                break;

            case Bundle.INSTALLED:
                xmlBundle.setState("INSTALLED");
                break;

            case Bundle.RESOLVED:
                xmlBundle.setState("RESOLVED");
                break;

            case Bundle.STARTING:
                xmlBundle.setState("STARTING");
                break;

            case Bundle.STOPPING:
                xmlBundle.setState("STOPPING");
                break;

            case Bundle.ACTIVE:
                xmlBundle.setState("ACTIVE");
                break;

            default:
                xmlBundle.setState(String.valueOf(state));
            }

            axb[i] = xmlBundle;
        }

        xmlBundles.setBundles(axb);

        KuraResponsePayload respPayload = new KuraResponsePayload(KuraResponsePayload.RESPONSE_CODE_OK);
        try {
            String s = marshal(xmlBundles);
            respPayload.setTimestamp(new Date());
            respPayload.setBody(s.getBytes("UTF-8"));
        } catch (Exception e) {
            logger.error("Error getting resource {}", RESOURCE_BUNDLES, e);
        }
        return respPayload;
    }

    public void installDownloadedFile(File dpFile, DeploymentPackageInstallOptions options) throws KuraException {
        try {
            if (options.getSystemUpdate()) {
                installImplementation.installSh(options, dpFile);
            } else {
                installImplementation.installDp(options, dpFile);
            }
            final DeploymentHook hook = options.getDeploymentHook();
            if (hook != null) {
                hook.postInstall(options.getHookRequestContext(), options.getHookProperties());
            }
        } catch (Exception e) {
            logger.info("Install exception");
            installImplementation.installFailedAsync(options, dpFile.getName(), e);
        }
    }

    private ServiceReference<Marshaller>[] getXmlMarshallers() {
        String filterString = String.format("(&(kura.service.pid=%s))",
                "org.eclipse.kura.xml.marshaller.unmarshaller.provider");
        return ServiceUtil.getServiceReferences(this.bundleContext, Marshaller.class, filterString);
    }

    private void ungetServiceReferences(final ServiceReference<?>[] refs) {
        ServiceUtil.ungetServiceReferences(this.bundleContext, refs);
    }

    protected String marshal(Object object) {
        String result = null;
        ServiceReference<Marshaller>[] marshallerSRs = getXmlMarshallers();
        try {
            for (final ServiceReference<Marshaller> marshallerSR : marshallerSRs) {
                Marshaller marshaller = this.bundleContext.getService(marshallerSR);
                result = marshaller.marshal(object);
                if (result != null) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to marshal configuration.");
        } finally {
            ungetServiceReferences(marshallerSRs);
        }
        return result;
    }

    @Override
    public Optional<Request> getRequest(String id) {
        return persistedRequestServiceImpl.getRequest(id);
    }

    @Override
    public List<Request> getRequests() {
        return persistedRequestServiceImpl.getRequests();
    }

    @Override
    public void registerEventStream(RequestEventStream stream) {
        persistedRequestServiceImpl.registerEventStream(stream);
    }

    @Override
    public void unregisterEventStream(RequestEventStream stream) {
        persistedRequestServiceImpl.unregisterEventStream(stream);
    }
}
