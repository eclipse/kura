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
package org.eclipse.kura.internal.wire.asset;

import static org.eclipse.kura.Preconditions.checkCondition;
import static org.eclipse.kura.Preconditions.checkNull;
import static org.eclipse.kura.asset.AssetConstants.ASSET_DESC_PROP;
import static org.eclipse.kura.asset.AssetConstants.ASSET_DRIVER_PROP;
import static org.eclipse.kura.asset.AssetConstants.ASSET_NAME_PROP;
import static org.eclipse.kura.asset.AssetConstants.CHANNEL_PROPERTY_POSTFIX;
import static org.eclipse.kura.asset.AssetConstants.CHANNEL_PROPERTY_PREFIX;
import static org.eclipse.kura.asset.AssetConstants.DRIVER_PROPERTY_POSTFIX;
import static org.eclipse.kura.asset.AssetConstants.NAME;
import static org.eclipse.kura.asset.AssetConstants.SEVERITY_LEVEL;
import static org.eclipse.kura.asset.AssetConstants.TYPE;
import static org.eclipse.kura.asset.AssetConstants.VALUE_TYPE;
import static org.eclipse.kura.asset.ChannelType.READ;
import static org.eclipse.kura.asset.ChannelType.READ_WRITE;
import static org.eclipse.kura.asset.ChannelType.WRITE;
import static org.eclipse.kura.wire.SeverityLevel.ERROR;
import static org.eclipse.kura.wire.SeverityLevel.INFO;
import static org.osgi.framework.Constants.SERVICE_PID;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.KuraRuntimeException;
import org.eclipse.kura.asset.Asset;
import org.eclipse.kura.asset.AssetConfiguration;
import org.eclipse.kura.asset.AssetFlag;
import org.eclipse.kura.asset.AssetRecord;
import org.eclipse.kura.asset.AssetService;
import org.eclipse.kura.asset.Channel;
import org.eclipse.kura.configuration.ComponentConfiguration;
import org.eclipse.kura.configuration.ConfigurationService;
import org.eclipse.kura.configuration.SelfConfiguringComponent;
import org.eclipse.kura.configuration.metatype.Option;
import org.eclipse.kura.core.configuration.ComponentConfigurationImpl;
import org.eclipse.kura.core.configuration.metatype.Tad;
import org.eclipse.kura.core.configuration.metatype.Tocd;
import org.eclipse.kura.core.configuration.metatype.Toption;
import org.eclipse.kura.core.configuration.metatype.Tscalar;
import org.eclipse.kura.driver.ChannelDescriptor;
import org.eclipse.kura.driver.Driver;
import org.eclipse.kura.localization.LocalizationAdapter;
import org.eclipse.kura.localization.resources.WireMessages;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.type.TypedValues;
import org.eclipse.kura.util.base.ThrowableUtil;
import org.eclipse.kura.util.collection.CollectionUtil;
import org.eclipse.kura.wire.SeverityLevel;
import org.eclipse.kura.wire.TimerWireField;
import org.eclipse.kura.wire.WireEmitter;
import org.eclipse.kura.wire.WireEnvelope;
import org.eclipse.kura.wire.WireField;
import org.eclipse.kura.wire.WireHelperService;
import org.eclipse.kura.wire.WireReceiver;
import org.eclipse.kura.wire.WireRecord;
import org.eclipse.kura.wire.WireSupport;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class WireAsset is a wire component which provides all necessary higher
 * level abstractions of a Kura asset. This wire asset is an integral wire
 * component in Kura Wires topology as it represents an industrial device with a
 * field protocol driver associated to it.<br/>
 * <br/>
 *
 * The WireRecord to be emitted by every wire asset comprises the following keys
 *
 * <ul>
 * <li>channel_name</li>
 * <li>asset_flag</li>
 * <li>timestamp</li>
 * <li>value</li>
 * <li>exception</li> (This Wire Field is present if and only if asset_flag is
 * set to FAILURE)
 * </ul>
 *
 * @see Asset
 */
public final class WireAsset implements WireEmitter, WireReceiver, SelfConfiguringComponent {

	/** Configuration PID Property. */
	private static final String CONF_PID = "org.eclipse.kura.wire.WireAsset";

	/** The Logger instance. */
	private static final Logger s_logger = LoggerFactory.getLogger(WireAsset.class);

