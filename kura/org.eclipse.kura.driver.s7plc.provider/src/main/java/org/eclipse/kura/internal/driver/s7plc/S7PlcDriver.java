/**
 * Copyright (c) 2017, 2020 Eurotech and/or its affiliates
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Eurotech
 */

package org.eclipse.kura.internal.driver.s7plc;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.channel.ChannelRecord;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.crypto.CryptoService;
import org.eclipse.kura.driver.ChannelDescriptor;
import org.eclipse.kura.driver.block.BlockFactory;
import org.eclipse.kura.driver.block.task.AbstractBlockDriver;
import org.eclipse.kura.driver.block.task.BlockTask;
import org.eclipse.kura.driver.block.task.Mode;
import org.eclipse.kura.driver.block.task.ToplevelBlockTask;
import org.eclipse.kura.internal.driver.s7plc.task.S7PlcTaskBuilder;
import org.eclipse.kura.internal.driver.s7plc.task.S7PlcToplevelBlockTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import Moka7.S7;
import Moka7.S7Client;

/**
 * The Kura S7PlcDriver is a S7 PLC Driver implementation for Kura Asset-Driver
 * Topology.<br/>
 * <br/>
 *
 * The Kura S7 PLC Driver can be used in cooperation with Kura Asset Model and in
 * isolation as well. In case of isolation, the properties needs to be provided
 * externally.<br/>
 * <br/>
 *
 * The required properties are enlisted in {@link S7PlcChannelDescriptor} and
 * the driver connection specific properties are enlisted in
 * {@link S7PlcOptions}
 *
 * @see S7PlcChannelDescriptor
 * @see S7PlcOptions
 */
public class S7PlcDriver extends AbstractBlockDriver<S7PlcDomain> implements ConfigurableComponent {

    private static final Logger logger = LoggerFactory.getLogger(S7PlcDriver.class);

    private S7ClientState state = new S7ClientState(new S7PlcOptions(Collections.emptyMap()));
    private final AtomicReference<S7PlcOptions> options = new AtomicReference<>();

    private CryptoService cryptoService;

    public void setCryptoService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    public void unsetCryptoService() {
        this.cryptoService = null;
    }

    public void activate(final Map<String, Object> properties) {
        logger.debug("Activating S7 PLC Driver...");
        updated(properties);
        logger.debug("Activating S7 PLC Driver... Done");
    }

    public synchronized void deactivate() {
        logger.debug("Deactivating S7 PLC Driver...");
        try {
            disconnect();
        } catch (final ConnectionException e) {
            logger.error("Error while disconnecting...", e);
        }
        logger.debug("Deactivating S7 PLC Driver.....Done");
    }

    public void updated(final Map<String, Object> properties) {
        logger.debug("Updating S7 PLC Driver...");
        this.options.set(new S7PlcOptions(properties));
        logger.debug("Updating S7 PLC Driver... Done");
    }

    private String decryptPassword(char[] encryptedPassword) throws KuraException {
        final char[] decodedPasswordChars = this.cryptoService.decryptAes(encryptedPassword);
        return new String(decodedPasswordChars);
    }

    private void authenticate(final S7ClientState state) throws ConnectionException {
        logger.debug("Authenticating");
        int code;
        try {
            code = state.client.SetSessionPassword(decryptPassword(state.options.getPassword().toCharArray()));
        } catch (Exception e) {
            throw new ConnectionException(e);
        }
        if (code != 0) {
            throw new ConnectionException("Authentication failed, SetSessionPassword() failed with code: " + code);
        }
    }

