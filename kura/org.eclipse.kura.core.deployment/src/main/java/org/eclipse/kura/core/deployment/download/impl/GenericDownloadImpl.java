/*******************************************************************************
 * Copyright (c) 2019 Eurotech and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.core.deployment.download.impl;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.kura.download.Download;
import org.eclipse.kura.download.DownloadParameters;
import org.eclipse.kura.download.DownloadState;
import org.eclipse.kura.download.DownloadStatus;
import org.eclipse.kura.download.Hash;
import org.eclipse.kura.download.listener.DownloadStateChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GenericDownloadImpl implements Download {

    private static final Logger logger = LoggerFactory.getLogger(GenericDownloadImpl.class);

    private final CompletableFuture<Void> future;
    private final ExecutorService executor;

    protected final DownloadParameters request;

    protected final AtomicBoolean canceled = new AtomicBoolean();

    protected final DownloadStateImpl state = new DownloadStateImpl();

    private Optional<Future<?>> executorFuture = Optional.empty();
    private Set<DownloadStateChangeListener> listeners = new CopyOnWriteArraySet<>();

    public GenericDownloadImpl(final DownloadParameters request, final ExecutorService executor) {
        this.request = request;
        this.executor = executor;

        this.future = new CompletableFuture<Void>() {

            @Override
            public boolean cancel(final boolean mayInterruptIfRunning) {
                GenericDownloadImpl.this.cancel(mayInterruptIfRunning);
                return super.cancel(false);
            }
        };
    }

    @Override
    public void registerListener(final DownloadStateChangeListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void unregisterListener(DownloadStateChangeListener listener) {
        this.listeners.remove(listener);
    }

    @Override
    public synchronized void start() {
        if (canceled.get()) {
            throw new IllegalStateException("Download has been cancelled");
        }

        if (!executorFuture.isPresent()) {
            executorFuture = Optional.of(executor.submit(this::runInternal));
        } else {
            throw new IllegalStateException("Download already started");
        }
    }

    @Override
    public DownloadParameters getParameters() {
        return request;
    }

    @Override
    public CompletableFuture<Void> future() {
        return future;
    }

    @Override
    public DownloadState getState() {
        return new DownloadStateImpl(this.state);
    }

    protected void runInternal() {

        try {

            final Optional<Hash> checksum = request.getChecksum();

            if (checksum.isPresent() && Utils.verifyDownload(request.getDestination(), checksum.get())) {
                logger.info("already downloaded and verified");
                complete(DownloadStatus.COMPLETED);
                return;
            }

            if (!checksum.isPresent() && request.shouldForceDownload() && request.getDestination().exists()) {
                deleteDestinationFile();
            }

            run();

            if (checksum.isPresent() && !Utils.verifyDownload(request.getDestination(), checksum.get())) {
                throw new IOException("download verification failed");
            }

            complete(DownloadStatus.COMPLETED);

        } catch (final InterruptedException e) {
            completeExceptionally(e, DownloadStatus.CANCELLED);
            Thread.currentThread().interrupt();
        } catch (final Exception e) {
            completeExceptionally(e, DownloadStatus.FAILED);
        }

    }

    private void deleteDestinationFile() {
        try {
            Files.delete(request.getDestination().toPath());
        } catch (final Exception e) {
            logger.warn("failed to delete destination file", e);
        }
    }

    protected void transferFrom(final InputStream in, final Optional<Long> contentLength,
            final boolean isPartialContent) throws IOException, InterruptedException {
        transferFrom(Channels.newChannel(in), contentLength, isPartialContent);
    }

    protected void transferFrom(final ReadableByteChannel in, final Optional<Long> contentLength,
            final boolean isPartialContent) throws IOException, InterruptedException {

        this.state.setTotalSize(contentLength);

        final Optional<Long> notificationBlockSize = getNotificationBlockSize(this.state.getTotalSize());
        final long blockSize = getBlockSize(notificationBlockSize);

        int notificationIndex = 1;

        postStateChange();

        try (final FileOutputStream os = new FileOutputStream(request.getDestination(), isPartialContent);
                final FileChannel out = os.getChannel()) {

            final long start = isPartialContent ? out.size() : 0;
            long rd;

            while ((rd = transferExact(in, out, start + this.state.getDownloadedBytes(), blockSize)) > 0) {

                this.state.incrementDownloadedBytes(rd);

                if (notificationBlockSize.isPresent()
                        && this.state.getDownloadedBytes() >= notificationIndex * notificationBlockSize.get()) {
                    postStateChange();
                    notificationIndex++;
                }

                waitBlockDelay();
            }
        }
    }

    protected long transferExact(final ReadableByteChannel in, final FileChannel out, final long pos, final long size)
            throws IOException, InterruptedException {

        long start = pos;
        long transferred = 0;
        long rd;

        boolean cancel;

        while (!(cancel = this.canceled.get()) && transferred < size
                && (rd = out.transferFrom(in, start, size - transferred)) > 0) {
            start += rd;
            transferred += rd;
        }

        if (cancel) {
            throw new InterruptedException("download cancelled");
        }

        return transferred;
    }

    protected abstract void run() throws IOException, InterruptedException;

    protected Optional<Long> getNotificationBlockSize(final Optional<Long> contentLength) {
        if (request.getNotificationBlockSize().isPresent()) {
            return request.getNotificationBlockSize();
        }

        return contentLength.map(v -> v * 5 / 100 + 1);
    }

    protected long getBlockSize(final Optional<Long> notificationBlockSize) {
        final long configBlockSize = request.getBlockSize().orElse(4096L);

        if (notificationBlockSize.isPresent()) {
            return Math.min(notificationBlockSize.get(), configBlockSize);
        }

        return configBlockSize;
    }

    protected void waitBlockDelay() throws InterruptedException {
        final Optional<Long> blockDelay = request.getBlockDelay();

        if (blockDelay.isPresent()) {
            Thread.sleep(blockDelay.get());
        }
    }

    protected void complete(final DownloadStatus status) {
        state.setStatus(status);
        postStateChange();
        future.complete(null);
    }

    protected void completeExceptionally(final Throwable e, final DownloadStatus status) {
        state.setStatus(status);
        state.setException(Optional.of(e));
        postStateChange();
        future.completeExceptionally(e);
    }

    protected void postStateChange() {
        for (final DownloadStateChangeListener listener : listeners) {
            try {
                listener.onDownloadStateChange(request, new DownloadStateImpl(this.state));
            } catch (final Exception e) {
                logger.warn("unexpected exception from DownloadStateChangeListener", e);
            }
        }
    }

    protected synchronized void cancel(final boolean mayInterruptIfRunning) {
        canceled.set(true);
        executorFuture.ifPresent(f -> f.cancel(mayInterruptIfRunning));
    }

}