	/** Localization Resource. */
	private static final WireMessages s_message = LocalizationAdapter.adapt(WireMessages.class);

	/** Asset Implementation. */
	private Asset m_asset;

	/** The provided asset configuration wrapper instance. */
	private AssetConfiguration m_assetConfiguration;

	/** The Asset Service instance. */
	private volatile AssetService m_assetService;

	/** The service component context. */
	private ComponentContext m_context;

	/** The configurable properties of this asset. */
	private Map<String, Object> m_properties;

	/** The Wire Helper Service. */
	private volatile WireHelperService m_wireHelperService;

	/** Wire Supporter Component. */
	private WireSupport m_wireSupport;

	/**
	 * OSGi service component callback while activation.
	 *
	 * @param componentContext
	 *            the component context
	 * @param properties
	 *            the service properties
	 */
	protected synchronized void activate(final ComponentContext componentContext,
			final Map<String, Object> properties) {
		s_logger.debug(s_message.activatingWireAsset());
		this.m_asset = this.m_assetService.newAsset();
		this.m_asset.initialize(properties);
		this.m_assetConfiguration = this.m_asset.getAssetConfiguration();
		this.m_context = componentContext;
		this.m_properties = properties;
		this.m_wireSupport = this.m_wireHelperService.newWireSupport(this);
		s_logger.debug(s_message.activatingWireAssetDone());
	}

	/**
	 * Binds the Asset Service.
	 *
	 * @param assetService
	 *            the Asset Service
	 */
	public synchronized void bindAssetService(final AssetService assetService) {
		if (this.m_assetService == null) {
			this.m_assetService = assetService;
		}
	}

	/**
	 * Binds the Wire Helper Service.
	 *
	 * @param wireHelperService
	 *            the new Wire Helper Service
	 */
	public synchronized void bindWireHelperService(final WireHelperService wireHelperService) {
		if (this.m_wireHelperService == null) {
			this.m_wireHelperService = wireHelperService;
		}
	}

	/**
	 * Clones provided Attribute Definition by prepending the provided prefix.
	 *
	 * @param oldAd
	 *            the old Attribute Definition
	 * @param prefix
	 *            the prefix to be prepended (this will be in the format of
	 *            {@code x.CH.} or {@code x.CH.DRIVER.} where {@code x} is
	 *            channel identifier number. {@code x.CH.} will be used for the
	 *            channel specific properties except the driver specific
	 *            properties. The driver specific properties in the channel will
	 *            use the {@code x.CH.DRIVER.} prefix)
	 * @return the new attribute definition
	 * @throws KuraRuntimeException
	 *             if any of the provided arguments is null
	 */
	private Tad cloneAd(final Tad oldAd, final String prefix) {
		checkNull(oldAd, s_message.oldAdNonNull());
		checkNull(prefix, s_message.adPrefixNonNull());

		String pref = prefix;
		final String oldAdId = oldAd.getId();
		if ((oldAdId != ASSET_DESC_PROP.value()) && (oldAdId != ASSET_DRIVER_PROP.value())
				&& (oldAdId != ASSET_NAME_PROP.value()) && (oldAdId != NAME.value()) && (oldAdId != TYPE.value())
				&& (oldAdId != VALUE_TYPE.value()) && (oldAdId != SEVERITY_LEVEL.value())) {
			pref = prefix + DRIVER_PROPERTY_POSTFIX.value() + CHANNEL_PROPERTY_POSTFIX.value();
		}
		final Tad result = new Tad();
		result.setId(pref + oldAdId);
		result.setName(pref + oldAd.getName());
		result.setCardinality(oldAd.getCardinality());
		result.setType(Tscalar.fromValue(oldAd.getType().value()));
		result.setDescription(oldAd.getDescription());
		result.setDefault(oldAd.getDefault());
		result.setMax(oldAd.getMax());
		result.setMin(oldAd.getMin());
		result.setRequired(oldAd.isRequired());
		for (final Option option : oldAd.getOption()) {
			final Toption newOption = new Toption();
			newOption.setLabel(option.getLabel());
			newOption.setValue(option.getValue());
			result.getOption().add(newOption);
		}
		return result;
	}

	/** {@inheritDoc} */
	@Override
	public void consumersConnected(final Wire[] wires) {
		this.m_wireSupport.consumersConnected(wires);
	}

