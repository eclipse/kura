package org.eclipse.kura.container.signature;

import java.util.Objects;
import java.util.Optional;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Class representing the result of the signature validation performed by {@link:ContainerSignatureValidationService}
 * 
 * The result of the validation is composed of two main parts: whether or not the container image signature was
 * validated and the container image digest (in the "algorithm:encoded" format, @see
 * <a href="https://github.com/opencontainers/image-spec/blob/main/descriptor.md#digests">Opencontainers specs</a>)
 *
 * If the signature is valid, the image digest MUST be provided.
 *
 * @since 2.7
 */
@ProviderType
public final class ValidationResult {

    private boolean isSignatureValid = false;
    private Optional<String> imageDigest = Optional.empty();

    public ValidationResult() {
        // Nothing to do
    }

    public ValidationResult(boolean signatureValid, String digest) {
        this.imageDigest = Optional.of(Objects.requireNonNull(digest));
        this.isSignatureValid = Objects.requireNonNull(signatureValid);

        if (this.isSignatureValid && (!this.imageDigest.isPresent() || this.imageDigest.get().isEmpty())) {
            throw new IllegalArgumentException("Image digest must be provided when signature is valid.");
        }
    }

    public boolean isSignatureValid() {
        return this.isSignatureValid;
    }

    public Optional<String> imageDigest() {
        return this.imageDigest;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ValidationResult other = (ValidationResult) obj;

        return this.isSignatureValid == other.isSignatureValid && this.imageDigest.equals(other.imageDigest);

    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (this.imageDigest == null ? 0 : this.imageDigest.hashCode());
        result = prime * result + Boolean.hashCode(isSignatureValid);
        return result;

    }

}
