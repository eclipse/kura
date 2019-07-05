/*******************************************************************************
 * Copyright (c) 2018 Eurotech and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.deployment.hook;

import org.eclipse.kura.KuraException;

public interface Request {

    public void setEventStream(final RequestEventStream requestEventStream);

    public String getId();

    public void makePersistent() throws KuraException;
}
