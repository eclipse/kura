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
package org.eclipse.kura.device.cloudlet;

import static org.eclipse.kura.Preconditions.checkNull;

import java.util.List;
import java.util.Map;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.KuraRuntimeException;
import org.eclipse.kura.cloud.CloudClient;
import org.eclipse.kura.cloud.CloudService;
import org.eclipse.kura.cloud.Cloudlet;
import org.eclipse.kura.cloud.CloudletTopic;
import org.eclipse.kura.device.Channel;
import org.eclipse.kura.device.Device;
import org.eclipse.kura.device.DeviceRecord;
import org.eclipse.kura.device.Devices;
import org.eclipse.kura.device.internal.BaseDevice;
import org.eclipse.kura.device.internal.DeviceConfiguration;
import org.eclipse.kura.localization.DeviceCloudletMessages;
import org.eclipse.kura.localization.LocalizationAdapter;
import org.eclipse.kura.message.KuraRequestPayload;
import org.eclipse.kura.message.KuraResponsePayload;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.type.TypedValues;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.component.ComponentContext;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

/**
 * The Class DeviceCloudlet is used to provide MQTT read/write operations on the
 * device. The application id is configured as {@code Devicelet}.
 *
 * The available {@code GET} commands are as follows
 * <ul>
 * <li>/devices</li> : to retrieve all the devices
 * <li>/devices/device_name</li> : to retrieve all the channels of the provided
 * device name
 * <li>/devices/device_name/channel_name</li> : to retrieve the value of the
 * specified channel from the provided device name
 * </ul>
 *
 * The available {@code PUT} commands are as follows
 * <ul>
 * <li>/devices/device_name/channel_name</li> : to write the provided
 * {@code value} in the payload to the specified channel of the provided device
 * name. The payload must also include the {@code type} of the {@code value}
 * provided.
 * </ul>
 *
 * The {@code type} key in the request payload can be one of the following
 * (case-insensitive)
 * <ul>
 * <li>INTEGER</li>
 * <li>LONG</li>
 * <li>STRING</li>
 * <li>BOOLEAN</li>
 * <li>BYTE</li>
 * <li>SHORT</li>
 * <li>DOUBLE</li>
 * </ul>
 *
 * @see Cloudlet
 * @see CloudClient
 * @see DeviceCloudlet#doGet(CloudletTopic, KuraRequestPayload,
 *      KuraResponsePayload)
 * @see DeviceCloudlet#doPut(CloudletTopic, KuraRequestPayload,
 *      KuraResponsePayload)
 */
public final class DeviceCloudlet extends Cloudlet {

	/** Application Identifier for Cloudlet. */
	private static final String APP_ID = "Devicelet";

	/** The Logger instance. */
	private static final Logger s_logger = LoggerFactory.getLogger(DeviceCloudlet.class);

	/** Localization Resource */
	private static final DeviceCloudletMessages s_messages = LocalizationAdapter.adapt(DeviceCloudletMessages.class);

	/** The map of devices present in the OSGi service registry. */
	private Map<String, Device> m_devices;

	/** Device Tracker Customizer */
	private DeviceTrackerCustomizer m_deviceTrackerCustomizer;

	/** Device Tracker. */
	private ServiceTracker<Device, Device> m_serviceTracker;

	/**
	 * Instantiates a new device cloudlet.
	 */
	public DeviceCloudlet() {
		super(APP_ID);
	}

	/** {@inheritDoc} */
	@Override
	protected synchronized void activate(final ComponentContext componentContext) {
		s_logger.debug(s_messages.activating());
		super.activate(componentContext);
		try {
			this.m_deviceTrackerCustomizer = new DeviceTrackerCustomizer(componentContext.getBundleContext());
			this.m_serviceTracker = new ServiceTracker<Device, Device>(componentContext.getBundleContext(),
					Device.class.getName(), this.m_deviceTrackerCustomizer);
			this.m_serviceTracker.open();
		} catch (final InvalidSyntaxException e) {
			Throwables.propagate(e);
		}
		s_logger.debug(s_messages.activatingDone());
	}

	/**
	 * Cloud Service registration callback
	 *
	 * @param cloudService
	 *            the cloud service dependency
	 */
	protected synchronized void bindCloudService(final CloudService cloudService) {
		if (this.getCloudService() == null) {
			super.setCloudService(cloudService);
		}
	}

	/** {@inheritDoc} */
	@Override
	protected synchronized void deactivate(final ComponentContext componentContext) {
		s_logger.debug(s_messages.deactivating());
		super.deactivate(componentContext);
		super.setCloudService(null);
		this.m_serviceTracker.close();
		s_logger.debug(s_messages.deactivatingDone());
	}

	/** {@inheritDoc} */
	@Override
	protected void doGet(final CloudletTopic reqTopic, final KuraRequestPayload reqPayload,
			final KuraResponsePayload respPayload) throws KuraException {
		s_logger.info(s_messages.cloudGETReqReceiving());
		if ("devices".equals(reqTopic.getResources()[0])) {
			// perform a search operation at the beginning
			this.findDevices();
			if (reqTopic.getResources().length == 1) {
				int index = 1;
				for (final Map.Entry<String, Device> deviceEntry : this.m_devices.entrySet()) {
					final Device device = deviceEntry.getValue();
					respPayload.addMetric(String.valueOf(index++),
							((BaseDevice) device).getDeviceConfiguration().getDeviceName());
				}
			}
			// Checks if the name of the device is provided
			if (reqTopic.getResources().length == 2) {
				final String deviceName = reqTopic.getResources()[1];
				final Device device = this.m_devices.get(deviceName);
				final DeviceConfiguration configuration = ((BaseDevice) device).getDeviceConfiguration();
				final Map<String, Channel> deviceConfiguredChannels = configuration.getChannels();
				int index = 1;
				for (final String channelName : deviceConfiguredChannels.keySet()) {
					respPayload.addMetric(String.valueOf(index++), channelName);
				}
			}
			// Checks if the name of the device and the name of the channel are
			// provided
			if (reqTopic.getResources().length == 3) {
				final String deviceName = reqTopic.getResources()[1];
				final String channelName = reqTopic.getResources()[2];
				final Device device = this.m_devices.get(deviceName);
				final DeviceConfiguration configuration = ((BaseDevice) device).getDeviceConfiguration();
				final Map<String, Channel> deviceConfiguredChannels = configuration.getChannels();
				if ((deviceConfiguredChannels != null) && deviceConfiguredChannels.containsKey(channelName)) {
					final List<DeviceRecord> deviceRecords = device.read(Lists.newArrayList(channelName));
					this.prepareResponse(respPayload, deviceRecords);
				}
			}
		}
		s_logger.info(s_messages.cloudGETReqReceived());
	}

