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

import java.util.List;
import java.util.Optional;

public interface PersistedRequestService {

    public Optional<Request> getRequest(String id);

    public List<Request> getRequests();

    public void registerEventStream(RequestEventStream stream);

    public void unregisterEventStream(RequestEventStream stream);
}
