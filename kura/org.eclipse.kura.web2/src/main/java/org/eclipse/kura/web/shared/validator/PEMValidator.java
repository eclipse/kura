/*******************************************************************************
 * Copyright (c) 2021 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Eurotech
 *******************************************************************************/
package org.eclipse.kura.web.shared.validator;

public class PEMValidator extends RegexValidator {

    private static final String PEM_REGEX = "^-{5}BEGIN CERTIFICATE-{5}\n[\\W\\w]*?-{5}END CERTIFICATE-{5}$";

    public PEMValidator(String message) {
        super(PEM_REGEX, message);
    }

}
