/*******************************************************************************
 * Copyright (c) 2022 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Eurotech
 ******************************************************************************/

package org.eclipse.kura.ai.triton.server;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.Security;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPPBEEncryptedData;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEDataDecryptorFactoryBuilder;
import org.bouncycastle.util.io.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TritonServerEncryptionUtils {

    private static final Logger logger = LoggerFactory.getLogger(TritonServerEncryptionUtils.class);

    private TritonServerEncryptionUtils() {
        // TODO
    }

    protected static void createDecryptionFolder(String folderPath) throws IOException {
        Path targetFolderPath = Paths.get(folderPath);

        if (Files.exists(targetFolderPath)) {
            throw new IOException("Target path " + targetFolderPath.toString() + " already exists");
        }

        logger.debug("Creating decryption folder at path: {}", folderPath);

        Files.createDirectories(targetFolderPath);

        Set<PosixFilePermission> permissions = new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        Files.setPosixFilePermissions(targetFolderPath, permissions);
    }

    protected static void decryptModel(String password, String inputFilePath, String outputFilePath)
            throws IOException {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        InputStream in = new BufferedInputStream(new FileInputStream(inputFilePath));
        OutputStream out = new FileOutputStream(outputFilePath);
        try {
            decryptFile(in, out, password.toCharArray());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (PGPException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        in.close();
    }

    protected static void unzipModel(String inputFilePath, String outputFolder) {
        // TODO
    }

    protected static void deleteModel(String modelName) {
        // TODO
    }

    private static void decryptFile(InputStream inStream, OutputStream outStream, char[] passPhrase)
            throws IOException, PGPException {
        inStream = PGPUtil.getDecoderStream(inStream);

        JcaPGPObjectFactory pgpF = new JcaPGPObjectFactory(inStream);
        PGPEncryptedDataList enc;
        Object currentObj = pgpF.nextObject();

        if (currentObj instanceof PGPEncryptedDataList) {
            enc = (PGPEncryptedDataList) currentObj;
        } else {
            enc = (PGPEncryptedDataList) pgpF.nextObject();
        }

        PGPPBEEncryptedData pbe = (PGPPBEEncryptedData) enc.get(0);

        InputStream clear = pbe.getDataStream(new JcePBEDataDecryptorFactoryBuilder(
                new JcaPGPDigestCalculatorProviderBuilder().setProvider("BC").build()).setProvider("BC")
                        .build(passPhrase));

        JcaPGPObjectFactory pgpFact = new JcaPGPObjectFactory(clear);

        currentObj = pgpFact.nextObject();
        if (currentObj instanceof PGPCompressedData) {
            PGPCompressedData cData = (PGPCompressedData) currentObj;

            pgpFact = new JcaPGPObjectFactory(cData.getDataStream());

            currentObj = pgpFact.nextObject();
        }

        PGPLiteralData ld = (PGPLiteralData) currentObj;
        InputStream unc = ld.getInputStream();

        Streams.pipeAll(unc, outStream);
        outStream.close();

        if (pbe.isIntegrityProtected()) {
            if (!pbe.verify()) {
                logger.error("File failed integrity check");
            } else {
                logger.info("File integrity check passed");
            }
        } else {
            logger.info("No file integrity check");
        }
    }
}
