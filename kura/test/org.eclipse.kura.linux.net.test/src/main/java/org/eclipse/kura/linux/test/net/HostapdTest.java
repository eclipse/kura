/*******************************************************************************
 * Copyright (c) 2011, 2017 Eurotech and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech
 *******************************************************************************/
package org.eclipse.kura.linux.test.net;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.kura.linux.net.wifi.HostapdManager;
import org.eclipse.kura.test.annotation.TestTarget;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import junit.framework.TestCase;

public class HostapdTest extends TestCase {

    private static final Logger s_logger = LoggerFactory.getLogger(HostapdTest.class);
    private static CountDownLatch dependencyLatch = new CountDownLatch(0);  // initialize with number of dependencies

    private static final String IFACE_NAME = "wlan0";

    @Override
    @TestTarget(targetPlatforms = { TestTarget.PLATFORM_ALL })
    @BeforeClass
    public void setUp() {

        // Wait for OSGi dependencies
        try {
            dependencyLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("OSGi dependencies unfulfilled");
            System.exit(1);
        }
    }

    @TestTarget(targetPlatforms = { TestTarget.PLATFORM_ALL })
    @Test
    public void testStart() {
        s_logger.info("Test start hostapd");

        try {
            HostapdManager.start(IFACE_NAME);
            assertTrue("hostapd is started", HostapdManager.isRunning(IFACE_NAME));

            boolean validPid = HostapdManager.getPid(IFACE_NAME) > 0 ? true : false;
            assertTrue("Valid hostapd PID", validPid);
        } catch (Exception e) {
            fail("testEnable failed: " + e);
        }
    }

    @TestTarget(targetPlatforms = { TestTarget.PLATFORM_ALL })
    @Test
    public void testStop() {
        s_logger.info("Test stop hostapd");

        try {
            HostapdManager.stop(IFACE_NAME);
            assertFalse("hostapd is disabled", HostapdManager.isRunning(IFACE_NAME));
        } catch (Exception e) {
            fail("testDisable failed: " + e);
        }
    }
}
