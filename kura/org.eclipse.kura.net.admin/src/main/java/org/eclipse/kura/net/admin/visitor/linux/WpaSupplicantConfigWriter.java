/*******************************************************************************
 * Copyright (c) 2011, 2018 Eurotech and/or its affiliates and others
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
package org.eclipse.kura.net.admin.visitor.linux;

import static org.eclipse.kura.net.admin.visitor.linux.WriterHelper.copyFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;

import org.apache.commons.io.FileUtils;
import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.core.net.AbstractNetInterface;
import org.eclipse.kura.core.net.NetworkConfiguration;
import org.eclipse.kura.core.net.NetworkConfigurationVisitor;
import org.eclipse.kura.core.net.WifiInterfaceAddressConfigImpl;
import org.eclipse.kura.core.util.IOUtil;
import org.eclipse.kura.linux.net.wifi.WpaSupplicantManager;
import org.eclipse.kura.net.NetConfig;
import org.eclipse.kura.net.NetConfigIP4;
import org.eclipse.kura.net.NetInterfaceAddressConfig;
import org.eclipse.kura.net.NetInterfaceConfig;
import org.eclipse.kura.net.NetInterfaceStatus;
import org.eclipse.kura.net.NetInterfaceType;
import org.eclipse.kura.net.admin.visitor.linux.util.KuranetConfig;
import org.eclipse.kura.net.admin.visitor.linux.util.WpaSupplicantUtil;
import org.eclipse.kura.net.wifi.WifiCiphers;
import org.eclipse.kura.net.wifi.WifiConfig;
import org.eclipse.kura.net.wifi.WifiMode;
import org.eclipse.kura.net.wifi.WifiSecurity;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WpaSupplicantConfigWriter implements NetworkConfigurationVisitor {

    private static final Logger logger = LoggerFactory.getLogger(WpaSupplicantConfigWriter.class);

    private static final String WPA_TMP_CONFIG_FILE = (System.getProperty("kura.data.snap.common") == null ?
                  "/etc/wpa_supplicant.conf.tmp"
                : System.getProperty("kura.data.snap.common") + "/etc/wpa_supplicant.conf.tmp");
    private static final String TMP_WPA_CONFIG_FILE = "/tmp/wpa_supplicant.conf";
    private static final String WPA_SUPPLICANT_CONF_RESOURCE = "/src/main/resources/wifi/wpasupplicant.conf";

    private static final String HEXES = "0123456789ABCDEF";

    private static WpaSupplicantConfigWriter instance;

    public static WpaSupplicantConfigWriter getInstance() {
        if (instance == null) {
            instance = new WpaSupplicantConfigWriter();
        }

        return instance;
    }

    @Override
    public void visit(NetworkConfiguration config) throws KuraException {
        List<NetInterfaceConfig<? extends NetInterfaceAddressConfig>> netInterfaceConfigs = config
                .getModifiedNetInterfaceConfigs();

        for (NetInterfaceConfig<? extends NetInterfaceAddressConfig> netInterfaceConfig : netInterfaceConfigs) {
            if (netInterfaceConfig.getType() == NetInterfaceType.WIFI) {
                // ignore 'mon' interface
                if (netInterfaceConfig.getName().startsWith("mon.")) {
                    continue;
                }

                writeConfig(netInterfaceConfig);
            }
        }

    }

    public void generateTempWpaSupplicantConf(WifiConfig wifiConfig, String interfaceName) throws KuraException {

        try {
            generateWpaSupplicantConf(wifiConfig, interfaceName, TMP_WPA_CONFIG_FILE);
        } catch (Exception e) {
            throw new KuraException(KuraErrorCode.INTERNAL_ERROR, e);
        }
    }

    public void generateTempWpaSupplicantConf() throws KuraException {

        try {
            String fileAsString = readResource(WPA_SUPPLICANT_CONF_RESOURCE);
            copyFile(fileAsString, Paths.get(TMP_WPA_CONFIG_FILE));
        } catch (Exception e) {
            throw KuraException.internalError("Failed to generate wpa_supplicant.conf");
        }
    }

    protected String getFinalConfigFile(String ifaceName) {
        return WpaSupplicantManager.getWpaSupplicantConfigFilename(ifaceName);
    }

    protected String getTemporaryConfigFile() {
        return WPA_TMP_CONFIG_FILE;
    }

    protected String readResource(String path) throws IOException {
        Bundle bundle = FrameworkUtil.getBundle(getClass());
        String s = IOUtil.readResource(bundle, path);

        return s;
    }

    protected void setKuranetProperty(String key, String value) throws IOException, KuraException {
        KuranetConfig.setProperty(key, value);
    }

    private void writeConfig(NetInterfaceConfig<? extends NetInterfaceAddressConfig> netInterfaceConfig)
            throws KuraException {
        String interfaceName = netInterfaceConfig.getName();
        logger.debug("Writing wpa_supplicant config for {}", interfaceName);

        NetInterfaceAddressConfig netInterfaceAddressConfig = ((AbstractNetInterface<?>) netInterfaceConfig)
                .getNetInterfaceAddressConfig();
        if (netInterfaceAddressConfig instanceof WifiInterfaceAddressConfigImpl) {
            List<NetConfig> netConfigs = netInterfaceAddressConfig.getConfigs();
            NetInterfaceStatus netInterfaceStatus = NetInterfaceStatus.netIPv4StatusDisabled;
            WifiMode wifiMode = ((WifiInterfaceAddressConfigImpl) netInterfaceAddressConfig).getMode();
            WifiConfig infraConfig = null;
            WifiConfig adhocConfig = null;

            // Get the wifi configs
            if (netConfigs != null) {
                for (NetConfig netConfig : netConfigs) {
                    if (netConfig instanceof WifiConfig) {
                        if (((WifiConfig) netConfig).getMode() == WifiMode.ADHOC) {
                            adhocConfig = (WifiConfig) netConfig;
                        } else if (((WifiConfig) netConfig).getMode() == WifiMode.INFRA) {
                            infraConfig = (WifiConfig) netConfig;
                        }
                    } else if (netConfig instanceof NetConfigIP4) {
                        netInterfaceStatus = ((NetConfigIP4) netConfig).getStatus();
                    }
                }
            }

            if (netInterfaceStatus == NetInterfaceStatus.netIPv4StatusDisabled
                    || netInterfaceStatus == NetInterfaceStatus.netIPv4StatusUnmanaged) {
                logger.info("Network interface status for {} is {} - not overwriting wpaconfig file", interfaceName,
                        netInterfaceStatus);
                return;
            }

            // Choose which config to write
            WifiConfig wpaSupplicantConfig = chooseConfig(interfaceName, wifiMode, infraConfig, adhocConfig);

            // Write the config
            writeAndMoveFile(interfaceName, wpaSupplicantConfig);
        }
    }

    private WifiConfig chooseConfig(String interfaceName, WifiMode wifiMode, WifiConfig infraConfig,
            WifiConfig adhocConfig) throws KuraException {

        WifiConfig wpaSupplicantConfig = null;

        if (wifiMode == WifiMode.INFRA) {
            if (infraConfig != null) {
                StringBuilder key = new StringBuilder().append("net.interface.").append(interfaceName)
                        .append(".config.wifi.infra.pingAccessPoint");
                try {
                    setKuranetProperty(key.toString(), Boolean.toString(infraConfig.pingAccessPoint()));
                } catch (IOException e) {
                    logger.warn("Error setting KuranetConfig property", e);
                }

                key = new StringBuilder().append("net.interface.").append(interfaceName)
                        .append(".config.wifi.infra.ignoreSSID");
                try {
                    setKuranetProperty(key.toString(), Boolean.toString(infraConfig.ignoreSSID()));
                } catch (IOException e) {
                    logger.warn("Error setting KuranetConfig property", e);
                }
                wpaSupplicantConfig = infraConfig;
            } else {
                logger.debug("Not updating wpa_supplicant config - wifi mode is {} but the infra config is null",
                        wifiMode);
            }
        } else if (wifiMode == WifiMode.ADHOC) {
            if (adhocConfig != null) {
                wpaSupplicantConfig = adhocConfig;
            } else {
                logger.debug("Not updating wpa_supplicant config - wifi mode is {} but the adhoc config is null",
                        wifiMode);
            }
        } else if (wifiMode == WifiMode.MASTER) {
            if (infraConfig != null) {
                wpaSupplicantConfig = infraConfig;
            } else if (adhocConfig != null) {
                wpaSupplicantConfig = adhocConfig;
            } else {
                logger.debug(
                        "Not updating wpa_supplicant config - wifi mode is {} and the infra and adhoc configs are null",
                        wifiMode);
            }
        }

        return wpaSupplicantConfig;
    }

    private void writeAndMoveFile(String interfaceName, WifiConfig wpaSupplicantConfig) throws KuraException {
        try {
            if (wpaSupplicantConfig != null) {
                logger.debug("Writing wifiConfig: {}", wpaSupplicantConfig);
                generateWpaSupplicantConf(wpaSupplicantConfig, interfaceName, getTemporaryConfigFile());
                moveWpaSupplicantConf(interfaceName, getTemporaryConfigFile());
            }
        } catch (Exception e) {
            logger.error("Failed to configure WPA Supplicant");
            throw KuraException.internalError(e);
        }
    }

    /*
     * This method generates the wpa_supplicant configuration file
     */
    private void generateWpaSupplicantConf(WifiConfig wifiConfig, String interfaceName, String configFile)
            throws KuraException, IOException {

        logger.debug("Generating WPA Supplicant Config");

        logger.debug("Store wifiMode driver: {}", wifiConfig.getDriver());
        StringBuilder key = new StringBuilder("net.interface." + interfaceName + ".config.wifi."
                + wifiConfig.getMode().toString().toLowerCase() + ".driver");
        try {
            setKuranetProperty(key.toString(), wifiConfig.getDriver());
        } catch (Exception e) {
            logger.error("Failed to save kuranet config", e);
            throw KuraException.internalError(e);
        }

        String fileAsString;
        if (wifiConfig.getSecurity() == WifiSecurity.SECURITY_WEP) {
            fileAsString = getAndUpdateModeContent(wifiConfig, "/src/main/resources/wifi/wpasupplicant.conf_wep",
                    "/src/main/resources/wifi/wpasupplicant.conf_adhoc_wep", false);

            fileAsString = updateWepPassKey(wifiConfig, fileAsString);
        } else if (wifiConfig.getSecurity() == WifiSecurity.SECURITY_WPA
                || wifiConfig.getSecurity() == WifiSecurity.SECURITY_WPA2
                || wifiConfig.getSecurity() == WifiSecurity.SECURITY_WPA_WPA2) {

            fileAsString = getAndUpdateModeContent(wifiConfig, "/src/main/resources/wifi/wpasupplicant.conf_wpa",
                    "/src/main/resources/wifi/wpasupplicant.conf_adhoc_wpa", true);

            fileAsString = updateWPA(wifiConfig, fileAsString);
        } else if (wifiConfig.getSecurity() == WifiSecurity.SECURITY_NONE
                || wifiConfig.getSecurity() == WifiSecurity.NONE) {

            fileAsString = getAndUpdateModeContent(wifiConfig, "/src/main/resources/wifi/wpasupplicant.conf_open",
                    "/src/main/resources/wifi/wpasupplicant.conf_adhoc_open", false);
        } else {
            throw KuraException.internalError("unsupported security type: " + wifiConfig.getSecurity());
        }

        fileAsString = updateWheelGroup(fileAsString);

        // replace the necessary components
        fileAsString = fileAsString.replaceFirst("KURA_MODE",
                Integer.toString(getSupplicantMode(wifiConfig.getMode())));

        fileAsString = updateSsid(wifiConfig, fileAsString);

        fileAsString = updateBgScan(wifiConfig, fileAsString);

        fileAsString = fileAsString.replaceFirst("KURA_SCANFREQ", getScanFrequenciesMHz(wifiConfig.getChannels()));

        // everything is set and we haven't failed - write the file
        copyFile(fileAsString, Paths.get(configFile));
    }

    private String getAndUpdateModeContent(WifiConfig wifiConfig, String infraResource, String adhocResource,
            boolean replaceAdhocPairwise) throws IOException, KuraException {

        String fileAsString;

        if (wifiConfig.getMode() == WifiMode.INFRA) {
            fileAsString = readResource(infraResource);
        } else if (wifiConfig.getMode() == WifiMode.ADHOC) {
            fileAsString = readResource(adhocResource);
            fileAsString = fileAsString.replaceFirst("KURA_FREQUENCY",
                    Integer.toString(WpaSupplicantUtil.convChannelToFrequency(wifiConfig.getChannels()[0])));

            if (replaceAdhocPairwise) {
                fileAsString = fileAsString.replaceFirst("KURA_PAIRWISE", "NONE");
            }
        } else {
            throw KuraException
                    .internalError("Failed to generate wpa_supplicant.conf -- Invalid mode: " + wifiConfig.getMode());
        }
        return fileAsString;
    }

    private String updateWPA(WifiConfig wifiConfig, String fileAsString) throws KuraException {
        String result = fileAsString;
        String passKey = new String(wifiConfig.getPasskey().getPassword());
        if (passKey.trim().length() > 0) {
            if (passKey.length() < 8 || passKey.length() > 63) {
                throw KuraException.internalError(
                        "the WPA passphrase (passwd) must be between 8 (inclusive) and 63 (inclusive) characters in length: "
                                + passKey);
            } else {
                result = result.replaceFirst("KURA_PASSPHRASE", Matcher.quoteReplacement(passKey.trim()));
            }
        } else {
            throw KuraException.internalError("the passwd can not be null");
        }

        String replacement;
        if (wifiConfig.getSecurity() == WifiSecurity.SECURITY_WPA) {
            replacement = "WPA";
        } else if (wifiConfig.getSecurity() == WifiSecurity.SECURITY_WPA2) {
            replacement = "RSN";
        } else {
            replacement = "WPA RSN";
        }
        result = result.replaceFirst("KURA_PROTO", replacement);

        if (wifiConfig.getPairwiseCiphers() != null) {
            replacement = WifiCiphers.toString(wifiConfig.getPairwiseCiphers());
        } else {
            replacement = "CCMP TKIP";
        }
        result = result.replaceFirst("KURA_PAIRWISE", replacement);

        if (wifiConfig.getGroupCiphers() != null) {
            replacement = WifiCiphers.toString(wifiConfig.getGroupCiphers());
        } else {
            replacement = "";
        }
        result = result.replaceFirst("KURA_GROUP", replacement);

        return result;
    }

    private String updateWheelGroup(String fileAsString) {
        String result = fileAsString;

        result = result.replaceFirst("ctrl_interface_group=wheel", "#ctrl_interface_group=wheel");

        return result;
    }

    private String updateSsid(WifiConfig wifiConfig, String fileAsString) throws KuraException {
        String result;

        if (wifiConfig.getSSID() != null) {
            result = fileAsString.replaceFirst("KURA_ESSID", Matcher.quoteReplacement(wifiConfig.getSSID()));
        } else {
            throw KuraException.internalError("the essid can not be null");
        }

        return result;
    }

    private String updateBgScan(WifiConfig wifiConfig, String fileAsString) {
        String result;

        if (wifiConfig.getBgscan() != null) {
            result = fileAsString.replaceFirst("KURA_BGSCAN", wifiConfig.getBgscan().toString());
        } else {
            result = fileAsString.replaceFirst("KURA_BGSCAN", "");
        }

        return result;
    }

    private String updateWepPassKey(WifiConfig wifiConfig, String fileAsString) throws KuraException {
        String passKey = new String(wifiConfig.getPasskey().getPassword());
        String exceptionHex = "the WEP key (passwd) must be all HEX characters (0, 1, 2, 3, 4, 5, 6, 7, 8, 9, a, b, c, d, e, and f";
        if (passKey.length() == 10) {
            // check to make sure it is all hex
            try {
                Long.parseLong(passKey, 16);
            } catch (Exception e) {
                throw KuraException.internalError(exceptionHex);
            }
        } else if (passKey.length() == 26) {
            String part1 = passKey.substring(0, 13);
            String part2 = passKey.substring(13);

            try {
                Long.parseLong(part1, 16);
                Long.parseLong(part2, 16);
            } catch (Exception e) {
                throw KuraException.internalError(exceptionHex);
            }
        } else if (passKey.length() == 32) {
            String part1 = passKey.substring(0, 10);
            String part2 = passKey.substring(10, 20);
            String part3 = passKey.substring(20);
            try {
                Long.parseLong(part1, 16);
                Long.parseLong(part2, 16);
                Long.parseLong(part3, 16);
            } catch (Exception e) {
                throw KuraException.internalError(exceptionHex);
            }
        } else if (passKey.length() == 5 || passKey.length() == 13 || passKey.length() == 16) {
            // 5, 13, or 16 ASCII characters
            passKey = toHex(passKey);
        } else {
            throw KuraException.internalError("the WEP key (passwd) must be 10, 26, or 32 HEX characters in length");
        }

        // since we're here - save the password
        return fileAsString.replaceFirst("KURA_WEP_KEY", passKey);
    }

    private void moveWpaSupplicantConf(String ifaceName, String configFile) throws KuraException {
        File outputFile = new File(configFile);
        File wpaConfigFile = new File(getFinalConfigFile(ifaceName));
        try {
            if (!FileUtils.contentEquals(outputFile, wpaConfigFile)) {
                if (outputFile.renameTo(wpaConfigFile)) {
                    logger.trace("Successfully wrote wpa_supplicant config file");
                } else {
                    logger.error("Failed to write wpa_supplicant config file");
                    throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR,
                            "error while building up new configuration file for wpa_supplicant config");
                }
            } else {
                logger.info("Not rewriting wpa_supplicant.conf file because it is the same");
            }
        } catch (IOException e) {
            throw new KuraException(KuraErrorCode.CONFIGURATION_ERROR,
                    "error while building up new configuration file for wpa_supplicant config");
        }
    }

    /*
     * This method converts the supplied string to hex
     */
    private String toHex(String s) {
        if (s == null) {
            return null;
        }
        byte[] raw = s.getBytes();

        StringBuilder hex = new StringBuilder(2 * raw.length);
        for (byte element : raw) {
            hex.append(HEXES.charAt((element & 0xF0) >> 4)).append(HEXES.charAt(element & 0x0F));
        }
        return hex.toString();
    }

    /*
     * This method returns the supplicant mode
     */
    private static int getSupplicantMode(WifiMode mode) {
        if (mode == WifiMode.ADHOC) {
            return WpaSupplicantUtil.MODE_IBSS;
        } else if (mode == WifiMode.MASTER) {
            return WpaSupplicantUtil.MODE_AP;
        } else {
            return WpaSupplicantUtil.MODE_INFRA;
        }
    }

    /*
     * This method returns a list of frequencies based on a list of channels
     */
    private String getScanFrequenciesMHz(int[] channels) {
        StringBuilder sbFrequencies = new StringBuilder();
        if (channels != null && channels.length > 0) {
            for (int i = 0; i < channels.length; i++) {
                sbFrequencies.append(WpaSupplicantUtil.convChannelToFrequency(channels[i]));
                if (i < channels.length - 1) {
                    sbFrequencies.append(' ');
                }
            }
        } else {
            for (int i = 1; i <= 13; i++) {
                sbFrequencies.append(WpaSupplicantUtil.convChannelToFrequency(i));
                if (i < 13) {
                    sbFrequencies.append(' ');
                }
            }
        }

        return sbFrequencies.toString();
    }

}
