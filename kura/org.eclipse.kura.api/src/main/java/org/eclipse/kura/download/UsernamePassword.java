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

import java.util.Optional;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Represents an username and password.
 * 
 * @since 2.2
 * @noimplement This interface is not intended to be implemented by clients.
 */
@ProviderType
public class UsernamePassword implements Credentials {

    private final char[] username;
    private final char[] password;

    /**
     * Creates a new instance.
     * 
     * @param username
     *            the user name.
     * @param password
     *            the password.
     */
    public UsernamePassword(char[] username, char[] password) {
        this.username = Optional.ofNullable(username).orElse(new char[0]);
        this.password = Optional.ofNullable(password).orElse(new char[0]);
    }

    /**
     * Returns the user name.
     * 
     * @return the user name.
     */
    public char[] getUsername() {
        return username;
    }

    /**
     * Returns the password.
     * 
     * @return the password.
     */
    public char[] getPassword() {
        return password;
    }

    /**
     * Clears username and password values.
     */
    public void erase() {
        for (int i = 0; i < password.length; i++) {
            password[i] = 0;
        }

        for (int i = 0; i < username.length; i++) {
            username[i] = 0;
        }
    }

}
