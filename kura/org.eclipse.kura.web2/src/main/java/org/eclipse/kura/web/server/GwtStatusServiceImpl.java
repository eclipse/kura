/*******************************************************************************
 * Copyright (c) 2011, 2023 Eurotech and/or its affiliates and others
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
package org.eclipse.kura.web.server;

import static java.lang.String.format;
import static java.util.Collections.sort;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.kura.cloud.CloudService;
import org.eclipse.kura.cloudconnection.CloudConnectionManager;
import org.eclipse.kura.cloudconnection.CloudEndpoint;
import org.eclipse.kura.configuration.ConfigurationService;
import org.eclipse.kura.data.DataService;
import org.eclipse.kura.data.DataTransportService;
import org.eclipse.kura.position.PositionService;
import org.eclipse.kura.security.tamper.detection.TamperDetectionProperties;
import org.eclipse.kura.security.tamper.detection.TamperDetectionService;
import org.eclipse.kura.security.tamper.detection.TamperStatus;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.web.server.util.ServiceLocator;
import org.eclipse.kura.web.shared.GwtKuraException;
import org.eclipse.kura.web.shared.model.GwtCloudConnectionInfo;
import org.eclipse.kura.web.shared.model.GwtGroupedNVPair;
import org.eclipse.kura.web.shared.model.GwtModemInterfaceConfig;
import org.eclipse.kura.web.shared.model.GwtNetIfConfigMode;
import org.eclipse.kura.web.shared.model.GwtNetIfStatus;
import org.eclipse.kura.web.shared.model.GwtNetIfType;
import org.eclipse.kura.web.shared.model.GwtNetInterfaceConfig;
import org.eclipse.kura.web.shared.model.GwtNetRouterMode;
import org.eclipse.kura.web.shared.model.GwtWifiConfig;
import org.eclipse.kura.web.shared.model.GwtWifiNetInterfaceConfig;
import org.eclipse.kura.web.shared.model.GwtWifiWirelessMode;
import org.eclipse.kura.web.shared.model.GwtXSRFToken;
import org.eclipse.kura.web.shared.service.GwtStatusService;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GwtStatusServiceImpl extends OsgiRemoteServiceServlet implements GwtStatusService {

    private static final String IP_ACQUISITION = "IP Acquisition: ";
    private static final String MODE = "Mode: ";
    private static final String SUBNET_MASK = "Subnet Mask: ";
    private static final String POSITION_STATUS = "positionStatus";
    private static final Logger logger = LoggerFactory.getLogger(GwtStatusServiceImpl.class);
    private static final long serialVersionUID = 8256280782910423734L;
    private static final String KURA_SERVICE_PID = ConfigurationService.KURA_SERVICE_PID;
    private static final String DATA_SERVICE_REFERENCE_NAME = "DataService";
    private static final String DATA_TRANSPORT_SERVICE_REFERENCE_NAME = "DataTransportService";
    private static final String NL = "<br />";
    private static final String tab = "&nbsp&nbsp&nbsp&nbsp";
    private static final long NETWORK_INFO_REFRESH_TIMEOUT = 30000l;

    private static List<GwtGroupedNVPair> networkStatus;
    private static long lastUpdate;

    @Override
    public ArrayList<GwtGroupedNVPair> getDeviceConfig(GwtXSRFToken xsrfToken, boolean hasNetAdmin, boolean recompute)
            throws GwtKuraException {
        checkXSRFToken(xsrfToken);
        List<GwtGroupedNVPair> pairs = new ArrayList<>();

        pairs.addAll(getCloudStatus());
        if (hasNetAdmin) {
            pairs.addAll(getNetworkStatus(recompute));
        }
        pairs.addAll(getPositionStatus());
        pairs.addAll(getTamperDetectionStatus());

        return new ArrayList<>(pairs);
    }

    private List<GwtGroupedNVPair> getTamperDetectionStatus() {
        final List<GwtGroupedNVPair> result = new ArrayList<>();

        try {
            ServiceLocator.applyToAllServices(TamperDetectionService.class, t -> {

                final TamperStatus tamperStatus = t.getTamperStatus();

                result.add(new GwtGroupedNVPair("tamperDetection", t.getDisplayName(),
                        tamperStatus.isDeviceTampered() ? "Tampered" : "Not Tampered"));

                final TypedValue<?> timestamp = tamperStatus.getProperties()
                        .get(TamperDetectionProperties.TIMESTAMP_PROPERTY_KEY.getValue());

                if (timestamp != null && timestamp.getValue() instanceof Number) {
                    result.add(new GwtGroupedNVPair("tamperDetection", "Last Tamper Event Timestamp",
                            new Date(((Number) timestamp.getValue()).longValue()).toString()));
                }

            });

        } catch (final GwtKuraException e) {
            logger.warn("failed to get tamper status", e);
        }

        return result;
    }

    private List<GwtGroupedNVPair> getCloudStatus() throws GwtKuraException {
        final List<GwtGroupedNVPair> pairs = new ArrayList<>();

        final List<GwtCloudConnectionInfo> connectionInfos = new ArrayList<>();

        try {
            final Collection<ServiceReference<CloudEndpoint>> cloudEndpointReferences = ServiceLocator.getInstance()
                    .getServiceReferences(CloudEndpoint.class, null);

            List<ServiceReference<CloudEndpoint>> cloudEndpointReferencesList = new ArrayList<>(
                    cloudEndpointReferences);

            for (ServiceReference<CloudEndpoint> cloudEndpointReference : cloudEndpointReferencesList) {
                GwtCloudConnectionInfo cloudConnectionInfo = new GwtCloudConnectionInfo();

                String cloudServicePid = (String) cloudEndpointReference.getProperty(KURA_SERVICE_PID);
                cloudConnectionInfo.setCloudServicePid(cloudServicePid);

                final String filter = format("(%s=%s)", KURA_SERVICE_PID, cloudServicePid);
                final Collection<ServiceReference<CloudConnectionManager>> cloudConnectionManagerReferences = ServiceLocator
                        .getInstance().getServiceReferences(CloudConnectionManager.class, filter);

                cloudConnectionManagerReferences.forEach(cloudConnectionManagerReference -> {
                    CloudConnectionManager cloudConnectionManager;
                    try {
                        cloudConnectionManager = ServiceLocator.getInstance()
                                .getService(cloudConnectionManagerReference);
                        cloudConnectionInfo.addConnectionProperty("Service Status",
                                cloudConnectionManager.isConnected() ? "CONNECTED" : "DISCONNECTED");
                    } catch (GwtKuraException e) {
                        // endpoint not connection oriented
                    }

                });

                CloudEndpoint cloudEndpoint = ServiceLocator.getInstance().getService(cloudEndpointReference);

                Map<String, String> connectionProps = cloudEndpoint.getInfo();
                connectionProps.forEach(cloudConnectionInfo::addConnectionProperty);

                connectionInfos.add(cloudConnectionInfo);
            }
        } catch (GwtKuraException e) {
            logger.warn("Get cloud status failed", e);
        }

        try {
            final Collection<ServiceReference<CloudService>> cloudServiceReferences = ServiceLocator.getInstance()
                    .getServiceReferences(CloudService.class, null);

            List<ServiceReference<CloudService>> cloudServiceReferencesList = new ArrayList<>(cloudServiceReferences);

            for (ServiceReference<CloudService> cloudServiceReference : cloudServiceReferencesList) {
                String cloudServicePid = (String) cloudServiceReference.getProperty(KURA_SERVICE_PID);

                if (connectionInfos.stream()
                        .noneMatch(connectionInfo -> connectionInfo.getCloudServicePid().equals(cloudServicePid))) {
                    GwtCloudConnectionInfo cloudConnectionInfo = fillFromCloudService(cloudServiceReference,
                            cloudServicePid);
                    connectionInfos.add(cloudConnectionInfo);
                }
            }
        } catch (GwtKuraException e) {
            logger.warn("Get cloud status failed", e);
        }

        connectionInfos.sort((c1, c2) -> c1.getCloudServicePid().compareTo(c2.getCloudServicePid()));

        fillCloudConnectionInfo(pairs, connectionInfos);
        return pairs;
    }

    private void fillCloudConnectionInfo(List<GwtGroupedNVPair> pairs, List<GwtCloudConnectionInfo> connectionInfos) {
        connectionInfos.forEach(connectionInfo -> {
            pairs.add(new GwtGroupedNVPair("cloudStatus", "Connection Name", connectionInfo.getCloudServicePid()));

            Map<String, Object> connectionProperties = connectionInfo.getConnectionProperties();
            connectionProperties
                    .forEach((key, value) -> pairs.add(new GwtGroupedNVPair("cloudStatus", key, (String) value)));
        });
    }

    private GwtCloudConnectionInfo fillFromCloudService(ServiceReference<CloudService> cloudServiceReference,
            String cloudServicePid) throws GwtKuraException {

        GwtCloudConnectionInfo result = new GwtCloudConnectionInfo();
        result.setCloudServicePid(cloudServicePid);

        final CloudService cloudService = ServiceLocator.getInstance().getService(cloudServiceReference);
        try {
            result.addConnectionProperty("Service Status", cloudService.isConnected() ? "CONNECTED" : "DISCONNECTED");

            final String dataServiceRef = (String) cloudServiceReference
                    .getProperty(DATA_SERVICE_REFERENCE_NAME + ComponentConstants.REFERENCE_TARGET_SUFFIX);
            if (dataServiceRef != null) {
                fillFromDataService(result, dataServiceRef);
            }
        } finally {
            ServiceLocator.getInstance().ungetService(cloudServiceReference);
        }
        return result;
    }

    private void fillFromDataService(GwtCloudConnectionInfo cloudConnectionInfo, final String dataServiceRef)
            throws GwtKuraException {
        final Collection<ServiceReference<DataService>> dataServiceReferences = ServiceLocator.getInstance()
                .getServiceReferences(DataService.class, dataServiceRef);

        for (ServiceReference<DataService> dataServiceReference : dataServiceReferences) {
            final DataService dataService = ServiceLocator.getInstance().getService(dataServiceReference);
            try {
                if (dataService != null) {
                    cloudConnectionInfo.addConnectionProperty("Auto-connect",
                            dataService.isAutoConnectEnabled()
                                    ? "ON (Retry Interval is " + Integer.toString(dataService.getRetryInterval()) + "s)"
                                    : "OFF");
                }

                final String dataTransportRef = (String) dataServiceReference.getProperty(
                        DATA_TRANSPORT_SERVICE_REFERENCE_NAME + ComponentConstants.REFERENCE_TARGET_SUFFIX);
                final List<DataTransportService> dataTransportServices = ServiceLocator.getInstance()
                        .getServices(DataTransportService.class, dataTransportRef);
                for (DataTransportService dataTransportService : dataTransportServices) {
                    cloudConnectionInfo.addConnectionProperty("Broker URL", dataTransportService.getBrokerUrl());
                    cloudConnectionInfo.addConnectionProperty("Account", dataTransportService.getAccountName());
                    cloudConnectionInfo.addConnectionProperty("Username", dataTransportService.getUsername());
                    cloudConnectionInfo.addConnectionProperty("Client ID", dataTransportService.getClientId());
                }
            } finally {
                ServiceLocator.getInstance().ungetService(dataServiceReference);
            }
        }
    }

    private static List<GwtGroupedNVPair> getNetworkStatus(boolean recompute) throws GwtKuraException {
        long currentTime = System.currentTimeMillis();
        if (GwtStatusServiceImpl.networkStatus != null
                && currentTime - GwtStatusServiceImpl.lastUpdate < NETWORK_INFO_REFRESH_TIMEOUT) {
            return GwtStatusServiceImpl.networkStatus;
        }

        List<GwtGroupedNVPair> pairs = new ArrayList<>();

        GwtNetworkServiceImplFacade gwtNetworkService = new GwtNetworkServiceImplFacade();
        boolean isNet2 = gwtNetworkService.isNet2();

        List<GwtNetInterfaceConfig> gwtNetInterfaceConfigs;

        try {
            gwtNetInterfaceConfigs = gwtNetworkService.findNetInterfaceConfigurations(recompute);
        } catch (GwtKuraException e) {
            logger.warn("Get network status failed");
            return Collections.emptyList();
        }

        sort(gwtNetInterfaceConfigs, comparing(GwtNetInterfaceConfig::getName, nullsFirst(naturalOrder())));

        for (GwtNetInterfaceConfig gwtNetInterfaceConfig : gwtNetInterfaceConfigs) {

            String currentAddress = gwtNetInterfaceConfig.getIpAddress();
            String currentSubnetMask = gwtNetInterfaceConfig.getSubnetMask();

            String currentConfigMode;
            if (gwtNetInterfaceConfig.getConfigModeEnum() == GwtNetIfConfigMode.netIPv4ConfigModeDHCP) {
                currentConfigMode = "DHCP";
            } else {
                currentConfigMode = "Manual";
            }

            String currentRouterMode;
            if (gwtNetInterfaceConfig.getRouterModeEnum() == GwtNetRouterMode.netRouterDchp) {
                currentRouterMode = "DHCPD";
            } else if (gwtNetInterfaceConfig.getRouterModeEnum() == GwtNetRouterMode.netRouterNat) {
                currentRouterMode = "NAT";
            } else if (gwtNetInterfaceConfig.getRouterModeEnum() == GwtNetRouterMode.netRouterDchpNat) {
                currentRouterMode = "DHCPD & NAT";
            } else {
                currentRouterMode = "";
            }

            if (gwtNetInterfaceConfig.getHwTypeEnum() == GwtNetIfType.ETHERNET) {
                if (gwtNetInterfaceConfig.getStatusEnum() == GwtNetIfStatus.netIPv4StatusDisabled
                        || gwtNetInterfaceConfig.getStatusEnum() == GwtNetIfStatus.netIPv4StatusUnmanaged
                        || gwtNetInterfaceConfig.getStatusEnum() == GwtNetIfStatus.netIPv4StatusL2Only) {
                    pairs.add(new GwtGroupedNVPair("networkStatusEthernet", gwtNetInterfaceConfig.getName() + " - IPv4",
                            gwtNetInterfaceConfig.getStatusEnum().getValue()));
                } else {
                    pairs.add(new GwtGroupedNVPair("networkStatusEthernet", gwtNetInterfaceConfig.getName() + " - IPv4",
                            currentAddress + NL + tab + SUBNET_MASK + currentSubnetMask + NL + tab + MODE
                                    + gwtNetInterfaceConfig.getStatusEnum().getValue() + NL + tab + IP_ACQUISITION
                                    + currentConfigMode + NL + tab + "Router Mode: " + currentRouterMode));
                }
            } else if (gwtNetInterfaceConfig.getHwTypeEnum() == GwtNetIfType.WIFI
                    && !gwtNetInterfaceConfig.getName().startsWith("mon")) {
                String currentWifiMode = ((GwtWifiNetInterfaceConfig) gwtNetInterfaceConfig)
                        .getWirelessModeEnum() == GwtWifiWirelessMode.netWifiWirelessModeStation ? "Station Mode"
                                : "Access Point";
                GwtWifiConfig gwtActiveWifiConfig = ((GwtWifiNetInterfaceConfig) gwtNetInterfaceConfig)
                        .getActiveWifiConfig();
                String currentWifiSsid = null;
                if (gwtActiveWifiConfig != null) {
                    currentWifiSsid = gwtActiveWifiConfig.getWirelessSsid();
                }
                if (gwtNetInterfaceConfig.getStatusEnum() == GwtNetIfStatus.netIPv4StatusDisabled
                        || gwtNetInterfaceConfig.getStatusEnum() == GwtNetIfStatus.netIPv4StatusUnmanaged
                        || gwtNetInterfaceConfig.getStatusEnum() == GwtNetIfStatus.netIPv4StatusL2Only) {
                    pairs.add(new GwtGroupedNVPair("networkStatusWifi", gwtNetInterfaceConfig.getName(),
                            gwtNetInterfaceConfig.getStatusEnum().getValue()));
                } else {
                    pairs.add(new GwtGroupedNVPair("networkStatusWifi", gwtNetInterfaceConfig.getName(),
                            currentAddress + NL + tab + SUBNET_MASK + currentSubnetMask + NL + tab + MODE
                                    + gwtNetInterfaceConfig.getStatusEnum().getValue() + NL + tab + IP_ACQUISITION
                                    + currentConfigMode + NL + tab + "Router Mode: " + currentRouterMode + NL + tab
                                    + "Wireless Mode:" + currentWifiMode + NL + tab + "SSID: " + currentWifiSsid + NL));
                }
            } else if (gwtNetInterfaceConfig.getHwTypeEnum() == GwtNetIfType.MODEM) {
                String currentModemApn = ((GwtModemInterfaceConfig) gwtNetInterfaceConfig).getApn();
                String name = ((GwtModemInterfaceConfig) gwtNetInterfaceConfig).getName();
                String interfaceName = ((GwtModemInterfaceConfig) gwtNetInterfaceConfig).getInterfaceName();
                if (Objects.nonNull(interfaceName) && !interfaceName.isEmpty()) {
                    name = name + " (" + interfaceName + ")";
                }
                if (gwtNetInterfaceConfig.getStatusEnum() == GwtNetIfStatus.netIPv4StatusDisabled
                        || gwtNetInterfaceConfig.getStatusEnum() == GwtNetIfStatus.netIPv4StatusUnmanaged
                        || gwtNetInterfaceConfig.getStatusEnum() == GwtNetIfStatus.netIPv4StatusL2Only) {
                    pairs.add(new GwtGroupedNVPair("networkStatusModem", name,
                            gwtNetInterfaceConfig.getStatusEnum().getValue()));
                } else {
                    pairs.add(new GwtGroupedNVPair("networkStatusModem", name,
                            currentAddress + NL + SUBNET_MASK + currentSubnetMask + NL + tab + MODE
                                    + gwtNetInterfaceConfig.getStatusEnum().getValue() + NL + tab + IP_ACQUISITION
                                    + currentConfigMode + NL + tab + "APN: " + currentModemApn + NL + tab
                                    + "Interface: " + interfaceName));
                }
            }

            if (isNet2) {
                String currentIPv6Address = gwtNetInterfaceConfig.getIpv6Address();
                String currentIPv6SubnetMask = String.valueOf(gwtNetInterfaceConfig.getIpv6SubnetMask());

                String currentIPv6ConfigMode;
                String currentIPv6ConfigModeString = gwtNetInterfaceConfig.getIpv6ConfigMode();
                if (Objects.nonNull(currentIPv6ConfigModeString)) {
                    if (gwtNetInterfaceConfig.getIpv6ConfigMode().equals("netIPv6MethodAuto")) {
                        currentIPv6ConfigMode = "Auto";
                    } else if (currentIPv6ConfigModeString.equals("netIPv6MethodDhcp")) {
                        currentIPv6ConfigMode = "DHCP";
                    } else {
                        currentIPv6ConfigMode = "Manual";
                    }
                } else {
                    currentIPv6ConfigMode = "Manual";
                }

                String statusIPv6 = gwtNetInterfaceConfig.getIpv6Status();

                if (gwtNetInterfaceConfig.getHwTypeEnum() == GwtNetIfType.ETHERNET) {

                    if (statusIPv6.equals("netIPv6StatusDisabled")
                            || statusIPv6.equals("netIPv6StatusUnmanaged")
                            || statusIPv6.equals("netIPv6StatusL2Only")) {
                        pairs.add(new GwtGroupedNVPair("networkStatusEthernet",
                                gwtNetInterfaceConfig.getName() + " - IPv6",
                                convertIPv6Status(statusIPv6)));
                    } else {
                        pairs.add(new GwtGroupedNVPair("networkStatusEthernet",
                                gwtNetInterfaceConfig.getName() + " - IPv6",
                                currentIPv6Address + NL + tab + SUBNET_MASK + currentIPv6SubnetMask + NL + tab + MODE
                                        + convertIPv6Status(statusIPv6) + NL + tab + IP_ACQUISITION
                                        + currentIPv6ConfigMode));
                    }
                } else if (gwtNetInterfaceConfig.getHwTypeEnum() == GwtNetIfType.WIFI
                        && !gwtNetInterfaceConfig.getName().startsWith("mon")) {
                    String currentWifiMode = ((GwtWifiNetInterfaceConfig) gwtNetInterfaceConfig)
                            .getWirelessModeEnum() == GwtWifiWirelessMode.netWifiWirelessModeStation ? "Station Mode"
                                    : "Access Point";
                    GwtWifiConfig gwtActiveWifiConfig = ((GwtWifiNetInterfaceConfig) gwtNetInterfaceConfig)
                            .getActiveWifiConfig();
                    String currentWifiSsid = null;
                    if (gwtActiveWifiConfig != null) {
                        currentWifiSsid = gwtActiveWifiConfig.getWirelessSsid();
                    }
                    if (statusIPv6.equals("netIPv6StatusDisabled")
                            || statusIPv6.equals("netIPv6StatusUnmanaged")
                            || statusIPv6.equals("netIPv6StatusL2Only")) {
                        pairs.add(new GwtGroupedNVPair("networkStatusWifi", gwtNetInterfaceConfig.getName() + " - IPv6",
                                convertIPv6Status(statusIPv6)));
                    } else {
                        pairs.add(new GwtGroupedNVPair("networkStatusWifi", gwtNetInterfaceConfig.getName() + " - IPv6",
                                currentIPv6Address + NL + tab + SUBNET_MASK + currentIPv6SubnetMask + NL + tab + MODE
                                        + convertIPv6Status(statusIPv6) + NL + tab + IP_ACQUISITION
                                        + currentIPv6ConfigMode + NL + tab + "Wireless Mode:" + currentWifiMode + NL
                                        + tab
                                        + "SSID: " + currentWifiSsid + NL));
                    }
                } else if (gwtNetInterfaceConfig.getHwTypeEnum() == GwtNetIfType.MODEM) {
                    String currentModemApn = ((GwtModemInterfaceConfig) gwtNetInterfaceConfig).getApn();
                    String name = ((GwtModemInterfaceConfig) gwtNetInterfaceConfig).getName();
                    String interfaceName = ((GwtModemInterfaceConfig) gwtNetInterfaceConfig).getInterfaceName();
                    if (Objects.nonNull(interfaceName) && !interfaceName.isEmpty()) {
                        name = name + " (" + interfaceName + ")";
                    }
                    if (statusIPv6.equals("netIPv6StatusDisabled")
                            || statusIPv6.equals("netIPv6StatusUnmanaged")
                            || statusIPv6.equals("netIPv6StatusL2Only")) {
                        pairs.add(new GwtGroupedNVPair("networkStatusModem", name + " - IPv6",
                                convertIPv6Status(statusIPv6)));
                    } else {
                        pairs.add(new GwtGroupedNVPair("networkStatusModem", name + " - IPv6",
                                currentIPv6Address + NL + SUBNET_MASK + currentIPv6SubnetMask + NL + tab + MODE
                                        + convertIPv6Status(statusIPv6) + NL + tab + IP_ACQUISITION
                                        + currentIPv6ConfigMode + NL + tab + "APN: " + currentModemApn + NL + tab
                                        + "Interface: " + interfaceName));
                    }
                }
            }
        }

        networkStatus = pairs;
        lastUpdate = System.currentTimeMillis();
        return networkStatus;
    }

    private static String convertIPv6Status(String status) {
        switch (status) {
            case "netIPv6StatusDisabled":
                return "Disabled";
            case "netIPv6StatusUnmanaged":
                return "Unmanaged";
            case "netIPv6StatusL2Only":
                return "L2Only";
            case "netIPv6StatusEnabledLAN":
                return "LAN";
            case "netIPv6StatusEnabledWAN":
                return "WAN";
            case "netIPv6StatusUnknown":
                return "Unknown";
            default:
                return "Unknown";
        }

    }

    private List<GwtGroupedNVPair> getPositionStatus() throws GwtKuraException {
        final List<GwtGroupedNVPair> pairs = new ArrayList<>();

        ServiceLocator.applyToServiceOptionally(PositionService.class, positionService -> {
            if (positionService != null) {
                pairs.add(new GwtGroupedNVPair(POSITION_STATUS, "Longitude",
                        Double.toString(Math.toDegrees(positionService.getPosition().getLongitude().getValue()))));
                pairs.add(new GwtGroupedNVPair(POSITION_STATUS, "Latitude",
                        Double.toString(Math.toDegrees(positionService.getPosition().getLatitude().getValue()))));
                pairs.add(new GwtGroupedNVPair(POSITION_STATUS, "Altitude",
                        positionService.getPosition().getAltitude().toString()));
            }
            return null;
        });

        return pairs;
    }
}