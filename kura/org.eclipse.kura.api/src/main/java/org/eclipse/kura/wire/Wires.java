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

import static org.eclipse.kura.Preconditions.checkNull;

import java.sql.Timestamp;
import java.util.List;

import org.eclipse.kura.KuraRuntimeException;
import org.eclipse.kura.annotation.Nullable;
import org.eclipse.kura.type.TypedValue;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.util.position.Position;

/**
 * The Class Wires is an utility class to provide quick operations for Kura
 * Wires.
 */
public final class Wires {
	
	/** Constructor */
	private Wires() {
		// Static Factory Methods container. No need to instantiate.
	}

	/**
	 * Instantiates a new wire configuration.
	 *
	 * @param emitterName
	 *            the Wire Emitter name
	 * @param receiverName
	 *            the Wire Receiver name
	 * @param filter
	 *            the filter
	 * @return the Wire Configuration
	 * @throws KuraRuntimeException
	 *             if any of the arguments is null
	 */
	public static WireConfiguration newWireConfiguration(final String emitterName, final String receiverName,
			@Nullable final String filter) {
		return new WireConfiguration(emitterName, receiverName, filter);
	}

	/**
	 * Instantiates a new wire configuration.
	 *
	 * @param emitterName
	 *            the Wire Emitter name
	 * @param receiverName
	 *            the Wire Receiver name
	 * @param filter
	 *            the filter
	 * @param created
	 *            the created flag signifying whether Wire Admin has already
	 *            created the wire between the wire emitter and a wire receiver
	 * @return the Wire Configuration
	 * @throws KuraRuntimeException
	 *             if any of the arguments is null
	 */
	public static WireConfiguration newWireConfiguration(final String emitterName, final String receiverName,
			@Nullable final String filter, final boolean created) {
		return new WireConfiguration(emitterName, receiverName, filter, created);
	}

	/**
	 * Returns new instance of Wire Configuration from json object provided
	 *
	 * @param jsonWire
	 *            the json object representing the wires
	 * @return the wire configuration
	 * @throws JSONException
	 *             the JSON exception
	 * @throws KuraRuntimeException
	 *             if the json object instance passed as argument is null
	 */
	public static WireConfiguration newWireConfigurationFromJson(final JSONObject jsonWire) throws JSONException {
		checkNull(jsonWire, "JSON Object cannot be null");
		final String emitter = jsonWire.getString("p");
		final String receiver = jsonWire.getString("c");
		final String filter = jsonWire.optString("f");
		return new WireConfiguration(emitter, receiver, filter);
	}

	/**
	 * Instantiates a new wire envelope.
	 *
	 * @param emitterName
	 *            the wire emitter name
	 * @param wireRecords
	 *            the wire records
	 * @return the Wire envelope
	 */
	public static WireEnvelope newWireEnvelope(final String emitterName, final List<WireRecord> wireRecords) {
		checkNull(emitterName, "Emitter name cannot be null");
		checkNull(wireRecords, "List of wire records cannot null");

		return new WireEnvelope(emitterName, wireRecords);
	}

	/**
	 * Prepares new wire field.
	 *
	 * @param name
	 *            the name
	 * @param value
	 *            the value
	 * @return the wire field
	 */
	public static WireField newWireField(final String name, final TypedValue<?> value) {
		return new WireField(name, value);
	}

	/**
	 * Prepares new wire record.
	 *
	 * @param timestamp
	 *            the timestamp
	 * @param fields
	 *            the wire fields
	 * @return the wire record
	 */
	public static WireRecord newWireRecord(final Timestamp timestamp, final List<WireField> fields) {
		return new WireRecord(timestamp, fields);
	}

	/**
	 * Prepares new wire record.
	 *
	 * @param timestamp
	 *            the timestamp
	 * @param position
	 *            the position
	 * @param fields
	 *            the wire fields
	 * @return the wire record
	 */
	public static WireRecord newWireRecord(final Timestamp timestamp, @Nullable final Position position,
			final List<WireField> fields) {
		return new WireRecord(timestamp, position, fields);
	}

	/**
	 * Prepares new wire record.
	 *
	 * @param fields
	 *            the wire fields
	 * @return the wire record
	 */
	public static WireRecord newWireRecord(final WireField... fields) {
		return new WireRecord(fields);
	}

	/**
	 * Returns a Wire Support instance of the provided wire component
	 *
	 * @param wireComponent
	 *            the wire component
	 * @return the wire support instance
	 */
	public static WireSupport newWireSupport(final WireComponent wireComponent) {
		return new WireSupport(wireComponent);
	}

}
