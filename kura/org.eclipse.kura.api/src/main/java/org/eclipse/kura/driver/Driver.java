/*******************************************************************************
 * Copyright (c) 2016, 2020, 2021 Eurotech and/or its affiliates and others
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *  Eurotech
 *  Amit Kumar Mondal
 *  Abe Shmookler
 *******************************************************************************/
package org.eclipse.kura.driver;

import java.util.List;
import java.util.Map;

import org.eclipse.kura.channel.ChannelRecord;
import org.eclipse.kura.channel.listener.ChannelListener;
import org.osgi.annotation.versioning.ProviderType;

/**
 * The Interface Driver is the main interface that all Kura specific
 * communication drivers have to implement and register as an OSGi service
 * instance. A driver often implements a communication protocol to interact with
 * the field devices (assets). The configuration as provided to the driver for
 * communicating with the field device (asset) is provided by user using the
 * configurable component of the actual driver which is internally managed by
 * the OSGi Configuration Admin service
 *
 * @see ChannelRecord
 * @see ChannelDescriptor
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @since 1.2
 */
@ProviderType
public interface Driver {

    /**
     * Each driver is identified by the value of this property in the Component Configuration
     */
    public static final String DRIVER_PID_PROPERTY_NAME = "kura.service.pid";

    /**
     * The Class ConnectionException is a driver specific exception which is
     * essentially used to raise exception related to driver connection
     */
    public final class ConnectionException extends Exception {

        /** The Constant serial version UID. */
        private static final long serialVersionUID = 3050873377900124238L;

        /**
         * Instantiates a new connection exception.
         */
        public ConnectionException() {
            super();
        }

        /**
         * Instantiates a new connection exception.
         *
         * @param messsage
         *            the exception message
         */
        public ConnectionException(final String messsage) {
            super(messsage);
        }

        /**
         * Instantiates a new connection exception.
         *
         * @param message
         *            the exception message
         * @param cause
         *            the exception cause
         */
        public ConnectionException(final String message, final Throwable cause) {
            super(message, cause);
        }

        /**
         * Instantiates a new connection exception.
         *
         * @param cause
         *            the exception cause
         */
        public ConnectionException(final Throwable cause) {
            super(cause);
        }

    }

    /**
     * Attempts to connect to the asset. Before performing any
     * read/write/monitor operation on the connection, a communication channel
     * has to be opened using this method. If the connection attempt fails it
     * throws a {@code ConnectionException}
     *
     * Some communication protocols are not connection oriented. That means no
     * connection has to be built up in order to read or write data. In this
     * case the connect function may optionally test if the asset is reachable.
     *
     * @throws ConnectionException
     *             if the connection to the field device is interrupted
     */
    public void connect() throws ConnectionException;

    /**
     * Attempts to disconnect the already established communication channel.
     *
     * @throws ConnectionException
     *             if the connection to the field device is interrupted
     */
    public void disconnect() throws ConnectionException;

    /**
     * Returns the protocol specific channel descriptor.
     *
     * @return the channel descriptor
     */
    public ChannelDescriptor getChannelDescriptor();

    /**
     * Reads the communication channels that correspond to the given channel
     * records. The read result is returned by setting the value in the channel
     * record. If for some reason no value can be read the value should be set
     * anyways. In this case the channel flag needs to be specified in the channel
     * record. The flag shall best describe the reason of failure. If no value
     * is set the default error code is
     * {@code DriverFlag#DRIVER_ERROR_UNSPECIFIED}. If the connection to the
     * asset is interrupted, then any necessary resources that correspond to
     * this connection should be cleaned up and a {@code ConnectionException}
     * shall be thrown.
     *
     * @param records
     *            the records hold the information of what channels are to be
     *            read. They will be filled by this function with the records
     *            already read.
     * @throws ConnectionException
     *             if the connection to the field device is interrupted
     * @throws NullPointerException
     *             if argument is null
     * @throws IllegalArgumentException
     *             if argument is empty
     * @throws org.eclipse.kura.KuraRuntimeException
     *             if the method is not implemented by the driver then specific
     *             error code {@code KuraErrorCode#OPERATION_NOT_SUPPORTED}
     *             needs to be set in the thrown {@link org.eclipse.kura.KuraRuntimeException}
     */
    public void read(List<ChannelRecord> records) throws ConnectionException;

