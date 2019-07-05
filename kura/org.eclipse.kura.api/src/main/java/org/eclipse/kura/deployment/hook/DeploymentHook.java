/*******************************************************************************
 * Copyright (c) 2017 Eurotech and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.deployment.hook;

import java.util.Map;

import org.eclipse.kura.KuraException;
import org.osgi.annotation.versioning.ConsumerType;

/**
 * <p>
 * This interface can be implemented by a client to define a DEPLOY-V2 Cloudlet hook.
 * {@link DeploymentHook} instances can be used to customize the behavior of the DEPLOY-V2
 * Cloudlet by executing user-defined code at specific stages of deployment requests.
 * </p>
 * 
 * <p>
 * A {@link DeploymentHook} implementation must be registered as an OSGi service and it is identified by the
 * value of the {@code kura.service.pid} service property.
 * </p>
 * 
 * <p>
 * In order for a registered hook to be used for a particular request the following conditions must
 * be verified:
 * </p>
 * 
 * <ul>
 * <li>The request must provide a metric named {@code request.type} of string type.</li>
 * <li>The configuration of the DEPLOY-V2 cloudlet must contain a mapping beetween the received {@code request.type}
 * and the {@code kura.service.pid} of a registered {@link DeploymentHook} implementation.</li>
 * </ul>
 * 
 * <p>
 * The associations between {@code request.type} and hook {@code kura.service.pid} are maintained in the configuration
 * of the DEPLOY-V2 Cloudlet and are modifiable using the Kura WEB Ui.
 * </p>
 * 
 * <p>
 * The {@link DeploymentHook} methods can be executed at different stages of the {@code /EXEC/download} and
 * {@code /EXEC/install} as specified below.
 * </p>
 * 
 * <table border=1>
 * <tr>
 * <th>Method</th>
 * <th>/EXEC/download</th>
 * <th>/EXEC/install</th>
 * </tr>
 * <tr>
 * <td>{@link DeploymentHook#preDownload(RequestContext, Map) preDownload()}</td>
 * <td>Called immediately after a valid request is received by the Cloudlet.</td>
 * <td>Never called.</td>
 * </tr>
 * <tr>
 * <td>{@link DeploymentHook#postDownload(RequestContext, Map) postDownload()}</td>
 * <td>Called immediately after the download is completed successfully, or immediately after the
 * {@code preDownload} method is called if the requested file already exists.
 * When this method is called the requested file should be available on the device.</td>
 * <td>Called immediately after a valid request is received by the Cloudlet, and only if the requested
 * file is available on the device</td>
 * </tr>
 * <tr>
 * <td>{@link DeploymentHook#postInstall(RequestContext, Map) postInstall()}</td>
 * <td>Called immediately after the downloaded deployment package/script has been successfully
 * installed/executed.</td>
 * <td>Called immediately after the downloaded deployment package/script has been successfully
 * installed/executed.</td>
 * </tr>
 * </table>
 * 
 * <p>
 * If any of the methods specified by this interface throws an exception, the current request will be aborted.
 * </p>
 * 
 * @since 1.3
 */
@ConsumerType
public interface DeploymentHook {

    /**
     * This method is called at the {@code preDownload} phase, see class description for more details.
     * 
     * @param context
     *            a {@link RequestContext} instance representing the current DEPLOY-V2 request
     * @param properties
     *            an hook-specific map of properties
     * @throws KuraException
     *             if an exception is thrown the current request will be aborted
     */
    public void preDownload(RequestContext context, Map<String, Object> properties) throws KuraException;

    /**
     * This method is called at the {@code postDownload} phase, see class description for more details.
     * 
     * @param context
     *            a {@link RequestContext} instance representing the current DEPLOY-V2 request
     * @param properties
     *            an hook-specific map of properties
     * @throws KuraException
     *             if an exception is thrown the current request will be aborted
     */
    public void postDownload(RequestContext context, Map<String, Object> properties) throws KuraException;

    /**
     * This method is called at the {@code postInstall} phase, see class description for more details.
     * 
     * @param context
     *            a {@link RequestContext} instance representing the current DEPLOY-V2 request
     * @param properties
     *            an hook-specific map of properties
     * @throws KuraException
     *             if an exception is thrown the current request will be aborted
     */
    public void postInstall(RequestContext context, Map<String, Object> properties) throws KuraException;

}
