/*******************************************************************************
 * Copyright (c) 2021 Eurotech and/or its affiliates and others
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *  Eurotech
 *******************************************************************************/
package org.eclipse.kura.web.client.util;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.kura.web.shared.model.GwtLogEntry;
import org.eclipse.kura.web.shared.service.GwtLogService;
import org.eclipse.kura.web.shared.service.GwtLogServiceAsync;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.RpcRequestBuilder;
import com.google.gwt.user.client.rpc.ServiceDefTarget;
import com.google.gwt.user.client.rpc.StatusCodeException;

public class LogPollService {

    private static final int POLL_TIMEOUT = 2000;
    private static final int ON_FAILURE_RESEND_DELAY = 2000;
    private static final int RESEND_DELAY = 100;
    private static final int RESEND_DELAY_NO_UPDATES = 5000;

    private boolean stop = false;
    private Timer resendTimer;
    private List<LogListener> listeners = new LinkedList<>();
    private static LogPollService instance = new LogPollService();
    private final GwtLogServiceAsync gwtLogService = GWT.create(GwtLogService.class);

    private Logger logger = Logger.getLogger("LogPollService");
    private int rpcCount = 0;

    private LogPollService() {
        ((ServiceDefTarget) this.gwtLogService).setRpcRequestBuilder(new TimeoutRequestBuilder());
    }

    public static LogPollService getInstance() {
        return instance;
    }

    public void startLogPolling() {
        this.gwtLogService.readLogs(this.eventCallback);
        this.stop = false;
    }

    public void stopLogPolling() {
        this.stop = true;
    }

    public static void subscribe(LogListener listener) {
        instance.listeners.add(listener);
    }

    public static void unsubscribe(LogListener listener) {
        instance.listeners.remove(listener);
    }

    public interface LogListener {

        public void onLogsReceived(List<GwtLogEntry> entries);
    }

    private class TimeoutRequestBuilder extends RpcRequestBuilder {

        @Override
        protected RequestBuilder doCreate(String serviceEntryPoint) {
            RequestBuilder builder = super.doCreate(serviceEntryPoint);
            builder.setTimeoutMillis(POLL_TIMEOUT);
            return builder;
        }
    }

    private final AsyncCallback<List<GwtLogEntry>> eventCallback = new AsyncCallback<List<GwtLogEntry>>() {

        @Override
        public void onFailure(Throwable caught) {
            if (caught instanceof StatusCodeException) {
                final StatusCodeException statusCodeException = (StatusCodeException) caught;
                if (statusCodeException.getStatusCode() == 401) {
                    FailureHandler.handle(caught);
                }
            }

            startResendTimer(ON_FAILURE_RESEND_DELAY);
        }

        @Override
        public void onSuccess(List<GwtLogEntry> result) {
            logger.info("RPC successful. Count: " + rpcCount++);

            int delay = RESEND_DELAY;

            if (result != null && !result.isEmpty()) {

                for (LogListener listener : LogPollService.this.listeners) {
                    listener.onLogsReceived(result);
                }

                stopResendTimer();
            } else {
                delay = RESEND_DELAY_NO_UPDATES;
            }

            new Timer() {

                @Override
                public void run() {
                    if (!LogPollService.this.stop) {
                        LogPollService.this.gwtLogService.readLogs(LogPollService.this.eventCallback);
                    }
                }
            }.schedule(delay);
        }

        private void startResendTimer(int timeout) {
            stopResendTimer();

            LogPollService.this.resendTimer = new Timer() {

                @Override
                public void run() {
                    LogPollService.this.gwtLogService.readLogs(LogPollService.this.eventCallback);
                }
            };
            LogPollService.this.resendTimer.schedule(timeout);
        }

        private void stopResendTimer() {
            if (LogPollService.this.resendTimer != null) {
                LogPollService.this.resendTimer.cancel();
            }

            LogPollService.this.resendTimer = null;
        }
    };
}
