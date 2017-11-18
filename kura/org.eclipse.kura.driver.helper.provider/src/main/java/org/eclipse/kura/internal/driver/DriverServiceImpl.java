/*******************************************************************************
 * Copyright (c) 2016, 2017 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech
 *     Red Hat Inc
 *******************************************************************************/
package org.eclipse.kura.internal.driver;

import static java.util.Objects.requireNonNull;
import static org.eclipse.kura.configuration.ConfigurationService.KURA_SERVICE_PID;
import static org.eclipse.kura.driver.Driver.DRIVER_PID_PROPERTY_NAME;
import static org.osgi.service.cm.ConfigurationAdmin.SERVICE_FACTORYPID;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.kura.driver.Driver;
import org.eclipse.kura.driver.DriverDescriptor;
import org.eclipse.kura.driver.DriverService;
import org.eclipse.kura.localization.LocalizationAdapter;
import org.eclipse.kura.localization.resources.AssetMessages;
import org.eclipse.kura.util.service.ServiceUtil;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;

/**
 * The Class DriverServiceImpl is an implementation of the utility API
 * {@link DriverService} to provide useful factory methods for drivers
 */
public class DriverServiceImpl implements DriverService {

    private static final AssetMessages message = LocalizationAdapter.adapt(AssetMessages.class);

    private BundleContext bundleContext;

    public void activate(ComponentContext componentContext) {
        this.bundleContext = componentContext.getBundleContext();
    }

    /** {@inheritDoc} */
    @Override
    public Driver getDriver(final String driverPid) {
        requireNonNull(driverPid, message.driverPidNonNull());
        
        Driver driver = null;
        
        String filterString = String.format("(&(kura.service.pid=%s))", driverPid);
        final ServiceReference<Driver>[] refs = getDriverServiceReferences(filterString);
        try {
            for (final ServiceReference<Driver> ref : refs) {
                driver = this.bundleContext.getService(ref);
            }
        } finally {
            ungetDriverServiceReferences(refs);
        }
        return driver;
    }

    /** {@inheritDoc} */
    @Override
    public String getDriverPid(final Driver driver) {
        requireNonNull(driver, message.driverNonNull());
        final ServiceReference<Driver>[] refs = getDriverServiceReferences(null);
        try {
            for (final ServiceReference<Driver> ref : refs) {
                final Driver driverRef = this.bundleContext.getService(ref);
                if (driverRef == driver) {
                    return ref.getProperty(DRIVER_PID_PROPERTY_NAME).toString();
                }
            }
        } finally {
            ungetDriverServiceReferences(refs);
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public List<Driver> listDrivers() {
        final List<Driver> drivers = new ArrayList<>();
        final ServiceReference<Driver>[] refs = getDriverServiceReferences(null);
        try {
            for (final ServiceReference<Driver> ref : refs) {
                final Driver driverRef = this.bundleContext.getService(ref);
                drivers.add(driverRef);
            }
        } finally {
            ungetDriverServiceReferences(refs);
        }
        return drivers;
    }

    @Override
    public Optional<DriverDescriptor> getDriverDescriptor(String driverPid) {
        requireNonNull(driverPid, message.driverPidNonNull());
        DriverDescriptor driverDescriptor = null;

        String filterString = String.format("(&(kura.service.pid=%s))", driverPid);

        final ServiceReference<Driver>[] refs = getDriverServiceReferences(filterString);
        try {
            for (final ServiceReference<Driver> driverServiceReference : refs) {
                String factoryPid = driverServiceReference.getProperty(SERVICE_FACTORYPID).toString();
                Driver driver = this.bundleContext.getService(driverServiceReference);
                driverDescriptor = newDriverDescriptor(driverPid, factoryPid, driver);
            }
        } finally {
            ungetDriverServiceReferences(refs);
        }

        return Optional.ofNullable(driverDescriptor);
    }

    @Override
    public List<DriverDescriptor> listDriverDescriptors() {
        List<DriverDescriptor> driverDescriptors = new ArrayList<>();

        final ServiceReference<Driver>[] refs = getDriverServiceReferences(null);
        try {
            for (final ServiceReference<Driver> driverServiceReference : refs) {
                String driverPid = driverServiceReference.getProperty(KURA_SERVICE_PID).toString();
                String factoryPid = driverServiceReference.getProperty(SERVICE_FACTORYPID).toString();
                Driver driver = this.bundleContext.getService(driverServiceReference);
                driverDescriptors.add(newDriverDescriptor(driverPid, factoryPid, driver));
            }
        } finally {
            ungetDriverServiceReferences(refs);
        }

        return driverDescriptors;
    }

    private DriverDescriptor newDriverDescriptor(String driverPid, String factoryPid, Driver driver) {
        Object channelDescriptorObj = driver.getChannelDescriptor().getDescriptor();
        return new DriverDescriptor(driverPid, factoryPid, channelDescriptorObj);
    }

    protected ServiceReference<Driver>[] getDriverServiceReferences(final String filter) {
        return ServiceUtil.getServiceReferences(this.bundleContext, Driver.class, filter);
    }

    protected void ungetDriverServiceReferences(final ServiceReference<Driver>[] refs) {
        ServiceUtil.ungetServiceReferences(this.bundleContext, refs);
    }
}
