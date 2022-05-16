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
 *******************************************************************************/
package org.eclipse.kura.certificate.enrollment;

import java.security.cert.Certificate;

import org.eclipse.kura.KuraException;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Provides a list of APIs allowing the system to perform an enrollment in a Certificate Authority
 *
 * @since 3.0
 */

@ProviderType
public interface EnrollmentService {

    /**
     * 
     * Perform the enrollment of the system with a configured Certificate Authority.
     * 
     * @throws KuraException
     *             if it is impossible to enroll the system.
     */
    public void enroll() throws KuraException;

    /**
     * Renews the client certificate creating a new keypair and a new CSR submitted to the CA.
     * 
     * @throws KuraException
     *             it it is impossible to renew the system certificate
     */
    public void renew() throws KuraException;

    /**
     * 
     * Get the Certificate Authority <code>Certificate</code> stored in the relative <code>Keystore</code>
     * 
     * @return returns the Certificate Authority <code>Certificate</code>
     * @throws KuraException
     *             if it is impossible to retrieve the certificate.
     */
    public Certificate getCACertificate() throws KuraException;

    /**
     * Get the Client <code>Certificate</code> stored in the relative <code>Keystore</code>
     * 
     * @return returns the Client <code>Certificate</code> obtained by the Certificate Autorithy
     * @throws KuraException
     *             if it is impossible to retrieve the certificate.
     */
    public Certificate getClientCertificate() throws KuraException;

    /**
     * Force the update of Certificate Authority certificate if a newest is available.
     * 
     * @throws KuraException
     *             if it is impossible to perform the request to the server.
     */
    public void forceCACertificateRollover() throws KuraException;

    /**
     * 
     * Check if the system in enrolled or not, that is the presence or not of the client certificate.
     * 
     * @return true if the system in enrolled, false otherwise.
     * @throws KuraException
     *             if it is impossible to query the keystore for the certificate availability.
     */
    public boolean isEnrolled() throws KuraException;

}