    @Override
    public synchronized void connect() throws ConnectionException {
        try {
            final S7PlcOptions currentOptions = this.options.get();

            if (this.state.options != currentOptions) {
                logger.info("configuration changed, disconnecting...");
                disconnect();
                this.state = createClientState(currentOptions);
                logger.info("configuration changed, disconnecting...Done");
            }

            if (!this.state.client.Connected) {
                logger.debug("Connecting to S7 PLC...");
                this.state.client.SetConnectionType(S7.OP);
                int code = this.state.client.ConnectTo(currentOptions.getIp(), currentOptions.getRack(),
                        currentOptions.getSlot());
                if (code != 0) {
                    throw new ConnectionException("Failed to connect to PLC, ConnectTo() failed with code: " + code);
                }
                if (currentOptions.shouldAuthenticate()) {
                    authenticate(this.state);
                }
                logger.debug("Connecting to S7 PLC... Done");
            }
        } catch (Exception e) {
            throw new ConnectionException("Connection failed, unexpected exception", e);
        }
    }

    @Override
    public synchronized void disconnect() throws ConnectionException {
        if (this.state.client.Connected) {
            logger.debug("Disconnecting from S7 PLC...");
            this.state.client.Disconnect();
            logger.debug("Disconnecting from S7 PLC... Done");
        }
    }

    @Override
    protected int getReadMinimumGapSizeForDomain(S7PlcDomain domain) {
        return this.options.get().getMinimumGapSize();
    }

    @Override
    protected BlockFactory<ToplevelBlockTask> getTaskFactoryForDomain(final S7PlcDomain domain, final Mode mode) {
        return (start, end) -> new S7PlcToplevelBlockTask(S7PlcDriver.this, mode, domain.getDB(), domain.getArea(),
                start, end);
    }

    @Override
    protected Stream<Pair<S7PlcDomain, BlockTask>> toTasks(List<ChannelRecord> records, Mode mode) {
        return S7PlcTaskBuilder.build(records, mode);
    }

    @Override
    public ChannelDescriptor getChannelDescriptor() {
        return new S7PlcChannelDescriptor();
    }

    protected S7ClientState createClientState(final S7PlcOptions options) {
        return new S7ClientState(options);
    }

    @Override
    protected void runTask(BlockTask task) {
        try {
            task.run();
        } catch (Moka7Exception e) {
            handleMoka7IOException(e);
        } catch (Exception e) {
            logger.warn("Unexpected exception", e);
        }
    }

    private void handleMoka7IOException(Moka7Exception e) {
        logger.warn("Operation failed due to IO error", e);
        if (e.getStatusCode() <= S7Client.errTCPConnectionReset) {
            logger.warn("Connection problems detected, disconnecting, will attempt to reconnect at next read/write");
            try {
                disconnect();
            } catch (ConnectionException e1) {
                logger.warn("Unable to Disconnect...", e1);
            }
        }
    }

    public synchronized void write(int db, int offset, byte[] data, S7PlcArea area) throws IOException {
        int result = this.state.client.WriteArea(area.getValue(), db, offset, data.length, data);
        if (result != 0) {
            throw new Moka7Exception(
                    area + ":" + db + " off: " + offset + " len: " + data.length + " status: " + result, result);
        }
    }

    public void write(int db, int offset, byte[] data) throws IOException {
        write(db, offset, data, S7PlcArea.AREA_DB);
    }

    public synchronized void read(int db, int offset, byte[] data, S7PlcArea area) throws IOException {
        int result = this.state.client.ReadArea(area.getValue(), db, offset, data.length, data);
        if (result != 0) {
            throw new Moka7Exception(
                    area + ":" + db + " off: " + offset + " len: " + data.length + " status: " + result, result);
        }
    }

    public void read(int db, int offset, byte[] data) throws IOException {
        read(db, offset, data, S7PlcArea.AREA_DB);
    }

    @SuppressWarnings("serial")
    static final class Moka7Exception extends IOException {

        /**
         *
         */
        private static final long serialVersionUID = -6118257067654374279L;
        private final int statusCode;

        public Moka7Exception(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return this.statusCode;
        }
    }

    static final class S7ClientState {

        private final S7Client client;
        private final S7PlcOptions options;

        S7ClientState(final S7PlcOptions options) {
            this(options, new S7Client());
        }

        public S7ClientState(final S7PlcOptions options, final S7Client client) {
            this.options = options;
            this.client = client;
        }
    }
}
