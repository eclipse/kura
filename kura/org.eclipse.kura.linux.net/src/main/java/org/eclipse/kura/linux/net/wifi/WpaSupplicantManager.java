/*******************************************************************************
 * Copyright (c) 2011, 2018 Eurotech and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech
 *******************************************************************************/
package org.eclipse.kura.linux.net.wifi;

import java.io.File;
import java.util.Collection;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.core.linux.util.LinuxProcessUtil;
import org.eclipse.kura.linux.net.util.LinuxNetworkUtil;
import org.eclipse.kura.net.wifi.WifiMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WpaSupplicantManager {

    private static Logger logger = LoggerFactory.getLogger(WpaSupplicantManager.class);

    private static final File TEMP_CONFIG_FILE = new File("/tmp/wpa_supplicant.conf");

    private static String supplicantDriver = null;

    private WpaSupplicantManager() {
    }

    public static void start(String interfaceName, final WifiMode mode, String driver) throws KuraException {
        start(interfaceName, mode, driver, new File(getWpaSupplicantConfigFilename(interfaceName)));
    }

    public static void startTemp(String interfaceName, final WifiMode mode, String driver) throws KuraException {
        start(interfaceName, mode, driver, TEMP_CONFIG_FILE);
    }

    private static synchronized void start(String interfaceName, final WifiMode mode, String driver, File configFile)
            throws KuraException {
        logger.debug("enable WPA Supplicant");

        try {
            if (WpaSupplicantManager.isRunning(interfaceName)) {
                stop(interfaceName);
            }

            String drv = getDriver(interfaceName);
            if (drv != null) {

                supplicantDriver = drv;
            } else {
                supplicantDriver = driver;
            }

            // start wpa_supplicant
            String wpaSupplicantCommand = formSupplicantStartCommand(interfaceName, configFile);
            logger.info("starting wpa_supplicant for the {} interface -> {}", interfaceName, wpaSupplicantCommand);
            int stat = LinuxProcessUtil.start(wpaSupplicantCommand);
            if (stat != 0 && stat != 255) {
                logger.error("failed to start wpa_supplicant for the {} interface for unknown reason - errorCode={}",
                        interfaceName, stat);
                throw KuraException.internalError("failed to start hostapd for unknown reason");
            }
        } catch (Exception e) {
            logger.error("Exception while enabling WPA Supplicant!", e);
            throw KuraException.internalError(e);
        }
    }

    /*
     * This method forms wpa_supplicant start command
     */
    private static String formSupplicantStartCommand(String ifaceName, File configFile) {
        StringBuilder sb = new StringBuilder();

        sb.append("wpa_supplicant -B -D ");
        sb.append(supplicantDriver);
        sb.append(" -i ");
        sb.append(ifaceName);
        sb.append(" -c ");
        sb.append(configFile);

        return sb.toString();
    }

    /**
     * Reports if wpa_supplicant is running
     *
     * @return {@link boolean}
     */
    public static boolean isRunning(String ifaceName) throws KuraException {
        try {
            boolean ret = false;
            if (getPid(ifaceName) > 0) {
                ret = true;
            }
            logger.trace("isRunning() :: --> {}", ret);
            return ret;
        } catch (Exception e) {
            throw KuraException.internalError(e);
        }
    }

    public static int getPid(String ifaceName) throws KuraException {
        try {
            String[] tokens = { "-i " + ifaceName };
            int pid = LinuxProcessUtil.getPid("wpa_supplicant", tokens);
            logger.trace("getPid() :: pid={}", pid);
            return pid;
        } catch (Exception e) {
            throw KuraException.internalError(e);
        }
    }

    public static boolean isTempRunning() throws KuraException {
        try {
            String[] tokens = { "-c " + TEMP_CONFIG_FILE };
            int pid = LinuxProcessUtil.getPid("wpa_supplicant", tokens);
            return pid > -1;
        } catch (Exception e) {
            throw KuraException.internalError(e);
        }
    }

    /**
     * Stops all instances of wpa_supplicant
     *
     * @throws Exception
     */
    public static void stop(String ifaceName) throws KuraException {
        try {

            LinuxProcessUtil.stopAndKill(getPid(ifaceName));

            if (ifaceName != null) {
                LinuxNetworkUtil.disableInterface(ifaceName);
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            throw KuraException.internalError(e);
        }
    }

    public static String getWpaSupplicantConfigFilename(String ifaceName) {
        StringBuilder sb = new StringBuilder();
        String snapCommon = System.getProperty("kura.data.snap.common");
        if (snapCommon == null) {
            sb.append("/etc/wpa_supplicant-").append(ifaceName).append(".conf");
        } else {
            sb.append(snapCommon + "/etc/wpa_supplicant/wpa_supplicant-").append(ifaceName).append(".conf");
        }

        return sb.toString();
    }

    public static String getDriver(String iface) throws KuraException {
        String driver = null;
        Collection<String> supportedWifiOptions = null;
        try {
            supportedWifiOptions = WifiOptions.getSupportedOptions(iface);
        } catch (Exception e) {
            throw new KuraException(KuraErrorCode.PROCESS_EXECUTION_ERROR, e);
        }

        if (!supportedWifiOptions.isEmpty()) {
            if (supportedWifiOptions.contains(WifiOptions.WIFI_MANAGED_DRIVER_NL80211)) {
                driver = WifiOptions.WIFI_MANAGED_DRIVER_NL80211;
            } else if (supportedWifiOptions.contains(WifiOptions.WIFI_MANAGED_DRIVER_WEXT)) {
                driver = WifiOptions.WIFI_MANAGED_DRIVER_WEXT;
            }
        }
        return driver;
    }
}
