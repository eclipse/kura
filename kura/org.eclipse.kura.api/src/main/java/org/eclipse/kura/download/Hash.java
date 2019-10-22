/*******************************************************************************
 * Copyright (c) 2019 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 *******************************************************************************/
package org.eclipse.kura.download;

import static java.util.Objects.requireNonNull;

import java.security.MessageDigest;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Represents an hash value returned by a specific algorithm, as specified by the {@link MessageDigest} class.
 *
 * @since 2.2
 * @noimplement This interface is not intended to be implemented by clients.
 */
@ProviderType
public class Hash {

    private final String algorithm;
    private final String value;

    /**
     * Creates a new instance.
     * 
     * @param algorithm
     *            the algorithm that generated the hash value.
     * @param value
     *            the hash value.
     */
    public Hash(String algorithm, String value) {
        this.algorithm = requireNonNull(algorithm, "algorithm cannot be null");
        this.value = requireNonNull(value, "value cannot be null");
    }

    /**
     * Returns the algorithm that generated the hash value.
     * 
     * @return the algorithm that generated the hash value.
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * Returns the hash value, encoded as an hexadecimal string.
     * 
     * @return the hash value, encoded as an hexadecimal string.
     */
    public String getValue() {
        return value;
    }

}
