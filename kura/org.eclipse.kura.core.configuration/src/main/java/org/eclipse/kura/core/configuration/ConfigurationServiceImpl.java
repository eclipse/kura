/*******************************************************************************
 * Copyright (c) 2011, 2022 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Eurotech
 *  Red Hat Inc
 *******************************************************************************/
package org.eclipse.kura.core.configuration;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.KuraPartialSuccessException;
import org.eclipse.kura.configuration.ComponentConfiguration;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.configuration.ConfigurationService;
import org.eclipse.kura.configuration.Password;
import org.eclipse.kura.configuration.SelfConfiguringComponent;
import org.eclipse.kura.configuration.metatype.AD;
import org.eclipse.kura.configuration.metatype.OCD;
import org.eclipse.kura.configuration.metatype.OCDService;
import org.eclipse.kura.configuration.metatype.Scalar;
import org.eclipse.kura.core.configuration.metatype.Tocd;
import org.eclipse.kura.core.configuration.upgrade.ConfigurationUpgrade;
import org.eclipse.kura.core.configuration.util.CollectionsUtil;
import org.eclipse.kura.core.configuration.util.ComponentUtil;
import org.eclipse.kura.core.configuration.util.StringUtil;
import org.eclipse.kura.crypto.CryptoService;
import org.eclipse.kura.marshalling.Marshaller;
import org.eclipse.kura.marshalling.Unmarshaller;
import org.eclipse.kura.system.SystemService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ComponentDescriptionDTO;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.MetaTypeService;
import org.osgi.service.metatype.ObjectClassDefinition;
import org.osgi.util.tracker.BundleTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of ConfigurationService.
 */
public class ConfigurationServiceImpl implements ConfigurationService, OCDService {