	/** {@inheritDoc} */
	@Override
	protected void doPut(final CloudletTopic reqTopic, final KuraRequestPayload reqPayload,
			final KuraResponsePayload respPayload) throws KuraException {
		s_logger.info(s_messages.cloudPUTReqReceiving());
		// Checks if the name of the device and the name of the channel are
		// provided
		if ("devices".equals(reqTopic.getResources()[0]) && (reqTopic.getResources().length > 2)) {
			// perform a search operation at the beginning
			this.findDevices();
			final String deviceName = reqTopic.getResources()[1];
			final String channelName = reqTopic.getResources()[2];
			final Device device = this.m_devices.get(deviceName);
			final DeviceConfiguration configuration = ((BaseDevice) device).getDeviceConfiguration();
			final Map<String, Channel> deviceConfiguredChannels = configuration.getChannels();
			if ((deviceConfiguredChannels != null) && deviceConfiguredChannels.containsKey(channelName)) {
				final DeviceRecord deviceRecord = Devices.newDeviceRecord(channelName);
				final String userValue = (String) reqPayload.getMetric("value");
				final String userType = (String) reqPayload.getMetric("type");
				this.wrapValue(deviceRecord, userValue, userType);
				final List<DeviceRecord> deviceRecords = device.write(Lists.newArrayList(deviceRecord));
				this.prepareResponse(respPayload, deviceRecords);
			}
		}
		s_logger.info(s_messages.cloudPUTReqReceived());
	}

	/**
	 * Searches for all the currently available devices in the service registry
	 */
	private void findDevices() {
		this.m_devices = this.m_deviceTrackerCustomizer.getRegisteredDevices();
	}

	/**
	 * Prepares the response payload based on the device records as provided
	 *
	 * @param respPayload
	 *            the response payload to prepare
	 * @param deviceRecords
	 *            the list of device records
	 * @throws KuraRuntimeException
	 *             if any of the arguments is null
	 */
	private void prepareResponse(final KuraResponsePayload respPayload, final List<DeviceRecord> deviceRecords) {
		checkNull(respPayload, s_messages.respPayloadNonNull());
		checkNull(deviceRecords, s_messages.deviceRecordsNonNull());

		for (final DeviceRecord deviceRecord : deviceRecords) {
			respPayload.addMetric(s_messages.flag(), deviceRecord.getDeviceFlag());
			respPayload.addMetric(s_messages.timestamp(), deviceRecord.getTimestamp());
			respPayload.addMetric(s_messages.value(), deviceRecord.getValue());
			respPayload.addMetric(s_messages.channel(), deviceRecord.getChannelName());
		}
	}

	/**
	 * Cloud Service deregistration callback
	 *
	 * @param cloudService
	 *            the cloud service dependency
	 */
	protected synchronized void unbindCloudService(final CloudService cloudService) {
		if (this.getCloudService() == cloudService) {
			super.unsetCloudService(cloudService);
		}
	}

	/**
	 * Wraps the provided user provided value to the an instance of
	 * {@link TypedValue} in the device record
	 *
	 * @param deviceRecord
	 *            the device record to contain the typed value
	 * @param userValue
	 *            the value to wrap
	 * @param userType
	 *            the type to use
	 * @throws KuraRuntimeException
	 *             if any of the provided arguments is null
	 */
	private void wrapValue(final DeviceRecord deviceRecord, final String userValue, final String userType) {
		checkNull(deviceRecord, s_messages.deviceRecordNonNull());
		checkNull(userValue, s_messages.valueNonNull());
		checkNull(userType, s_messages.typeNonNull());

		TypedValue<?> value = null;
		if ("INTEGER".equalsIgnoreCase(userType)) {
			value = TypedValues.newIntegerValue(Integer.valueOf(userValue));
		}
		if ("BOOLEAN".equalsIgnoreCase(userType)) {
			value = TypedValues.newBooleanValue(Boolean.valueOf(userValue));
		}
		if ("BYTE".equalsIgnoreCase(userType)) {
			value = TypedValues.newByteValue(Byte.valueOf(userValue));
		}
		if ("DOUBLE".equalsIgnoreCase(userType)) {
			value = TypedValues.newDoubleValue(Double.valueOf(userValue));
		}
		if ("LONG".equalsIgnoreCase(userType)) {
			value = TypedValues.newLongValue(Long.valueOf(userValue));
		}
		if ("SHORT".equalsIgnoreCase(userType)) {
			value = TypedValues.newShortValue(Short.valueOf(userValue));
		}
		if ("STRING".equalsIgnoreCase(userType)) {
			value = TypedValues.newStringValue(userValue);
		}
		if (userValue != null) {
			deviceRecord.setValue(value);
		}
	}

}
