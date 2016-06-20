/**
 * Copyright (c) 2011, 2016 Eurotech and/or its affiliates
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Eurotech
 *   Amit Kumar Mondal (admin@amitinside.com)
 */
package org.eclipse.kura.wire;

import java.util.List;

/**
 * The Interface WireRecordFilter is responsible to filter out the provided wire
 * records
 */
public interface WireRecordFilter {

	/**
	 * filter the list of provided wire records
	 *
	 * @return the list of filtered wire records
	 */
	public List<WireRecord> filter();

}