    private static final String GETTING_CONFIGURATION_ERROR = "Error getting Configuration for component: {}. Ignoring it.";

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationServiceImpl.class);

    private ComponentContext ctx;
    private BundleContext bundleContext;

    private BundleTracker<Bundle> bundleTracker;

    @SuppressWarnings("unused")
    private MetaTypeService metaTypeService;
    private ConfigurationAdmin configurationAdmin;
    private SystemService systemService;
    private CryptoService cryptoService;
    private ServiceComponentRuntime scrService;
    private Marshaller xmlMarshaller;
    private Unmarshaller xmlUnmarshaller;
    protected EventAdmin eventAdmin;

    // contains all the PIDs (aka kura.service.pid) - both of configurable and self configuring components
    private final Set<String> allActivatedPids;

    // contains the self configuring components ONLY!
    private final Set<String> activatedSelfConfigComponents;

    // maps either service.pid or service.factoryPid to the related OCD
    private final Map<String, Tocd> ocds;

    // contains the service.factoryPid of all Factory Components
    private final Set<TrackedComponentFactory> factoryPids;

    // maps the kura.service.pid to the associated service.factoryPid
    private final Map<String, String> factoryPidByPid;

    // contains all the pids (kura.service.pid) which have to be deleted
    private final Set<String> pendingDeletePids;

    // maps the kura.service.pid to the associated service.pid
    private final Map<String, String> servicePidByPid;

    // ----------------------------------------------------------------
    //
    // Dependencies
    //
    // ----------------------------------------------------------------

    public void setConfigurationAdmin(ConfigurationAdmin configAdmin) {
        this.configurationAdmin = configAdmin;
    }

    public void unsetConfigurationAdmin(ConfigurationAdmin configAdmin) {
        this.configurationAdmin = null;
    }

    public void setMetaTypeService(MetaTypeService metaTypeService) {
        this.metaTypeService = metaTypeService;
    }

    public void unsetMetaTypeService(MetaTypeService metaTypeService) {
        this.metaTypeService = null;
    }

    public void setSystemService(SystemService systemService) {
        this.systemService = systemService;
    }

    public void unsetSystemService(SystemService systemService) {
        this.systemService = null;
    }

    public void setCryptoService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    public void unsetCryptoService(CryptoService cryptoService) {
        this.cryptoService = null;
    }

    public void setScrService(ServiceComponentRuntime scrService) {
        this.scrService = scrService;
    }

    public void unsetScrService(ServiceComponentRuntime scrService) {
        this.scrService = null;
    }

    public void setXmlMarshaller(final Marshaller marshaller) {
        this.xmlMarshaller = marshaller;
    }

    public void unsetXmlMarshaller(final Marshaller marshaller) {
        this.xmlMarshaller = null;
    }

    public void setXmlUnmarshaller(final Unmarshaller unmarshaller) {
        this.xmlUnmarshaller = unmarshaller;
    }

    public void unsetXmlUnmarshaller(final Unmarshaller unmarshaller) {
        this.xmlUnmarshaller = null;
    }

    public void setEventAdmin(EventAdmin eventAdmin) {
        this.eventAdmin = eventAdmin;
    }

    public void unsetEventAdmin(EventAdmin eventAdmin) {
        if (this.eventAdmin == eventAdmin) {
            this.eventAdmin = null;
        }
    }

    public ConfigurationServiceImpl() {
        this.allActivatedPids = new HashSet<>();
        this.activatedSelfConfigComponents = new HashSet<>();
        this.pendingDeletePids = new HashSet<>();
        this.ocds = new HashMap<>();
        this.factoryPids = new HashSet<>();
        this.factoryPidByPid = new HashMap<>();
        this.servicePidByPid = new HashMap<>();
    }

    // ----------------------------------------------------------------
    //
    // Activation APIs
    //
    // ----------------------------------------------------------------

    protected void activate(ComponentContext componentContext) throws InvalidSyntaxException {
        logger.info("activate...");

        // save the bundle context
        this.ctx = componentContext;
        this.bundleContext = componentContext.getBundleContext();

        // Load the latest snapshot and push it to ConfigurationAdmin
        try {
            loadLatestSnapshotInConfigAdmin();
        } catch (Exception e) {
            throw new ComponentException("Error loading latest snapshot", e);
        }

        this.bundleTracker = new ComponentMetaTypeBundleTracker(this.ctx.getBundleContext(), this);
        this.bundleTracker.open();
    }

    protected void addConfigurableComponent(final ServiceReference<ConfigurableComponent> reference) {

        final String servicePid = makeString(reference.getProperty(Constants.SERVICE_PID));

        if (servicePid == null) {
            return;
        }

        final String kuraPid = makeString(reference.getProperty(ConfigurationService.KURA_SERVICE_PID));
        final String factoryPid = makeString(reference.getProperty(ConfigurationAdmin.SERVICE_FACTORYPID));

        registerComponentConfiguration(kuraPid, servicePid, factoryPid);
    }

    protected void removeConfigurableComponent(final ServiceReference<ConfigurableComponent> reference) {

        final String servicePid = makeString(reference.getProperty(Constants.SERVICE_PID));

        if (servicePid == null) {
            return;
        }

        final String kuraPid = makeString(reference.getProperty(ConfigurationService.KURA_SERVICE_PID));

        deleteConfigurationFromConfigAdminInternal(kuraPid);
        unregisterComponentConfiguration(kuraPid);
    }

    protected void addSelfConfiguringComponent(final ServiceReference<SelfConfiguringComponent> reference) {

        final String servicePid = makeString(reference.getProperty(Constants.SERVICE_PID));

        if (servicePid == null) {
            return;
        }

        final String kuraPid = makeString(reference.getProperty(ConfigurationService.KURA_SERVICE_PID));

        registerSelfConfiguringComponent(kuraPid, servicePid);
    }

    protected void removeSelfConfiguringComponent(final ServiceReference<SelfConfiguringComponent> reference) {

        final String servicePid = makeString(reference.getProperty(Constants.SERVICE_PID));

        if (servicePid == null) {
            return;
        }

        final String kuraPid = makeString(reference.getProperty(ConfigurationService.KURA_SERVICE_PID));

        deleteConfigurationFromConfigAdminInternal(kuraPid);
        unregisterComponentConfiguration(kuraPid);
    }

    protected void deactivate(ComponentContext componentContext) {
        logger.info("deactivate...");

        if (this.bundleTracker != null) {
            this.bundleTracker.close();
            this.bundleTracker = null;
        }
    }

    // ----------------------------------------------------------------
    //
    // Service APIs
    //
    // ----------------------------------------------------------------

    @Override
    public Set<String> getConfigurableComponentPids() {
        if (this.allActivatedPids.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(this.allActivatedPids);
    }

    // Don't perform internal calls to this method
    @Override
    public List<ComponentConfiguration> getComponentConfigurations() throws KuraException {
        return getComponentConfigurationsInternal();
    }

    @Override
    public List<ComponentConfiguration> getComponentConfigurations(final Filter filter) throws KuraException {

        if (filter == null) {
            return getComponentConfigurationsInternal();
        }

        try {
            final ServiceReference<?>[] refs = this.bundleContext.getAllServiceReferences(null, null);
            final List<ComponentConfiguration> result = new ArrayList<>(refs.length);

            for (final ServiceReference<?> ref : refs) {

                if (!filter.match(ref)) {
                    continue;
                }

                final Object kuraServicePid = ref.getProperty(KURA_SERVICE_PID);

                if (kuraServicePid instanceof String) {
                    final ComponentConfiguration config = getComponentConfigurationInternal((String) kuraServicePid);

                    if (config != null) {
                        result.add(config);
                    }
                }
            }

            return result;
        } catch (final Exception e) {
            throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, e);
        }

    }

    // Don't perform internal calls to this method
    @Override
    public ComponentConfiguration getComponentConfiguration(String pid) throws KuraException {
        ComponentConfiguration tempConfig = getComponentConfigurationInternal(pid);
        if (tempConfig != null && tempConfig.getConfigurationProperties() != null) {
            decryptConfigurationProperties(tempConfig.getConfigurationProperties());
        }
        return tempConfig;
    }

    @Override
    public synchronized void updateConfiguration(String pidToUpdate, Map<String, Object> propertiesToUpdate)
            throws KuraException { // don't call this method internally
        updateConfiguration(pidToUpdate, propertiesToUpdate, true);
    }

    @Override
    public synchronized void updateConfiguration(String pidToUpdate, Map<String, Object> propertiesToUpdate,
            boolean takeSnapshot) throws KuraException { // don't call this method internally
        List<ComponentConfiguration> configs = new ArrayList<>();
        ComponentConfigurationImpl cci = new ComponentConfigurationImpl(pidToUpdate, null, propertiesToUpdate);
        configs.add(cci);
        updateConfigurations(configs, takeSnapshot);
    }

    // Don't perform internal calls to this method
    @Override
    public synchronized void updateConfigurations(List<ComponentConfiguration> configsToUpdate) throws KuraException {
        updateConfigurations(configsToUpdate, true);
    }

    @Override
    public synchronized void updateConfigurations(List<ComponentConfiguration> configsToUpdate, boolean takeSnapshot)
            throws KuraException { // don't call this method internally
        for (ComponentConfiguration config : configsToUpdate) {
            if (config != null) {
                encryptConfigurationProperties(config.getConfigurationProperties());
            }
        }

        // only encrypted properties are passed to internal methods
        updateConfigurationsInternal(configsToUpdate, takeSnapshot);
    }

    // ----------------------------------------------------------------
    //
    // Service APIs: Factory Management
    //
    // ----------------------------------------------------------------
    @Override
    public Set<String> getFactoryComponentPids() {
        return Collections.unmodifiableSet(
                this.factoryPids.stream().map(TrackedComponentFactory::getFactoryPid).collect(Collectors.toSet()));
    }

    @Override
    public ComponentConfiguration getDefaultComponentConfiguration(String pid) throws KuraException {
        ComponentConfiguration tempConfig = getDefaultComponentConfigurationInternal(pid);
        if (tempConfig != null && tempConfig.getConfigurationProperties() != null) {
            decryptConfigurationProperties(tempConfig.getConfigurationProperties());
        }
        return tempConfig;
    }

    @Override
    public synchronized void createFactoryConfiguration(String factoryPid, String pid, Map<String, Object> properties,
            boolean takeSnapshot) throws KuraException {
        if (pid == null) {
            throw new KuraException(KuraErrorCode.INVALID_PARAMETER, "pid cannot be null");
        } else if (this.servicePidByPid.containsKey(pid) || this.allActivatedPids.contains(pid)) {
            throw new KuraException(KuraErrorCode.INVALID_PARAMETER, "pid " + pid + " already exists");
        }

        try {
            // Second argument in createFactoryConfiguration is a bundle location. If left null the new bundle location
            // will be bound to the location of the first bundle that registers a Managed Service Factory with a
            // corresponding PID
            logger.info("Creating new configuration for factory pid {} and pid {}", factoryPid, pid);
            String servicePid = this.configurationAdmin.createFactoryConfiguration(factoryPid, null).getPid();

            logger.info("Updating newly created configuration for pid {}", pid);

            Map<String, Object> mergedProperties = new HashMap<>();
            if (properties != null) {
                mergedProperties.putAll(properties);
            }

            OCD ocd = this.ocds.get(factoryPid);
            mergeWithDefaults(ocd, mergedProperties);

            mergedProperties.put(ConfigurationService.KURA_SERVICE_PID, pid);

            Dictionary<String, Object> dict = CollectionsUtil.mapToDictionary(mergedProperties);
            Configuration config = this.configurationAdmin.getConfiguration(servicePid, "?");
            config.update(dict);

            registerComponentConfiguration(pid, servicePid, factoryPid);

            this.pendingDeletePids.remove(pid);

            if (takeSnapshot) {
                snapshot();
            }
        } catch (IOException e) {
            throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, e,
                    "Cannot create component instance for factory " + factoryPid);
        }
    }

    @Override
    public synchronized void deleteFactoryConfiguration(String pid, boolean takeSnapshot) throws KuraException {
        if (pid == null) {
            throw new KuraException(KuraErrorCode.INVALID_PARAMETER, "pid cannot be null");
        }

        try {
            final Configuration[] configurations = this.configurationAdmin.listConfigurations(null);

            if (configurations == null) {
                logger.warn("ConfigurationAdmin has no configurations");
                return;
            }

            final Optional<Configuration> config = Arrays.stream(configurations).filter(c -> {
                final Object kuraServicePid = c.getProperties().get(KURA_SERVICE_PID);
                final String factoryPid = c.getFactoryPid();
                return pid.equals(kuraServicePid) && factoryPid != null;
            }).findAny();

            if (!config.isPresent()) {
                logger.warn("The component with kura.service.pid {} does not exist or it is not a Factory Component",
                        pid);
                return;
            }

            logger.info("Deleting factory configuration for component with pid {}...", pid);

            config.get().delete();

            unregisterComponentConfiguration(pid);

            this.pendingDeletePids.add(pid);

            if (takeSnapshot) {
                snapshot();
            }
            logger.info("Deleting factory configuration for component with pid {}...done", pid);
        } catch (Exception e) {
            throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, e, "Cannot delete component instance " + pid);
        }
    }

    // ----------------------------------------------------------------
    //
    // Service APIs: Snapshot Management
    //
    // ----------------------------------------------------------------

    @Override
    public long snapshot() throws KuraException {
        logger.info("Writing snapshot - Getting component configurations...");

        List<ComponentConfiguration> configs = buildCurrentConfiguration(null);

        return saveSnapshot(configs);
    }

    @Override
    public long rollback() throws KuraException {
        // get the second-last most recent snapshot
        // and rollback to that one.
        Set<Long> ids = getSnapshots();
        if (ids.size() < 2) {
            throw new KuraException(KuraErrorCode.CONFIGURATION_SNAPSHOT_NOT_FOUND, null, "No Snapshot Available");
        }

        // rollback to the second last snapshot
        Long[] snapshots = ids.toArray(new Long[] {});
        Long id = snapshots[ids.size() - 2];

        rollback(id);
        return id;
    }

    @Override
    public synchronized void rollback(long id) throws KuraException {
        // load the snapshot we need to rollback to
        XmlComponentConfigurations xmlConfigs = loadEncryptedSnapshotFileContent(id);

        //
        // restore configuration
        logger.info("Rolling back to snapshot {}...", id);

        Set<String> snapshotPids = new HashSet<>();
        boolean snapshotOnConfirmation = false;
        List<Throwable> causes = new ArrayList<>();
        List<ComponentConfiguration> configs = xmlConfigs.getConfigurations();

        // remove all existing factory configurations
        for (String pid : new ArrayList<>(this.factoryPidByPid.keySet())) {
            try {
                deleteFactoryConfiguration(pid, false);
            } catch (Exception e) {
                logger.warn("Failed to remove factory configuration for pid: " + pid, e);
                causes.add(e);
            }
        }

        // create all factory configurations in snapshot
        final Stream<ComponentConfiguration> factoryConfigurationsInSnapshot = configs.stream()
                .filter(config -> config.getPid() != null
                        && config.getConfigurationProperties().containsKey(ConfigurationAdmin.SERVICE_FACTORYPID));

        factoryConfigurationsInSnapshot.forEach(config -> {
            final String pid = config.getPid();
            final Map<String, Object> properties = config.getConfigurationProperties();
            final String factoryPid = properties.get(ConfigurationAdmin.SERVICE_FACTORYPID).toString();
            try {
                createFactoryConfiguration(factoryPid, pid, properties, false);
            } catch (Exception e) {
                logger.warn("Error during rollback for component " + pid, e);
                causes.add(e);
            }
        });

        for (ComponentConfiguration config : configs) {
            if (config != null) {
                try {
                    rollbackConfigurationInternal(config.getPid(), config.getConfigurationProperties(),
                            snapshotOnConfirmation);
                } catch (Exception e) {
                    logger.warn("Error during rollback for component " + config.getPid(), e);
                    causes.add(e);
                }
                // Track the pid of the component
                snapshotPids.add(config.getPid());
            }
        }

        // rollback to the default configuration for those configurable
        // components
        // whose configuration is not present in the snapshot
        Set<String> pids = new HashSet<>(this.allActivatedPids);
        pids.removeAll(snapshotPids);

        for (String pid : pids) {
            logger.info("Rolling back to default configuration for component pid: '{}'", pid);
            try {
                rollbackConfigurationInternal(pid, Collections.emptyMap(), snapshotOnConfirmation);
            } catch (Exception e) {
                logger.warn("Error during rollback for component " + pid, e);
                causes.add(e);
            }
        }

        if (!causes.isEmpty()) {
            throw new KuraPartialSuccessException("Rollback", causes);
        }

        // Do not call snapshot() here because it gets the configurations of
        // SelfConfiguringComponents
        // using SelfConfiguringComponent.getConfiguration() and the
        // configuration returned
        // might be the old one not the one just loaded from the snapshot and
        // updated through
        // the Configuration Admin. Instead just make a copy of the snapshot.
        saveSnapshot(configs);
    }

    @Override
    public Set<Long> getSnapshots() throws KuraException {
        return getSnapshotsInternal();
    }

    @Override
    public List<ComponentConfiguration> getSnapshot(long sid) throws KuraException {
        List<ComponentConfiguration> returnConfigs = new ArrayList<>();

        XmlComponentConfigurations xmlConfigs = loadEncryptedSnapshotFileContent(sid);
        if (xmlConfigs != null) {
            List<ComponentConfiguration> configs = xmlConfigs.getConfigurations();
            for (ComponentConfiguration config : configs) {
                if (config != null) {
                    try {
                        decryptConfigurationProperties(config.getConfigurationProperties());
                    } catch (Throwable t) {
                        logger.warn("Error during snapshot password decryption");
                    }
                }
            }

            returnConfigs.addAll(xmlConfigs.getConfigurations());
        }

        return returnConfigs;
    }

    // ----------------------------------------------------------------
    //
    // Package APIs
    //
    // ----------------------------------------------------------------
    synchronized void registerComponentOCD(String metatypePid, Tocd ocd, boolean isFactory, final Bundle provider)
            throws KuraException {
        // metatypePid is either the 'pid' or 'factoryPid' attribute of the MetaType Designate element
        // 'pid' matches a service.pid, not a kura.service.pid
        logger.info("Registering metatype pid: {} ...", metatypePid);

        this.ocds.put(metatypePid, ocd);

        if (isFactory) {
            registerFactoryComponentOCD(metatypePid, ocd, provider);
        } else {
            try {
                updateWithDefaultConfiguration(metatypePid, ocd);
            } catch (IOException e) {
                throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
            }
        }
    }

    synchronized void onBundleRemoved(final Bundle bundle) {
        this.factoryPids.removeIf(factory -> {
            final Bundle provider = factory.getProviderBundle();
            return provider.getSymbolicName().equals(bundle.getSymbolicName())
                    && provider.getVersion().equals(bundle.getVersion());
        });
    }

    synchronized void registerComponentConfiguration(final String pid, final String servicePid,
            final String factoryPid) {
        if (pid == null || servicePid == null) {
            logger.warn("Either PID (kura.service.pid) {} or Service PID (service.pid) {} is null", pid, servicePid);
            return;
        }

        if (!this.allActivatedPids.contains(pid)) {
            // register the component instance
            logger.info("Registering ConfigurableComponent - {}....", pid);
            this.servicePidByPid.put(pid, servicePid);
            if (factoryPid != null) {
                this.factoryPidByPid.put(pid, factoryPid);
                Tocd factoryOCD = this.ocds.get(factoryPid);
                if (factoryOCD != null) {
                    try {
                        updateWithDefaultConfiguration(pid, factoryOCD);
                    } catch (KuraException e) {
                        logger.info("Error seeding updated configuration for pid: {}", pid);
                    } catch (IOException e) {
                        logger.info("Error seeding updated configuration for pid: {}", pid);
                    }
                }
            }
            this.allActivatedPids.add(pid);
            logger.info("Registering ConfigurableComponent - {}....Done", pid);
        }
    }

    synchronized void registerSelfConfiguringComponent(final String pid, final String servicePid) {
        if (pid == null) {
            logger.warn("PID (kura.service.pid) is null");
            return;
        }
        logger.info("Registering SelfConfiguringComponent - {}....", pid);
        if (!this.allActivatedPids.contains(pid)) {
            this.allActivatedPids.add(pid);
        }
        if (!this.activatedSelfConfigComponents.contains(pid)) {
            this.servicePidByPid.put(pid, servicePid);
            this.activatedSelfConfigComponents.add(pid);
        }
        logger.info("Registering SelfConfiguringComponent - {}....Done", pid);
    }

    synchronized void unregisterComponentConfiguration(String pid) {
        if (pid == null) {
            logger.warn("pid is null");
            return;
        }
        logger.info("Removing component configuration for pid {}", pid);
        this.servicePidByPid.remove(pid);
        this.factoryPidByPid.remove(pid);
        this.activatedSelfConfigComponents.remove(pid);
        this.allActivatedPids.remove(pid);
    }

    boolean mergeWithDefaults(OCD ocd, Map<String, Object> properties) throws KuraException {
        boolean changed = false;
        Set<String> keys = properties.keySet();

        Map<String, Object> defaults = getDefaultProperties(ocd);
        Set<String> defaultsKeys = defaults.keySet();

        defaultsKeys.removeAll(keys);
        if (!defaultsKeys.isEmpty()) {

            changed = true;
            logger.info("Merging configuration for pid: {}", ocd.getId());
            for (String key : defaultsKeys) {

                Object value = defaults.get(key);
                properties.put(key, value);
                logger.debug("Merged configuration properties with property with name: {} and default value {}", key,
                        value);
            }
        }
        return changed;
    }

    Map<String, Object> getDefaultProperties(OCD ocd) throws KuraException {
        return ComponentUtil.getDefaultProperties(ocd, this.ctx);
    }

    void decryptConfigurationProperties(Map<String, Object> configProperties) {
        for (Entry<String, Object> property : configProperties.entrySet()) {
            Object configValue = property.getValue();

            if (configValue instanceof Password || configValue instanceof Password[]) {
                try {
                    Object decryptedValue = decryptPasswordProperties(configValue);
                    configProperties.put(property.getKey(), decryptedValue);
                } catch (Exception e) {
                }
            }
        }
    }

    private Object decryptPasswordProperties(Object encryptedValue) throws KuraException {
        Object decryptedValue = null;
        if (encryptedValue instanceof Password) {
            decryptedValue = decryptPassword((Password) encryptedValue);
        } else if (encryptedValue instanceof Password[]) {
            Password[] encryptedPasswords = (Password[]) encryptedValue;
            Password[] decryptedPasswords = new Password[encryptedPasswords.length];
            for (int i = 0; i < encryptedPasswords.length; i++) {
                decryptedPasswords[i] = decryptPassword(encryptedPasswords[i]);
            }
            decryptedValue = decryptedPasswords;
        }
        return decryptedValue;
    }

    private Password decryptPassword(Password encryptedPassword) throws KuraException {
        return new Password(this.cryptoService.decryptAes(encryptedPassword.getPassword()));
    }

    // ----------------------------------------------------------------
    //
    // Private APIs
    //
    // ----------------------------------------------------------------

    private synchronized void updateConfigurationsInternal(List<ComponentConfiguration> configsToUpdate,
            boolean takeSnapshot) throws KuraException {
        boolean snapshotOnConfirmation = false;
        List<Throwable> causes = new ArrayList<>();

        List<ComponentConfiguration> configs = buildCurrentConfiguration(configsToUpdate);

        for (ComponentConfiguration config : configs) {
            for (ComponentConfiguration configToUpdate : configsToUpdate) {
                if (config.getPid().equals(configToUpdate.getPid())) {
                    try {
                        updateConfigurationInternal(config.getPid(), config.getConfigurationProperties(),
                                snapshotOnConfirmation);
                    } catch (KuraException e) {
                        logger.warn("Error during updateConfigurations for component " + config.getPid(), e);
                        causes.add(e);
                    }
                    break;
                }
            }
        }

        // this step creates any not yet existing factory configuration present in configsToUpdate
        for (ComponentConfiguration config : configsToUpdate) {
            String factoryPid = null;
            final Map<String, Object> properties = config.getConfigurationProperties();
            if (properties != null) {
                factoryPid = (String) properties.get(ConfigurationAdmin.SERVICE_FACTORYPID);
            }
            if (factoryPid != null && !this.allActivatedPids.contains(config.getPid())) {
                ConfigurationUpgrade.upgrade(config, this.bundleContext);
                String pid = config.getPid();
                logger.info("Creating configuration with pid: {} and factory pid: {}", pid, factoryPid);
                try {
                    createFactoryConfiguration(factoryPid, pid, properties, false);
                    configs.add(config);
                } catch (KuraException e) {
                    logger.warn("Error creating configuration with pid: {} and factory pid: {}", pid, factoryPid, e);
                }
            }
        }

        if (takeSnapshot && configs != null && !configs.isEmpty()) {
            saveSnapshot(configs);
        }

        if (!causes.isEmpty()) {
            throw new KuraPartialSuccessException("updateConfigurations", causes);
        }
    }

    private synchronized void deleteConfigurationFromConfigAdminInternal(String kuraServicePid) {
        try {
            final Configuration[] configurations = this.configurationAdmin.listConfigurations(null);

            final Optional<Configuration> configuration = Arrays.stream(configurations).filter(c -> {
                final String pid = (String) c.getProperties().get(KURA_SERVICE_PID);
                return pid.equals(kuraServicePid);
            }).findAny();

            if (!configuration.isPresent()) {
                logger.warn("The component with kura.service.pid '{}' does not exist", kuraServicePid);
            } else {
                logger.info("Deleting factory configuration for component with kura.service.pid '{}'",
                        kuraServicePid);
                configuration.get().delete();
            }
        } catch (Exception e) {
            logger.error("Error deleting configuration for component with kura.service.pid '{}'", kuraServicePid, e);
        }
    }

    // returns configurations with encrypted passwords
    private List<ComponentConfiguration> getComponentConfigurationsInternal() throws KuraException {
        List<ComponentConfiguration> configs = new ArrayList<>();

        // assemble all the configurations we have
        // clone the list to avoid concurrent modifications
        List<String> allPids = new ArrayList<>(this.allActivatedPids);
        for (String pid : allPids) {
            try {
                ComponentConfiguration cc = getComponentConfigurationInternal(pid);
                if (cc != null) {
                    configs.add(cc);
                }
            } catch (Exception e) {
                logger.error("Error getting configuration for component " + pid, e);
                throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, e,
                        "Error getting configuration for component " + pid);
            }
        }
        return configs;
    }

    // returns configurations with encrypted passwords
    private ComponentConfiguration getComponentConfigurationInternal(String pid) {
        ComponentConfiguration cc;
        if (!this.activatedSelfConfigComponents.contains(pid)) {
            cc = getConfigurableComponentConfiguration(pid);
        } else {
            cc = getSelfConfiguringComponentConfiguration(pid);
        }
        return cc;
    }

    private ComponentConfiguration getDefaultComponentConfigurationInternal(String pid) {
        ComponentConfiguration cc;
        if (!this.activatedSelfConfigComponents.contains(pid)) {
            cc = getConfigurableComponentDefaultConfiguration(pid);
        } else {
            cc = getSelfConfiguringComponentDefaultConfiguration(pid);
        }
        return cc;
    }

    private ComponentConfiguration getConfigurableComponentDefaultConfiguration(String pid) {
        Tocd ocd = getOCDForPid(pid);
        Map<String, Object> props = ComponentUtil.getDefaultProperties(ocd, this.ctx);
        return new ComponentConfigurationImpl(pid, ocd, props);
    }

    private ComponentConfiguration getSelfConfiguringComponentDefaultConfiguration(String pid) {
        ComponentConfiguration cc = null;
        try {
            String filter = String.format("(kura.service.pid=%s)", pid);
            ServiceReference<?>[] refs = this.ctx.getBundleContext().getServiceReferences((String) null, filter);
            if (refs != null && refs.length > 0) {
                ServiceReference<?> ref = refs[0];
                Object obj = this.ctx.getBundleContext().getService(ref);
                try {
                    cc = getSelfConfiguringComponentDefaultConfigurationInternal(obj, pid);
                } finally {
                    this.ctx.getBundleContext().ungetService(ref);
                }
            }
        } catch (InvalidSyntaxException e) {
            logger.error(GETTING_CONFIGURATION_ERROR, pid, e);
        }

        return cc;
    }

    private ComponentConfiguration getSelfConfiguringComponentDefaultConfigurationInternal(Object obj, String pid) {
        ComponentConfiguration cc = null;
        if (obj instanceof SelfConfiguringComponent) {
            SelfConfiguringComponent selfConfigComp = (SelfConfiguringComponent) obj;
            try {
                ComponentConfiguration tempCc = selfConfigComp.getConfiguration();
                if (tempCc.getPid() == null || !tempCc.getPid().equals(pid)) {
                    logger.error(
                            "Invalid pid for returned Configuration of SelfConfiguringComponent with pid: {} Ignoring it.",
                            pid);
                    return cc;
                }

                OCD ocd = tempCc.getDefinition();
                if (ocd != null) {
                    Map<String, Object> props = ComponentUtil.getDefaultProperties(ocd, this.ctx);
                    cc = new ComponentConfigurationImpl(pid, (Tocd) ocd, props);
                }
            } catch (KuraException e) {
                logger.error(GETTING_CONFIGURATION_ERROR, pid, e);
            }
        } else {
            logger.error("Component {} is not a SelfConfiguringComponent. Ignoring it.", obj);
        }
        return cc;
    }

    private void updateWithDefaultConfiguration(String pid, Tocd ocd) throws KuraException, IOException {
        String servicePid = this.servicePidByPid.get(pid);
        if (servicePid == null) {
            servicePid = pid;
        }
        Configuration config = this.configurationAdmin.getConfiguration(servicePid, "?");
        if (config != null) {
            // get the properties from ConfigurationAdmin if any are present
            Map<String, Object> props = new HashMap<>();
            if (config.getProperties() != null) {
                props.putAll(CollectionsUtil.dictionaryToMap(config.getProperties(), ocd));
            }

            if (!props.containsKey(ConfigurationService.KURA_SERVICE_PID)) {
                props.put(ConfigurationService.KURA_SERVICE_PID, pid);
            }

            // merge the current properties, if any, with the defaults from metatype
            mergeWithDefaults(ocd, props);

            config.update(CollectionsUtil.mapToDictionary(props));
            logger.info("Seeding updated configuration for pid: {}", pid);
        }
    }

    private void registerFactoryComponentOCD(String metatypePid, Tocd ocd, final Bundle provider) throws KuraException {
        this.factoryPids.add(new TrackedComponentFactory(metatypePid, provider));

        for (Map.Entry<String, String> entry : this.factoryPidByPid.entrySet()) {
            if (entry.getValue().equals(metatypePid) && this.servicePidByPid.get(entry.getKey()) != null) {
                try {
                    updateWithDefaultConfiguration(entry.getKey(), ocd);
                } catch (IOException e) {
                    throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
                }
            }
        }
    }

    private void encryptConfigurationProperties(Map<String, Object> propertiesToUpdate) {
        if (propertiesToUpdate == null) {
            return;
        }

        for (Entry<String, Object> property : propertiesToUpdate.entrySet()) {
            Object configValue = property.getValue();
            if (configValue instanceof Password || configValue instanceof Password[]) {
                try {
                    Object encryptedValue = encryptPasswordProperties(configValue);
                    propertiesToUpdate.put(property.getKey(), encryptedValue);
                } catch (KuraException e) {
                    logger.warn("Failed to encrypt Password property: {}", property.getKey());
                    propertiesToUpdate.remove(property.getKey());
                }
            }
        }
    }

    private Object encryptPasswordProperties(Object configValue) throws KuraException {
        Object encryptedValue = null;
        if (configValue instanceof Password) {
            encryptedValue = encryptPassword((Password) configValue);

        } else if (configValue instanceof Password[]) {
            Password[] passwordArray = (Password[]) configValue;
            Password[] encryptedPasswords = new Password[passwordArray.length];

            for (int i = 0; i < passwordArray.length; i++) {
                encryptedPasswords[i] = encryptPassword(passwordArray[i]);
            }
            encryptedValue = encryptedPasswords;
        }
        return encryptedValue;
    }

    private boolean isEncrypted(Password configPassword) {
        boolean result = false;
        try {
            this.cryptoService.decryptAes(configPassword.getPassword());
            result = true;
        } catch (Exception e1) {
        }
        return result;
    }

    private Password encryptPassword(Password password) throws KuraException {
        if (!isEncrypted(password)) {
            return new Password(this.cryptoService.encryptAes(password.getPassword()));
        }
        return password;
    }

    private void encryptConfigs(List<ComponentConfiguration> configs) {
        if (configs != null) {
            for (ComponentConfiguration config : configs) {
                encryptConfigurationProperties(config.getConfigurationProperties());
            }
        }
    }

    private boolean allSnapshotsUnencrypted() {
        try {
            Set<Long> snapshotIDs = getSnapshots();
            if (snapshotIDs == null || snapshotIDs.isEmpty()) {
                return false;
            }
            Long[] snapshots = snapshotIDs.toArray(new Long[] {});

            for (Long snapshot : snapshots) {

                try {
                    // Verify if the current snapshot is encrypted
                    loadEncryptedSnapshotFileContent(snapshot);
                    return false;
                } catch (Exception e) {
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String readFully(final File file) throws IOException {
        final char[] buf = new char[4096];
        final StringBuilder builder = new StringBuilder();

        try (final FileReader r = new FileReader(file)) {
            int rd;

            while ((rd = r.read(buf, 0, buf.length)) > 0) {
                builder.append(buf, 0, rd);
            }
        }

        return builder.toString();
    }

    private void encryptPlainSnapshots() throws KuraException, IOException {
        Set<Long> snapshotIDs = getSnapshots();
        if (snapshotIDs == null || snapshotIDs.isEmpty()) {
            return;
        }
        Long[] snapshots = snapshotIDs.toArray(new Long[] {});

        for (Long snapshot : snapshots) {
            File fSnapshot = getSnapshotFile(snapshot);
            if (fSnapshot == null || !fSnapshot.exists()) {
                throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR, snapshot);
            }

            final XmlComponentConfigurations xmlConfigs = unmarshal(readFully(fSnapshot),
                    XmlComponentConfigurations.class);

            encryptConfigs(xmlConfigs.getConfigurations());

            // Writes an encrypted snapshot with encrypted passwords.
            writeSnapshot(snapshot, xmlConfigs);
        }
    }

    private synchronized long saveSnapshot(List<ComponentConfiguration> configs) throws KuraException {

        List<ComponentConfiguration> configsToSave = configs;

        // Remove definition from configurations
        configsToSave = configs.stream()
                .map(cc -> new ComponentConfigurationImpl(cc.getPid(), null, cc.getConfigurationProperties()))
                .collect(Collectors.toList());

        // Build the XML structure
        XmlComponentConfigurations conf = new XmlComponentConfigurations();
        conf.setConfigurations(configsToSave);

        // Write it to disk: marshall
        long sid = new Date().getTime();

        // Do not save the snapshot in the past
        Set<Long> snapshotIDs = getSnapshots();
        if (snapshotIDs != null && !snapshotIDs.isEmpty()) {
            Long[] snapshots = snapshotIDs.toArray(new Long[] {});
            Long lastestID = snapshots[snapshotIDs.size() - 1];

            if (lastestID != null && sid <= lastestID) {
                logger.warn("Snapshot ID: {} is in the past. Adjusting ID to: {} + 1", sid, lastestID);
                sid = lastestID + 1;
            }
        }

        // Write snapshot
        writeSnapshot(sid, conf);

        this.pendingDeletePids.clear();

        // Garbage Collector for number of Snapshots Saved
        garbageCollectionOldSnapshots();
        return sid;
    }

    private void writeSnapshot(long sid, XmlComponentConfigurations conf) throws KuraException {
        File fSnapshot = getSnapshotFile(sid);
        if (fSnapshot == null) {
            throw new KuraException(KuraErrorCode.CONFIGURATION_SNAPSHOT_NOT_FOUND);
        }

        // Marshall the configuration into an XML
        String xmlResult;
        try {
            xmlResult = marshal(conf);
            if (xmlResult == null || xmlResult.trim().isEmpty()) {
                throw new KuraException(KuraErrorCode.INVALID_PARAMETER, conf);
            }
        } catch (KuraException e) {
            throw e;
        } catch (Exception e) {
            throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
        }

        // Encrypt the XML
        char[] encryptedXML = this.cryptoService.encryptAes(xmlResult.toCharArray());

        // Write the snapshot
        FileOutputStream fos = null;
        OutputStreamWriter osw = null;
        try {
            logger.info("Writing snapshot - Saving {}...", fSnapshot.getAbsolutePath());
            fos = new FileOutputStream(fSnapshot);
            osw = new OutputStreamWriter(fos, "UTF-8");
            osw.append(new String(encryptedXML));
            osw.flush();
            fos.flush();
            fos.getFD().sync();
            logger.info("Writing snapshot - Saving {}... Done.", fSnapshot.getAbsolutePath());
        } catch (FileNotFoundException e) {
            throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
        } catch (UnsupportedEncodingException e) {
            throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
        } catch (IOException e) {
            throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
        } finally {
            if (osw != null) {
                try {
                    osw.close();
                } catch (IOException e) {

                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {

                }
            }
        }
    }

    private ComponentConfiguration getConfigurableComponentConfiguration(String pid) {
        ComponentConfiguration cc = null;
        try {

            Tocd ocd = getOCDForPid(pid);

            String servicePid = this.servicePidByPid.get(pid);

            if (servicePid != null) {
                Configuration cfg = this.configurationAdmin.getConfiguration(servicePid, "?");
                Map<String, Object> props = CollectionsUtil.dictionaryToMap(cfg.getProperties(), ocd);

                cc = new ComponentConfigurationImpl(pid, ocd, props);
            }
        } catch (Exception e) {
            logger.error("Error getting Configuration for component: " + pid + ". Ignoring it.", e);
        }
        return cc;
    }

    private ComponentConfiguration getSelfConfiguringComponentConfiguration(String pid) {
        ComponentConfiguration cc = null;
        try {
            ServiceReference<?>[] refs = this.ctx.getBundleContext().getServiceReferences((String) null, null);
            if (refs != null) {
                for (ServiceReference<?> ref : refs) {
                    String ppid = (String) ref.getProperty(KURA_SERVICE_PID);
                    if (pid.equals(ppid)) {
                        Object obj = this.ctx.getBundleContext().getService(ref);
                        try {
                            if (obj instanceof SelfConfiguringComponent) {
                                SelfConfiguringComponent selfConfigComp = null;
                                selfConfigComp = (SelfConfiguringComponent) obj;
                                try {
                                    cc = selfConfigComp.getConfiguration();
                                    if (cc.getPid() == null || !cc.getPid().equals(pid)) {
                                        logger.error(
                                                "Invalid pid for returned Configuration of SelfConfiguringComponent with pid: "
                                                        + pid + ". Ignoring it.");
                                        return null;
                                    }

                                    OCD ocd = cc.getDefinition();
                                    if (ocd != null) {
                                        List<AD> ads = ocd.getAD();

                                        if (ads != null) {
                                            for (AD ad : ads) {
                                                String adId = ad.getId();
                                                String adType = ad.getType().value();

                                                if (adId == null) {
                                                    logger.error(
                                                            "null required id for AD for returned Configuration of SelfConfiguringComponent with pid: {}",
                                                            pid);
                                                    return null;
                                                }
                                                if (adType == null) {
                                                    logger.error(
                                                            "null required type for AD id: {} for returned Configuration of SelfConfiguringComponent with pid: {}",
                                                            adId, pid);
                                                    return null;
                                                }

                                                Map<String, Object> props = cc.getConfigurationProperties();
                                                if (props != null) {
                                                    Object value = props.get(adId);
                                                    if (value != null) {
                                                        String propType;
                                                        if (!value.getClass().isArray()) {
                                                            propType = value.getClass().getSimpleName();
                                                        } else {
                                                            propType = value.getClass().getComponentType()
                                                                    .getSimpleName();
                                                        }

                                                        try {
                                                            logger.debug(
                                                                    "pid: {}, property name: {}, type: {}, value: {}",
                                                                    pid, adId, propType, value);
                                                            Scalar propertyScalar = Scalar.fromValue(propType);
                                                            Scalar adScalar = Scalar.fromValue(adType);
                                                            if (propertyScalar != adScalar) {
                                                                logger.error(
                                                                        "Type: {} for property named: {} does not match the AD type: {} for returned Configuration of SelfConfiguringComponent with pid: {}",
                                                                        new Object[] { propType, adId, adType, pid });
                                                                return null;
                                                            }
                                                        } catch (IllegalArgumentException e) {
                                                            logger.error(
                                                                    "Invalid class: {} for property named: {} for returned Configuration of SelfConfiguringComponent with pid: "
                                                                            + pid,
                                                                    propType, adId);
                                                            return null;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (KuraException e) {
                                    logger.error(GETTING_CONFIGURATION_ERROR, pid, e);
                                }
                            } else {
                                logger.error("Component {} is not a SelfConfiguringComponent. Ignoring it.", obj);
                            }
                        } finally {
                            this.ctx.getBundleContext().ungetService(ref);
                        }
                    }
                }
            }
        } catch (InvalidSyntaxException e) {
            logger.error(GETTING_CONFIGURATION_ERROR, pid, e);
        }

        return cc;
    }

    private TreeSet<Long> getSnapshotsInternal() {
        // keeps the list of snapshots ordered
        TreeSet<Long> ids = new TreeSet<>();
        String configDir = getSnapshotsDirectory();
        if (configDir != null) {
            File fConfigDir = new File(configDir);
            File[] files = fConfigDir.listFiles();
            if (files != null) {

                Pattern p = Pattern.compile("snapshot_([0-9]+)\\.xml");
                for (File file : files) {
                    Matcher m = p.matcher(file.getName());
                    if (m.matches()) {
                        ids.add(Long.parseLong(m.group(1)));
                    }
                }
            }
        }
        return ids;
    }

    String getSnapshotsDirectory() {
        return this.systemService.getKuraSnapshotsDirectory();
    }

    private File getSnapshotFile(long id) {
        String configDir = getSnapshotsDirectory();

        if (configDir == null) {
            return null;
        }

        StringBuilder sbSnapshot = new StringBuilder(configDir);
        sbSnapshot.append(File.separator).append("snapshot_").append(id).append(".xml");

        String snapshot = sbSnapshot.toString();
        return new File(snapshot);
    }

    private void garbageCollectionOldSnapshots() {
        // get the current snapshots and compared with the maximum number we
        // need to keep
        TreeSet<Long> sids = getSnapshotsInternal();

        int currCount = sids.size();
        int maxCount = this.systemService.getKuraSnapshotsCount();
        while (currCount > maxCount && !sids.isEmpty()) { // stop if count reached or no more snapshots remain

            // preserve snapshot ID 0 as this will be considered the seeding
            // one.
            long sid = sids.pollFirst();
            File fSnapshot = getSnapshotFile(sid);
            if (sid == 0 || fSnapshot == null) {
                continue;
            }

            Path fSnapshotPath = fSnapshot.toPath();
            try {
                if (Files.deleteIfExists(fSnapshotPath)) {
                    logger.info("Snapshots Garbage Collector. Deleted {}", fSnapshotPath);
                    currCount--;
                }
            } catch (IOException e) {
                logger.warn("Snapshots Garbage Collector. Deletion failed for {}", fSnapshotPath, e);
            }
        }
    }

    private void loadLatestSnapshotInConfigAdmin() throws KuraException {
        //
        // save away initial configuration
        List<ComponentConfiguration> configs = buildCurrentConfiguration(null);
        if (configs == null) {
            return;
        }
        for (ComponentConfiguration config : configs) {
            if (config != null) {
                Map<String, Object> props = config.getConfigurationProperties();
                if (props != null) {
                    String factoryPid = (String) props.get(ConfigurationAdmin.SERVICE_FACTORYPID);

                    if (factoryPid != null) {
                        String pid = config.getPid();
                        logger.info("Creating configuration with pid: {} and factory pid: {}", pid, factoryPid);
                        try {
                            createFactoryConfiguration(factoryPid, pid, props, false);
                        } catch (KuraException e) {
                            logger.warn("Error creating configuration with pid: {} and factory pid: {}", pid,
                                    factoryPid, e);
                        }
                    } else {
                        try {
                            logger.debug("Pushing config to config admin: {}", config.getPid());

                            // push it to the ConfigAdmin
                            Configuration cfg = this.configurationAdmin.getConfiguration(config.getPid(), "?");

                            // set kura.service.pid if missing
                            Map<String, Object> newProperties = new HashMap<>(props);
                            if (!newProperties.containsKey(ConfigurationService.KURA_SERVICE_PID)) {
                                newProperties.put(ConfigurationService.KURA_SERVICE_PID, config.getPid());
                            }

                            cfg.update(CollectionsUtil.mapToDictionary(newProperties));

                        } catch (IOException e) {
                            logger.warn("Error seeding initial properties to ConfigAdmin for pid: {}", config.getPid(),
                                    e);
                        }
                    }
                }
            }
        }
    }

    private List<ComponentConfiguration> loadLatestSnapshotConfigurations() throws KuraException {
        //
        // Get the latest snapshot file to use as initialization
        Set<Long> snapshotIDs = getSnapshots();
        if (snapshotIDs == null || snapshotIDs.isEmpty()) {
            return null;
        }

        Long[] snapshots = snapshotIDs.toArray(new Long[] {});
        Long lastestID = snapshots[snapshotIDs.size() - 1];

        //
        // Unmarshall
        logger.info("Loading init configurations from: {}...", lastestID);

        List<ComponentConfiguration> configs = null;
        try {
            XmlComponentConfigurations xmlConfigs = loadEncryptedSnapshotFileContent(lastestID);
            if (xmlConfigs != null) {
                configs = xmlConfigs.getConfigurations();
            }
        } catch (Exception e) {
            logger.info("Unable to decrypt snapshot! Fallback to unencrypted snapshots mode.");
            try {
                if (allSnapshotsUnencrypted()) {
                    encryptPlainSnapshots();
                    configs = loadLatestSnapshotConfigurations();
                }
            } catch (Exception ex) {
                throw new KuraException(KuraErrorCode.INTERNAL_ERROR, ex);
            }
        }

        return configs;
    }

    XmlComponentConfigurations loadEncryptedSnapshotFileContent(long snapshotID) throws KuraException {
        File fSnapshot = getSnapshotFile(snapshotID);
        if (fSnapshot == null || !fSnapshot.exists()) {
            throw new KuraException(KuraErrorCode.CONFIGURATION_SNAPSHOT_NOT_FOUND,
                    fSnapshot != null ? fSnapshot.getAbsolutePath() : "null");
        }

        final String rawSnapshot;
        try {
            rawSnapshot = readFully(fSnapshot);
        } catch (IOException e) {
            logger.error("Error loading file from disk", e);
            return null;
        }

        // File loaded, try to decrypt and unmarshall
        char[] decryptAes = this.cryptoService.decryptAes(rawSnapshot.toCharArray());
        if (decryptAes == null) {
            throw new KuraException(KuraErrorCode.DECODER_ERROR, "snapshot");
        }
        String decryptedContent = new String(decryptAes);

        XmlComponentConfigurations xmlConfigs = null;

        try {
            xmlConfigs = unmarshal(decryptedContent, XmlComponentConfigurations.class);
        } catch (KuraException e) {
            logger.warn("Error parsing xml", e);
        }

        return xmlConfigs;
    }

    private void updateConfigurationInternal(String pid, Map<String, Object> properties, boolean snapshotOnConfirmation)
            throws KuraException {
        logger.debug("Attempting update configuration for {}", pid);

        if (!this.allActivatedPids.contains(pid)) {
            logger.info("UpdatingConfiguration ignored as ConfigurableComponent {} is NOT tracked.", pid);
            return;
        }
        if (properties == null) {
            logger.info("UpdatingConfiguration ignored as properties for ConfigurableComponent {} are NULL.", pid);
            return;
        }

        // get the OCD from the registered ConfigurableComponents
        OCD registerdOCD = getRegisteredOCD(pid);
        if (registerdOCD == null) {
            logger.info("UpdatingConfiguration ignored as OCD for pid {} cannot be found.", pid);
            return;
        }

        Map<String, Object> mergedProperties = new HashMap<>();
        mergeWithDefaults(registerdOCD, mergedProperties);

        if (!this.activatedSelfConfigComponents.contains(pid)) {
            try {
                // get the current running configuration for the selected component
                Configuration config = this.configurationAdmin.getConfiguration(this.servicePidByPid.get(pid), "?");
                Map<String, Object> runningProps = CollectionsUtil.dictionaryToMap(config.getProperties(),
                        registerdOCD);

                mergedProperties.putAll(runningProps);
            } catch (IOException e) {
                logger.info("merge with running failed!");
                throw new KuraException(KuraErrorCode.CONFIGURATION_UPDATE, e, pid);
            }
        }

        mergedProperties.putAll(properties);

        if (!mergedProperties.containsKey(ConfigurationService.KURA_SERVICE_PID)) {
            mergedProperties.put(ConfigurationService.KURA_SERVICE_PID, pid);
        }

        try {
            updateComponentConfiguration(pid, mergedProperties, snapshotOnConfirmation);
            logger.info("Updating Configuration of ConfigurableComponent {} ... Done.", pid);
        } catch (IOException e) {
            logger.warn("Error updating Configuration of ConfigurableComponent with pid {}", pid, e);
            throw new KuraException(KuraErrorCode.CONFIGURATION_UPDATE, e, pid);
        }
    }

    private void rollbackConfigurationInternal(String pid, Map<String, Object> properties,
            boolean snapshotOnConfirmation) throws KuraException {
        logger.debug("Attempting to rollback configuration for {}", pid);

        if (!this.allActivatedPids.contains(pid)) {
            logger.info("UpdatingConfiguration ignored as ConfigurableComponent {} is NOT tracked.", pid);
            return;
        }
        if (properties == null) {
            logger.info("UpdatingConfiguration ignored as properties for ConfigurableComponent {} are NULL.", pid);
            return;
        }

        // get the OCD from the registered ConfigurableComponents
        OCD registerdOCD = getRegisteredOCD(pid);
        if (registerdOCD == null) {
            logger.info("UpdatingConfiguration ignored as OCD for pid {} cannot be found.", pid);
            return;
        }

        Map<String, Object> mergedProperties = new HashMap<>();
        mergeWithDefaults(registerdOCD, mergedProperties);

        mergedProperties.putAll(properties);

        if (!mergedProperties.containsKey(ConfigurationService.KURA_SERVICE_PID)) {
            mergedProperties.put(ConfigurationService.KURA_SERVICE_PID, pid);
        }

        try {
            updateComponentConfiguration(pid, mergedProperties, snapshotOnConfirmation);
            logger.info("Updating Configuration of ConfigurableComponent {} ... Done.", pid);
        } catch (IOException e) {
            logger.warn("Error updating Configuration of ConfigurableComponent with pid {}", pid, e);
            throw new KuraException(KuraErrorCode.CONFIGURATION_UPDATE, e, pid);
        }
    }

    private void updateComponentConfiguration(String pid, Map<String, Object> mergedProperties,
            boolean snapshotOnConfirmation) throws KuraException, IOException {
        if (!this.activatedSelfConfigComponents.contains(pid)) {

            // load the ocd to do the validation
            BundleContext ctx = this.ctx.getBundleContext();
            // FIXME: why the returned ocd is always null?
            ObjectClassDefinition ocd = ComponentUtil.getObjectClassDefinition(ctx, this.servicePidByPid.get(pid));

            // Validate the properties to be applied and set them
            validateProperties(pid, ocd, mergedProperties);
        } else {
            // FIXME: validation of properties for self-configuring
            // components
        }

        // Update the new properties
        // use ConfigurationAdmin to do the update
        Configuration config = this.configurationAdmin.getConfiguration(this.servicePidByPid.get(pid), "?");
        config.update(CollectionsUtil.mapToDictionary(mergedProperties));

        if (snapshotOnConfirmation) {
            snapshot();
        }
    }

    private OCD getRegisteredOCD(String pid) {
        // try to get the OCD from the registered ConfigurableComponents
        OCD registeredOCD = getOCDForPid(pid);
        // otherwise try to get it from the registered SelfConfiguringComponents
        if (registeredOCD == null) {
            ComponentConfiguration config = getSelfConfiguringComponentConfiguration(pid);
            if (config != null) {
                registeredOCD = config.getDefinition();
            }
        }
        return registeredOCD;
    }

    private void validateProperties(String pid, ObjectClassDefinition ocd, Map<String, Object> updatedProps)
            throws KuraException {
        if (ocd != null) {

            // build a map of all the attribute definitions
            Map<String, AttributeDefinition> attrDefs = new HashMap<>();
            AttributeDefinition[] defs = ocd.getAttributeDefinitions(ObjectClassDefinition.ALL);
            for (AttributeDefinition def : defs) {
                attrDefs.put(def.getID(), def);
            }

            // loop over the proposed property values
            // and validate them against the definition
            for (Entry<String, Object> property : updatedProps.entrySet()) {

                String key = property.getKey();
                AttributeDefinition attrDef = attrDefs.get(key);

                // is attribute undefined?
                if (attrDef == null) {
                    // we do not have an attribute descriptor to the validation
                    // against
                    // As OSGI insert attributes at runtime like service.pid,
                    // component.name,
                    // for the attribute for which we do not have a definition,
                    // just accept them.
                    continue;
                }

                // validate the attribute value
                Object objectValue = property.getValue();
                String stringValue = StringUtil.valueToString(objectValue);
                if (stringValue != null) {
                    String result = attrDef.validate(stringValue);
                    if (result != null && !result.isEmpty()) {
                        throw new KuraException(KuraErrorCode.CONFIGURATION_ATTRIBUTE_INVALID, attrDef.getID(),
                                stringValue, result);
                    }
                }
            }

            // make sure all required properties are set
            OCD ocdFull = getOCDForPid(pid);
            if (ocdFull != null) {
                for (AD attrDef : ocdFull.getAD()) {
                    // to the required attributes make sure a value is defined.
                    if (attrDef.isRequired()) {
                        if (updatedProps.get(attrDef.getId()) == null) {
                            // if the default one is not defined, throw
                            // exception.
                            throw new KuraException(KuraErrorCode.CONFIGURATION_REQUIRED_ATTRIBUTE_MISSING,
                                    attrDef.getId());
                        }
                    }
                }
            }
        }
    }

    private synchronized List<ComponentConfiguration> buildCurrentConfiguration(
            List<ComponentConfiguration> configsToUpdate) throws KuraException {
        List<ComponentConfiguration> result = new ArrayList<>();

        // Merge the current configuration of registered components with the provided configurations.
        // It is assumed that the PIDs in the provided configurations is a subset of the registered PIDs.
        List<ComponentConfiguration> currentConfigs = getComponentConfigurationsInternal();
        if (currentConfigs != null) {
            for (ComponentConfiguration currentConfig : currentConfigs) {
                // either add this configuration or a new one obtained by merging its properties with the ones provided
                ComponentConfiguration cc = currentConfig;
                String pid = currentConfig.getPid();
                if (configsToUpdate != null) {
                    for (ComponentConfiguration configToUpdate : configsToUpdate) {
                        if (configToUpdate.getPid().equals(pid)) {
                            Map<String, Object> props = new HashMap<>();
                            if (currentConfig.getConfigurationProperties() != null) {
                                props.putAll(currentConfig.getConfigurationProperties());
                            }
                            if (configToUpdate.getConfigurationProperties() != null) {
                                props.putAll(configToUpdate.getConfigurationProperties());
                            }
                            cc = new ComponentConfigurationImpl(pid, (Tocd) configToUpdate.getDefinition(), props);
                            break;
                        }
                    }
                }
                result.add(cc);
            }
        }

        // complete the returned configurations adding the snapshot configurations
        // of those components not yet in the list.
        List<ComponentConfiguration> snapshotConfigs = loadLatestSnapshotConfigurations();
        if (snapshotConfigs != null) {
            for (ComponentConfiguration snapshotConfig : snapshotConfigs) {
                boolean found = false;
                for (ComponentConfiguration config : result) {
                    if (config.getPid().equals(snapshotConfig.getPid())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // Add old configurations (or not yet tracked ones) present
                    result.add(snapshotConfig);
                }
            }
        }

        // remove configurations being deleted
        for (String deletedPid : this.pendingDeletePids) {
            for (ComponentConfiguration config : result) {
                if (config.getPid().equals(deletedPid)) {
                    result.remove(config);
                    break;
                }
            }
        }

        for (final ComponentConfiguration config : result) {
            ConfigurationUpgrade.upgrade(config, this.bundleContext);
        }

        return result;

    }

    private Tocd getOCDForPid(String pid) {
        Tocd ocd = this.ocds.get(this.factoryPidByPid.get(pid));
        if (ocd == null) {
            ocd = this.ocds.get(pid);
        }
        return ocd;
    }

    /**
     * Convert property value to string
     *
     * @param value
     *            the input value
     * @return the string property value, or {@code null}
     */
    private static String makeString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return value.toString();
    }

    @Override
    public List<ComponentConfiguration> getFactoryComponentOCDs() {
        return this.factoryPids.stream().map(factory -> {
            final String factoryPid = factory.getFactoryPid();
            return new ComponentConfigurationImpl(factoryPid, this.ocds.get(factoryPid), new HashMap<>());
        }).collect(Collectors.toList());
    }

    private ComponentConfiguration getComponentDefinition(String pid) {
        final Tocd ocd = this.ocds.get(pid);
        Map<String, Object> defaultProperties = new HashMap<>();
        if (ocd != null) {
            try {
                defaultProperties = ComponentUtil.getDefaultProperties(ocd, this.ctx);
            } catch (Exception e) {
                logger.warn("Failed to get default properties for component: {}", pid, e);
            }
        }
        return new ComponentConfigurationImpl(pid, ocd, defaultProperties);
    }

    @Override
    public ComponentConfiguration getFactoryComponentOCD(String factoryPid) {
        if (!this.factoryPids.contains(new TrackedComponentFactory(factoryPid, null))) {
            return null;
        }
        return getComponentDefinition(factoryPid);
    }

    private static boolean implementsAnyService(ComponentDescriptionDTO component, String[] classes) {
        final String[] services = component.serviceInterfaces;
        if (services == null) {
            return false;
        }
        for (String className : classes) {
            for (String s : services) {
                if (s.equals(className)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<ComponentConfiguration> getServiceProviderOCDs(String... classNames) {
        return this.scrService.getComponentDescriptionDTOs().stream()
                .filter(component -> implementsAnyService(component, classNames)).map(c -> c.name)
                .map(this::getComponentDefinition).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public List<ComponentConfiguration> getServiceProviderOCDs(Class<?>... classes) {
        final String[] classNames = new String[classes.length];
        for (int i = 0; i < classes.length; i++) {
            classNames[i] = classes[i].getName();
        }
        return getServiceProviderOCDs(classNames);
    }

    protected <T> T unmarshal(final String string, final Class<T> clazz) throws KuraException {
        try {
            return requireNonNull(this.xmlUnmarshaller.unmarshal(string, clazz));
        } catch (final Exception e) {
            throw new KuraException(KuraErrorCode.DECODER_ERROR, "configuration", e);
        }
    }

    protected String marshal(final Object object) throws KuraException {
        try {
            return requireNonNull(this.xmlMarshaller.marshal(object));
        } catch (Exception e) {
            throw new KuraException(KuraErrorCode.ENCODE_ERROR, "configuration", e);
        }
    }

    private static final class TrackedComponentFactory {

        private final String factoryPid;
        private final Bundle provider;

        public TrackedComponentFactory(final String factoryPid, final Bundle provider) {
            this.factoryPid = factoryPid;
            this.provider = provider;
        }

        public String getFactoryPid() {
            return this.factoryPid;
        }

        public Bundle getProviderBundle() {
            return this.provider;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (this.factoryPid == null ? 0 : this.factoryPid.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            TrackedComponentFactory other = (TrackedComponentFactory) obj;
            if (this.factoryPid == null) {
                if (other.factoryPid != null) {
                    return false;
                }
            } else if (!this.factoryPid.equals(other.factoryPid)) {
                return false;
            }
            return true;
        }
    }
}
