/*******************************************************************************
 * Copyright (c) 2019 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 *******************************************************************************/
package org.eclipse.kura.download;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Represents the parameters describing a download request.
 * 
 * @since 2.2
 * @noimplement This interface is not intended to be implemented by clients.
 */
@ProviderType
public class DownloadParameters {

    private URI uri;
    private File destination;
    private boolean shouldResume;
    private boolean forceDownload;
    private Optional<Hash> checksum;
    private Optional<Credentials> credentials;

    private Optional<Long> blockSize;
    private Optional<Long> timeoutMs;
    private Optional<Long> blockDelay;
    private Optional<Long> notificationBlockSize;

    private Map<String, Object> extraProperties;

    private DownloadParameters() {
    }

    /**
     * Returns the URI of the resource to be donwloaded.
     * 
     * @return the URI for the download.
     */
    public URI getUri() {
        return uri;
    }

    /**
     * The destination file where to store the downloaded data.
     * 
     * @return the destination file where to store the downloaded data.
     */
    public File getDestination() {
        return destination;
    }

    /**
     * Specifies whether the download operation should be resumed.
     * If true and the destination file is not empty, the download implementation will attempt to fetch only the missing
     * data.
     * 
     * @return whether the download operation should be resumed.
     */
    public boolean shouldResume() {
        return shouldResume;
    }

    /**
     * Specifies whether the download operation should be forced.
     * If true, the download operation will be performed even if the destination file exisits.
     * 
     * @return whether the download operation should be forced.
     */
    public boolean shouldForceDownload() {
        return forceDownload;
    }

    /**
     * Returns a checksum used for validating the downloaded data, if specified.
     * 
     * @return the checksum.
     */
    public Optional<Hash> getChecksum() {
        return checksum;
    }

    /**
     * Return the credentials to be used for authentication, if specified.
     * 
     * @return the credentials
     */
    public Optional<Credentials> getCredentials() {
        return credentials;
    }

    /**
     * A timeout parameter for the download, its meaning depends on the implementation.
     * 
     * @return the timeout parameter.
     */
    public Optional<Long> getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * Returns the block delay. If not empty, the download implementation will wait block delay milliseconds after
     * downloading each block
     * 
     * @return the block delay
     */
    public Optional<Long> getBlockDelay() {
        return blockDelay;
    }

    /**
     * 
     * Returns the minimum amount of that that needs to be transferred before emitting a progress event.
     * 
     * @return the notification block size
     */
    public Optional<Long> getNotificationBlockSize() {
        return notificationBlockSize;
    }

    /**
     * Returns the block size.
     * The download implementation will split the whole operation in multiple re
     * 
     * @return the block size
     */
    public Optional<Long> getBlockSize() {
        return blockSize;
    }

    /**
     * Returns some implementation specific properties.
     * 
     * @return implementation specific properties.
     */
    public Map<String, Object> getExtraProperties() {
        return extraProperties;
    }

    /**
     * Creates a builder for creating a new {@link DownloadParameters} instance.
     * 
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI uri;
        private File destination;
        private boolean shouldResume;
        private boolean forceDownload = true;

        private Optional<Hash> checksum = Optional.empty();
        private Optional<Credentials> credentials = Optional.empty();

        private Optional<Long> blockSize = Optional.of(4096L);
        private Optional<Long> timeoutMs = Optional.of(60000L);
        private Optional<Long> blockDelay = Optional.empty();
        private Optional<Long> notificationBlockSize = Optional.empty();

        private Map<String, Object> extraProperties = Collections.emptyMap();

        /**
         * Sets the request URI
         * 
         * @param uri
         *            the request URI
         * @return the current builder instance for chaining.
         */
        public Builder withUri(final URI uri) {
            this.uri = uri;
            return this;
        }

        /**
         * Sets the destination file.
         * 
         * @param destination
         *            the destination file.
         * @return the current builder instance for chaining.
         */
        public Builder withDestination(final File destination) {
            this.destination = destination;
            return this;
        }