	/**
	 * OSGi service component callback while deactivation.
	 *
	 * @param context
	 *            the context
	 */
	protected synchronized void deactivate(final ComponentContext context) {
		s_logger.debug(s_message.deactivatingWireAsset());
		this.m_asset.release();
		s_logger.debug(s_message.deactivatingWireAssetDone());
	}

	/**
	 * Emit the provided list of asset records to the associated wires.
	 *
	 * @param assetRecords
	 *            the list of asset records conforming to the aforementioned
	 *            specification
	 * @throws KuraRuntimeException
	 *             if provided records list is null or it is empty
	 */
	private void emitAssetRecords(final List<AssetRecord> assetRecords) {
		checkNull(assetRecords, s_message.assetRecordsNonNull());
		checkCondition(assetRecords.isEmpty(), s_message.assetRecordsNonEmpty());

		final List<WireRecord> wireRecords = CollectionUtil.newArrayList();
		for (final AssetRecord assetRecord : assetRecords) {
			final AssetFlag assetFlag = assetRecord.getAssetFlag();
			final SeverityLevel level = (assetFlag == AssetFlag.FAILURE) ? ERROR : INFO;
			final WireField channelIdWireField = new WireField(s_message.channelId(),
					TypedValues.newLongValue(assetRecord.getChannelId()), level);
			final WireField assetFlagWireField = new WireField(s_message.assetFlag(),
					TypedValues.newStringValue(assetFlag.name()), level);
			final WireField timestampWireField = new WireField(s_message.timestamp(),
					TypedValues.newLongValue(assetRecord.getTimestamp()), level);
			final WireField valueWireField = new WireField(s_message.value(), assetRecord.getValue(), level);
			WireRecord wireRecord;
			WireField errorField;
			if (level == ERROR) {
				errorField = new WireField(s_message.error(), TypedValues.newStringValue("ERROR"), level);
				wireRecord = new WireRecord(new Timestamp(new Date().getTime()), Arrays.asList(channelIdWireField,
						assetFlagWireField, timestampWireField, valueWireField, errorField));
			} else {
				wireRecord = new WireRecord(new Timestamp(new Date().getTime()),
						Arrays.asList(channelIdWireField, assetFlagWireField, timestampWireField, valueWireField));
			}
			wireRecords.add(wireRecord);
		}
		this.m_wireSupport.emit(wireRecords);
	}

