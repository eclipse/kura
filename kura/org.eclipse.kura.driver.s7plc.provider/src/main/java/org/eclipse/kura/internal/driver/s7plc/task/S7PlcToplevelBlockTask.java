/*******************************************************************************
 * Copyright (c) 2017, 2020 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech
 *******************************************************************************/

package org.eclipse.kura.internal.driver.s7plc.task;

import java.io.IOException;

import org.eclipse.kura.driver.binary.Buffer;
import org.eclipse.kura.driver.binary.ByteArrayBuffer;
import org.eclipse.kura.driver.block.task.Mode;
import org.eclipse.kura.driver.block.task.ToplevelBlockTask;
import org.eclipse.kura.internal.driver.s7plc.S7PlcArea;
import org.eclipse.kura.internal.driver.s7plc.S7PlcDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class S7PlcToplevelBlockTask extends ToplevelBlockTask {

    private static final Logger logger = LoggerFactory.getLogger(S7PlcToplevelBlockTask.class);

    private final int dbNo;
    private final S7PlcArea area;
    private ByteArrayBuffer data;
    private final S7PlcDriver driver;

    public S7PlcToplevelBlockTask(S7PlcDriver driver, Mode mode, int dbNumber, S7PlcArea area, int start, int end) {
        super(start, end, mode);
        this.dbNo = dbNumber;
        this.area = area;
        this.driver = driver;
    }

    @Override
    public void processBuffer() throws IOException {
        if (getMode() == Mode.READ) {
            logger.debug("Reading from PLC, {} {} offset: {} length: {}", this.area, this.dbNo, getStart(),
                    getBuffer().getLength());
            this.driver.read(this.dbNo, getStart(), ((ByteArrayBuffer) getBuffer()).getBackingArray(), this.area);
        } else {
            logger.debug("Writing to PLC, {} {} offset: {} length: {}", this.area, this.dbNo, getStart(),
                    getBuffer().getLength());
            this.driver.write(this.dbNo, getStart(), ((ByteArrayBuffer) getBuffer()).getBackingArray(), this.area);
        }
    }

    @Override
    public Buffer getBuffer() {
        if (this.data == null) {
            this.data = new ByteArrayBuffer(new byte[getEnd() - getStart()]);
        }
        return this.data;
    }

}