        /**
         * Sets the resume parameter.
         * 
         * @param shouldResume
         *            the resume parameter.
         * @return the current builder instance for chaining.
         */
        public Builder withResume(final boolean shouldResume) {
            this.shouldResume = shouldResume;
            return this;
        }

        /**
         * Sets the force download parameter.
         * 
         * @param forceDownload
         *            the force download parameter.
         * @return the current builder instance for chaining.
         */
        public Builder withForceDownload(final boolean forceDownload) {
            this.forceDownload = forceDownload;
            return this;
        }

        /**
         * Sets the checksum parameter
         * 
         * @param checksum
         *            the checksum parameter
         * @return the current builder instance for chaining.
         */
        public Builder withChecksum(final Optional<Hash> checksum) {
            this.checksum = checksum;
            return this;
        }

        /**
         * Sets the operation timeout in milliseconds.
         * 
         * @param timeoutMs
         *            the operation timeout in milliseconds.
         * @return the current builder instance for chaining.
         */
        public Builder withTimeoutMs(final Optional<Long> timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        /**
         * Sets the credentials.
         * 
         * @param credentials
         *            the credentials.
         * @return the current builder instance for chaining.
         */
        public Builder withCredentials(final Optional<Credentials> credentials) {
            this.credentials = credentials;
            return this;
        }

        /**
         * Sets the block size.
         * 
         * @param blockSize
         *            the block size.
         * @return the current builder instance for chaining.
         */
        public Builder withBlockSize(final Optional<Long> blockSize) {
            this.blockSize = blockSize;
            return this;
        }

        /**
         * Sets the block delay.
         * 
         * @param blockDelay
         *            the block delay.
         * @return the current builder instance for chaining.
         */
        public Builder withBlockDelay(final Optional<Long> blockDelay) {
            this.blockDelay = blockDelay;
            return this;
        }

        /**
         * Sets the notification block size.
         * 
         * @param notificationBlockSize
         *            the notification block size.
         * @return the current builder instance for chaining.
         */
        public Builder withNotificationBlockSize(final Optional<Long> notificationBlockSize) {
            this.notificationBlockSize = notificationBlockSize;
            return this;
        }

        /**
         * Sets additional protocol specific properties.
         * 
         * @param extraProperties
         *            the additional properties.
         * @return the current builder instance for chaining.
         */
        public Builder withExtraProperties(final Map<String, Object> extraProperties) {
            this.extraProperties = extraProperties;
            return this;
        }

        private static long requirePositive(final long value, final String message) {
            if (value <= 0) {
                throw new IllegalArgumentException(message);
            }
            return value;
        }

        /**
         * Creates a new {@link DownloadParameters} instance.
         * 
         * @return the newly created instance.
         * @throws IllegalArgumentException
         *             if parameter validation fails.
         */
        public DownloadParameters build() {

            try {
                final DownloadParameters result = new DownloadParameters();

                result.uri = requireNonNull(uri, "Request URI cannot be null");
                result.destination = requireNonNull(destination, "Destination file cannot be null");
                result.shouldResume = shouldResume;
                result.forceDownload = forceDownload;
                result.checksum = requireNonNull(checksum, "Checksum cannot be null");

                result.credentials = requireNonNull(credentials, "Credentials cannot be null");

                result.timeoutMs = requireNonNull(timeoutMs, "Timeout cannot be null")
                        .map(v -> requirePositive(v, "timeout must be positive"));
                result.blockSize = requireNonNull(blockSize, "Block size cannot be null")
                        .map(v -> requirePositive(v, "block size must be positive"));
                result.blockDelay = requireNonNull(blockDelay, "Block delay cannot be null")
                        .map(v -> requirePositive(v, "block delay must be positive"));
                result.notificationBlockSize = requireNonNull(notificationBlockSize,
                        "Notification block size cannot be null")
                                .map(v -> requirePositive(v, "notificaiton block size must be positive"));

                result.extraProperties = Collections.unmodifiableMap(requireNonNull(extraProperties));

                return result;
            } catch (final IllegalArgumentException e) {
                throw e;
            } catch (final Exception e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }

        }
    }
}