	/** {@inheritDoc} */
	@SuppressWarnings("unchecked")
	@Override
	public ComponentConfiguration getConfiguration() throws KuraException {
		final String componentName = this.m_context.getProperties().get(ConfigurationService.KURA_SERVICE_PID).toString();

		final Tocd mainOcd = new Tocd();
		mainOcd.setId(CONF_PID);
		mainOcd.setName(s_message.ocdName());
		mainOcd.setDescription(s_message.ocdDescription());

		final Tad assetNameAd = new Tad();
		assetNameAd.setId(ASSET_NAME_PROP.value());
		assetNameAd.setName(ASSET_NAME_PROP.value());
		assetNameAd.setCardinality(0);
		assetNameAd.setType(Tscalar.STRING);
		assetNameAd.setDescription(s_message.name());
		assetNameAd.setRequired(true);

		final Tad assetDescriptionAd = new Tad();
		assetDescriptionAd.setId(ASSET_DESC_PROP.value());
		assetDescriptionAd.setName(ASSET_DESC_PROP.value());
		assetDescriptionAd.setCardinality(0);
		assetDescriptionAd.setType(Tscalar.STRING);
		assetDescriptionAd.setDescription(s_message.description());
		assetDescriptionAd.setRequired(true);

		final Tad driverNameAd = new Tad();
		driverNameAd.setId(ASSET_DRIVER_PROP.value());
		driverNameAd.setName(ASSET_DRIVER_PROP.value());
		driverNameAd.setCardinality(0);
		driverNameAd.setType(Tscalar.STRING);
		driverNameAd.setDescription(s_message.driverName());
		driverNameAd.setRequired(true);

		final Tad severityLevelAd = new Tad();
		severityLevelAd.setId(SEVERITY_LEVEL.value());
		severityLevelAd.setName(SEVERITY_LEVEL.value());
		// default severity level is ERROR
		severityLevelAd.setDefault(s_message.error());
		severityLevelAd.setCardinality(0);
		severityLevelAd.setType(Tscalar.STRING);
		severityLevelAd.setDescription(s_message.driverName());
		severityLevelAd.setRequired(true);

		final Toption infoLevel = new Toption();
		infoLevel.setValue(s_message.info());
		infoLevel.setLabel(s_message.info());
		severityLevelAd.getOption().add(infoLevel);

		final Toption configLevel = new Toption();
		configLevel.setValue(s_message.error());
		configLevel.setLabel(s_message.error());
		severityLevelAd.getOption().add(configLevel);

		final Toption errorLevel = new Toption();
		errorLevel.setValue(s_message.config());
		errorLevel.setLabel(s_message.config());
		severityLevelAd.getOption().add(errorLevel);

		mainOcd.addAD(assetDescriptionAd);
		mainOcd.addAD(assetNameAd);
		mainOcd.addAD(severityLevelAd);

		final Map<String, Object> props = CollectionUtil.newHashMap();
		for (final Map.Entry<String, Object> entry : this.m_properties.entrySet()) {
			props.put(entry.getKey(), entry.getValue());
		}
		ChannelDescriptor channelDescriptor = null;
		final Driver driver = this.m_asset.getDriver();
		if (driver != null) {
			channelDescriptor = driver.getChannelDescriptor();
		}
		if (channelDescriptor != null) {
			List<Tad> driverSpecificChannelConfiguration = null;
			final Object descriptor = channelDescriptor.getDescriptor();
			if (descriptor instanceof List<?>) {
				driverSpecificChannelConfiguration = (List<Tad>) descriptor;
			}
			if (driverSpecificChannelConfiguration != null) {
				final ChannelDescriptor basicChanneldescriptor = new BaseChannelDescriptor();
				List<Tad> channelConfiguration = null;
				final Object baseChannelDescriptor = basicChanneldescriptor.getDescriptor();
				if (baseChannelDescriptor instanceof List<?>) {
					channelConfiguration = (List<Tad>) baseChannelDescriptor;
				}
				if (channelConfiguration != null) {
					channelConfiguration.addAll(driverSpecificChannelConfiguration);
				}
				for (final Tad attribute : channelConfiguration) {
					for (final String prefix : this
							.retrieveChannelPrefixes(this.m_assetConfiguration.getAssetChannels())) {
						final Tad newAttribute = this.cloneAd(attribute, prefix);
						mainOcd.addAD(newAttribute);
					}
				}
			}
		}
		return new ComponentConfigurationImpl(componentName, mainOcd, props);
	}

	/**
	 * This method is triggered as soon as the wire component receives a Wire
	 * Envelope. After it receives a Wire Envelope, it checks for all associated
	 * channels to read and write and perform the operations accordingly. The
	 * order of executions are performed the following way:
	 *
	 * <ul>
	 * <li>Perform all read operations on associated reading channels</li>
	 * <li>Perform all write operations on associated writing channels</li>
	 * <ul>
	 *
	 * Both of the aforementioned operations are performed as soon as it timer
	 * wire component is also triggered.
	 *
	 * @param wireEnvelope
	 *            the received wire envelope
	 */
	@Override
	public void onWireReceive(final WireEnvelope wireEnvelope) {
		checkNull(wireEnvelope, s_message.wireEnvelopeNonNull());
		s_logger.debug(s_message.wireEnvelopeReceived() + this.m_wireSupport);

		// filtering list of wire records based on the provided severity level
		final List<WireRecord> records = this.m_wireSupport.filter(wireEnvelope.getRecords());
		final List<AssetRecord> assetRecordsToWriteChannels = CollectionUtil.newArrayList();
		final List<Long> channelsToRead = CollectionUtil.newArrayList();
		final Map<Long, Channel> channels = this.m_assetConfiguration.getAssetChannels();
		// determining channels to read
		for (final Map.Entry<Long, Channel> channelEntry : channels.entrySet()) {
			final Channel channel = channelEntry.getValue();
			if ((channel.getType() == READ) || (channel.getType() == READ_WRITE)) {
				channelsToRead.add(channel.getId());
			}
		}
		checkCondition(records.isEmpty(), s_message.wireRecordsNonEmpty());
		final Object field = records.get(0).getFields().get(0);
		if (field instanceof TimerWireField) {
			// perform the read operation on timer event receive
			try {
				final List<AssetRecord> recentlyReadRecords = this.m_asset.read(channelsToRead);
				this.emitAssetRecords(recentlyReadRecords);
			} catch (final KuraException e) {
				s_logger.error(s_message.errorPerformingRead() + ThrowableUtil.stackTraceAsString(e));
			}
		}
		// determining channels to write
		for (final WireRecord wireRecord : records) {
			for (final WireField wireField : wireRecord.getFields()) {
				for (final Map.Entry<Long, Channel> channelEntry : channels.entrySet()) {
					final Channel channel = channelEntry.getValue();
					if ((channel.getType() == WRITE) || (channel.getType() == READ_WRITE)) {
						final String wireFieldName = wireField.getName();
						// if the channel name is equal to the received wire
						// field name, then write the wire field value to the
						// specific channel
						if (channel.getName().equalsIgnoreCase(wireFieldName)
								&& (wireField.getSeverityLevel() == INFO)) {
							assetRecordsToWriteChannels.add(this.prepareAssetRecord(channel, wireField.getValue()));
						}
					}
				}
			}
		}
		// perform the write operation
		try {
			this.m_asset.write(assetRecordsToWriteChannels);
		} catch (final KuraException e) {
			s_logger.error(s_message.errorPerformingWrite() + ThrowableUtil.stackTraceAsString(e));
		}
	}