    /**
     * Registers channel listener for the provided channel configuration for a
     * monitor operation on it.
     *
     * @param channelConfig
     *            the channel configuration
     * @param listener
     *            the listener
     * @throws ConnectionException
     *             if the connection to the field device is interrupted
     * @throws NullPointerException
     *             any of the arguments is null
     * @throws org.eclipse.kura.KuraRuntimeException
     *             if the method is not implemented by the driver then specific
     *             error code {@code KuraErrorCode#OPERATION_NOT_SUPPORTED}
     *             needs to be set in the thrown {@link org.eclipse.kura.KuraRuntimeException}
     */
    public void registerChannelListener(Map<String, Object> channelConfig, ChannelListener listener)
            throws ConnectionException;

    /**
     * Unregisters a already registered channel listener which has been
     * registered for a monitor operation.
     *
     * @param listener
     *            the listener to unregister
     * @throws ConnectionException
     *             if the connection to the field device is interrupted
     * @throws NullPointerException
     *             if the argument is null
     * @throws org.eclipse.kura.KuraRuntimeException
     *             if the method is not implemented by the driver then specific
     *             error code {@code KuraErrorCode#OPERATION_NOT_SUPPORTED}
     *             needs to be set in the thrown {@link org.eclipse.kura.KuraRuntimeException}
     */
    public void unregisterChannelListener(ChannelListener listener) throws ConnectionException;

    /**
     * Writes the data channels that correspond to the given channel records. The
     * write result is returned by setting the driver flag
     * {@code ChannelFlag#SUCCESS} in the channel records. If the
     * connection to the asset is interrupted, then any necessary resources that
     * correspond to this connection should be cleaned up and a
     * {@code ConnectionException} shall be thrown.
     *
     * @param records
     *            the records hold the information of what channels are to be
     *            written and the values that are to written. They will be
     *            filled by this function with a driver flag stating whether the
     *            write process was successful or not.
     * @throws ConnectionException
     *             if the connection to the field device is interrupted
     * @throws NullPointerException
     *             if the argument is null
     * @throws IllegalArgumentException
     *             if the provided list is empty
     * @throws org.eclipse.kura.KuraRuntimeException
     *             if the method is not implemented by the driver then specific
     *             error code {@code KuraErrorCode#OPERATION_NOT_SUPPORTED}
     *             needs to be set in the thrown {@link org.eclipse.kura.KuraRuntimeException}
     */
    public void write(List<ChannelRecord> records) throws ConnectionException;

    /**
     * This method allows the driver to perform protocol specific optimizations in order to accelerate the execution of
     * batches of read requests having the same channel configuration.
     * The result of this optimization will be returned by the driver as a {@link PreparedRead} instance that can be
     * used to perform the requests.
     * In order to improve efficiency a driver should validate the channel configuration of the provided channels during
     * this method call.
     * It is also permitted to the implementation of the {@link PreparedRead#execute()} and
     * {@link PreparedRead#getChannelRecords()} methods to return the same {@link ChannelRecord} instances provided as
     * an
     * argument to this method.
     * If the validation of the channel configuration fails for some channels, the driver must not throw an exception
     * but it is required to return channel records with proper error flags set as a result of the
     * {@link PreparedRead#execute()} call.
     *
     * @see PreparedRead
     * @param records
     *            The list of channel records that represent the request to be optimized.
     * @return The {@link PreparedRead} instance
     * @throws NullPointerException
     *             if the provided list is null
     */
    public PreparedRead prepareRead(List<ChannelRecord> records);
}
