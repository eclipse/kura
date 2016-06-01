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
package org.eclipse.kura.device;

import static org.eclipse.kura.Preconditions.checkNull;

import java.util.Map;

import org.eclipse.kura.KuraRuntimeException;
import org.eclipse.kura.annotation.NotThreadSafe;
import org.eclipse.kura.type.TypedValue;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableMap;

/**
 * This class represents records needed for read, write or a monitor operation
 * on the provided channel configuration by the Kura specific device driver.
 */
@NotThreadSafe
public final class DriverRecord implements Comparable<DriverRecord> {

	/**
	 * Provided channel configuration to perform read or write or monitor
	 * operation.
	 */
	private Map<String, Object> m_channelConfig;

	/**
	 * Channel Name as associated with the device.
	 */
	private String m_channelName;

	/**
	 * Represents a driver specific flag which signifies the status of the read
	 * or write or monitor operation.
	 */
	private DriverFlag m_driverFlag;

	/** Represents the timestamp of the operation performed. */
	private long m_timestamp;

	/**
	 * Represents the value as read by the driver during a read or a monitor
	 * operation. It can also represent the value which needs to be written by
	 * the driver to the actual device.
	 */
	private TypedValue<?> m_value;

	/**
	 * Instantiates a new driver record.
	 *
	 * @param channelName
	 *            the channel name
	 * @throws KuraRuntimeException
	 *             if the argument is null
	 */
	public DriverRecord(final String channelName) {
		checkNull(channelName, "Channel name cannot be null");
		this.m_channelName = channelName;
	}

	/** {@inheritDoc} */
	@Override
	public int compareTo(final DriverRecord otherDriverRecord) {
		checkNull(otherDriverRecord, "Provided driver record to compare is null");
		return ComparisonChain.start().compare(this.m_channelName, otherDriverRecord.getChannelName())
				.compare(this.m_value, otherDriverRecord.getValue())
				.compare(this.m_driverFlag, otherDriverRecord.getDriverFlag())
				.compare(this.m_timestamp, otherDriverRecord.getTimestamp()).result();
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof DriverRecord) {
			final DriverRecord rec = (DriverRecord) obj;
			return Objects.equal(rec.getChannelName(), this.m_channelName)
					&& Objects.equal(rec.getValue(), this.m_value)
					&& Objects.equal(rec.getDriverFlag(), this.m_driverFlag)
					&& Objects.equal(rec.getTimestamp(), this.m_timestamp);
		}
		return false;
	}

	/**
	 * Returns the channel configuration as provided.
	 *
	 * @return the channel configuration
	 */
	public Map<String, Object> getChannelConfig() {
		return this.m_channelConfig;
	}

	/**
	 * Gets the channel name.
	 *
	 * @return the channel name
	 */
	public String getChannelName() {
		return this.m_channelName;
	}

	/**
	 * Returns the driver flag.
	 *
	 * @return the driver flag
	 */
	public DriverFlag getDriverFlag() {
		return this.m_driverFlag;
	}

	/**
	 * Returns the associated timestamp.
	 *
	 * @return the timestamp
	 */
	public long getTimestamp() {
		return this.m_timestamp;
	}

	/**
	 * Returns the associated value.
	 *
	 * @return the value
	 */
	public TypedValue<?> getValue() {
		return this.m_value;
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		return Objects.hashCode(this.m_channelName, this.m_value, this.m_driverFlag, this.m_timestamp);
	}

	/**
	 * Sets the channel configuration as provided.
	 *
	 * @param channelConfig
	 *            the channel config
	 * @throws KuraRuntimeException
	 *             if the argument is null
	 */
	public void setChannelConfig(final Map<String, Object> channelConfig) {
		checkNull(channelConfig, "Channel configuration cannot be null");
		this.m_channelConfig = ImmutableMap.copyOf(channelConfig);
	}

	/**
	 * Sets the channel name.
	 *
	 * @param channelName
	 *            the new channel name
	 * @throws KuraRuntimeException
	 *             if the argument is null
	 */
	public void setChannelName(final String channelName) {
		checkNull(channelName, "Channel name cannot be null");
		this.m_channelName = channelName;
	}

	/**
	 * Sets the driver flag as provided.
	 *
	 * @param driverFlag
	 *            the new driver flag
	 * @throws KuraRuntimeException
	 *             if the argument is null
	 */
	public void setDriverFlag(final DriverFlag driverFlag) {
		checkNull(driverFlag, "Driver flag cannot be null");
		this.m_driverFlag = driverFlag;
	}

	/**
	 * Sets the timestamp as provided.
	 *
	 * @param timetstamp
	 *            the new timestamp
	 */
	public void setTimestamp(final long timestamp) {
		this.m_timestamp = timestamp;
	}

	/**
	 * Sets the value as provided.
	 *
	 * @param value
	 *            the new value
	 * @throws KuraRuntimeException
	 *             if the argument is null
	 */
	public void setValue(final TypedValue<?> value) {
		checkNull(value, "Value type cannot be null");
		this.m_value = value;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("channel_name", this.m_channelName)
				.add("channel_config", this.m_channelConfig).add("driver_flag", this.m_driverFlag)
				.add("timestamp", this.m_timestamp).add("value", this.m_value).toString();
	}

}