	/** {@inheritDoc} */
	@Override
	public Object polled(final Wire wire) {
		return this.m_wireSupport.polled(wire);
	}

	/**
	 * Create an asset record from the provided channel information.
	 *
	 * @param channel
	 *            the channel to get the values from
	 * @param value
	 *            the value
	 * @return the asset record
	 * @throws KuraRuntimeException
	 *             if any of the provided arguments is null
	 */
	private AssetRecord prepareAssetRecord(final Channel channel, final TypedValue<?> value) {
		checkNull(channel, s_message.channelNonNull());
		checkNull(value, s_message.valueNonNull());

		final AssetRecord assetRecord = new AssetRecord(channel.getId());
		assetRecord.setValue(value);
		return assetRecord;
	}

	/** {@inheritDoc} */
	@Override
	public void producersConnected(final Wire[] wires) {
		this.m_wireSupport.producersConnected(wires);
	}

	/**
	 * Retrieves the set of prefixes of the channels from the map of channels.
	 *
	 * @param channels
	 *            the properties to parse
	 * @return the list of channel IDs
	 * @throws KuraRuntimeException
	 *             if the argument is null
	 */
	private Set<String> retrieveChannelPrefixes(final Map<Long, Channel> channels) {
		checkNull(channels, s_message.propertiesNonNull());
		final Set<String> channelPrefixes = CollectionUtil.newHashSet();
		for (final Map.Entry<Long, Channel> entry : channels.entrySet()) {
			final Long key = entry.getKey();
			final String prefix = key + CHANNEL_PROPERTY_POSTFIX.value() + CHANNEL_PROPERTY_PREFIX.value()
					+ CHANNEL_PROPERTY_POSTFIX.value();
			channelPrefixes.add(prefix);
		}
		return channelPrefixes;
	}

	/**
	 * Unbinds the Asset Service.
	 *
	 * @param assetService
	 *            the Asset Service
	 */
	public synchronized void unbindAssetService(final AssetService assetService) {
		if (this.m_assetService == assetService) {
			this.m_assetService = null;
		}
	}

	/**
	 * Unbinds the Wire Helper Service.
	 *
	 * @param wireHelperService
	 *            the new Wire Helper Service
	 */
	public synchronized void unbindWireHelperService(final WireHelperService wireHelperService) {
		if (this.m_wireHelperService == wireHelperService) {
			this.m_wireHelperService = null;
		}
	}

	/** {@inheritDoc} */
	public synchronized void updated(final Map<String, Object> properties) {
		s_logger.debug(s_message.updatingWireAsset());
		this.m_asset.initialize(properties);
		s_logger.debug(s_message.updatingWireAssetDone());
	}

	/** {@inheritDoc} */
	@Override
	public void updated(final Wire wire, final Object value) {
		this.m_wireSupport.updated(wire, value);
	}

}
