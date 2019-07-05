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

public interface RequestEventStream {

    public String getId();

    public int getEventCount();

    public Event getEvent(int index);

    public void destroy();

    public void registerListener(final RequestEventStreamListener listener);

    public void unregisterListener(final RequestEventStreamListener listener);
}
