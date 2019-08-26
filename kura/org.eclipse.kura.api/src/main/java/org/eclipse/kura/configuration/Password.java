/*******************************************************************************
 * Copyright (c) 2011, 2020 Eurotech and/or its affiliates and others
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
package org.eclipse.kura.configuration;

import java.util.Arrays;

import org.osgi.annotation.versioning.ProviderType;

/**
 * @noextend This class is not intended to be subclassed by clients.
 */
@ProviderType
public class Password {

    private char[] passwordVal;

    public Password(String password) {
        super();
        if (password != null) {
            this.passwordVal = password.toCharArray();
        }
    }

    public Password(char[] password) {
        super();
        this.passwordVal = password;
    }

    public char[] getPassword() {
        return this.passwordVal;
    }

    @Override
    public String toString() {
        return new String(this.passwordVal);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(this.value);
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
        Password other = (Password) obj;
        if (!Arrays.equals(this.value, other.value)) {
            return false;
        }
        return true;
    }

}
