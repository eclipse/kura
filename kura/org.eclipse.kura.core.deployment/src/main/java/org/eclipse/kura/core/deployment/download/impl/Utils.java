/*******************************************************************************
 * Copyright (c) 2019 Eurotech and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.core.deployment.download.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.eclipse.kura.core.deployment.install.DeploymentPackageInstallOptions;
import org.eclipse.kura.core.deployment.util.FileUtilities;
import org.eclipse.kura.download.Hash;

public class Utils {

    private Utils() {
    }

    public static boolean verifyDownload(final File dest, final Hash checksum)
            throws NoSuchAlgorithmException, IOException {

        if (!dest.exists()) {
            return false;
        }

        final MessageDigest digest = MessageDigest.getInstance(checksum.getAlgorithm());

        final byte[] buf = new byte[4096];

        try (final DigestInputStream in = new DigestInputStream(new FileInputStream(dest), digest)) {
            while (in.read(buf) != -1)
                ;
        }

        final String providedChecksum = checksum.getValue();
        final byte[] computedChecksum = digest.digest();

        for (int i = 0; i < computedChecksum.length; i++) {
            final int next = Integer.parseInt(providedChecksum.substring(i * 2, i * 2 + 2), 16);
            if ((computedChecksum[i] & 0xff) != next) {
                return false;
            }
        }

        return providedChecksum.length() == computedChecksum.length * 2;
    }

    // File Management
    public static File getDpDownloadFile(DeploymentPackageInstallOptions options) throws IOException {
        String downloadDirectory = options.getDownloadDirectory();
        String packageFilename;
        if (!options.getSystemUpdate()) {
            String dpName = FileUtilities.getFileName(options.getDpName(), options.getDpVersion(), ".dp");
            packageFilename = new StringBuilder().append(downloadDirectory).append(File.separator).append(dpName)
                    .toString();
        } else {
            String shName = FileUtilities.getFileName(options.getDpName(), options.getDpVersion(), ".sh");
            packageFilename = new StringBuilder().append(downloadDirectory).append(File.separator).append(shName)
                    .toString();
        }

        File localFolder = new File(downloadDirectory);
        String fileName = validateFileName(packageFilename, localFolder.getPath());
        return new File(fileName);
    }

    public static boolean deleteDownloadedFile(DeploymentPackageInstallOptions options) throws IOException {
        File file = getDpDownloadFile(options);

        if (file != null && file.exists() && file.isFile()) {
            return file.delete();
        }

        return false;
    }

    private static String validateFileName(String destFileName, String intendedDir) throws IOException {
        File destFile = new File(destFileName);
        String filePath = destFile.getCanonicalPath();

        File iD = new File(intendedDir);
        String canonicalID = iD.getCanonicalPath();

        if (filePath.startsWith(canonicalID)) {
            return filePath;
        } else {
            throw new IOException();
        }
    }
}
