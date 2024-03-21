# Kura Container Image Authenticity and Allowlist Enforcement feature

Containerized applications are becoming increasingly popular in the software ecosystem as a way to deploy and distrubute applications. As a result, ensuring the security of software supply chains has become a critical concern. Implementing best practices, such as signing and verifying images to mitigate man-in-the-middle (MITM) attacks and validating their authenticity and freshness, play a pivotal role in safeguarding the integrity of the software supply chain.

To ensure the authenticity and integrity of the code within the container images, binding the image to a specific entity or organization via signature verification is crucial and increasingly common.

The purpose of this document is to provide a high-level understanding of the Kura container authenticity and allowlist enforcement feature introduced with **Kura version 5.5.0**.

## High-level flow

The Kura container authenticity and allowlist enforcement feature is designed to address container authenticity concerns by providing a mechanism to perform the signature verification of container images and restricting which container images can be deployed on the Kura platform.

This is achieved by implementing the following flow:

![](./images/container-auth-high-level-flow.png){ width="600" }

Description of the flow:

- **Monitoring**: Kura monitors the container engine events. When a "container start" event is triggered it intercepts the information regarding the image used to spin up the container and starts performing the check.
- **Allowlist** Firstly it checks if the digest of the image used to run the container can be found in the Kura allowlist. If so, the container is allowed to run. If not, Kura proceeds with the image signature verification.
- **Signature verification**: If no trust anchor (i.e. a public key or a X509 certificate depending on the signature mechanism) is available for the verification process to happen, the container is not allowed to run. If one is available, the image signature is verified and the allowlist is updated accordingly.

This flow applies to both containers managed by Kura itself and containers ran directly by the user via CLI and allows for a fine-grained control over the container images that can be deployed on the Kura platform.

- **Unmanaged containers**: Kura allows the user to define a list of container images that can be deployed on the platform. This is done by adding the image digests to the allowlist. When a container is started, Kura checks if the image digest is in the allowlist and, if so, allows the container to run. This is meant to be used by users who, for any reason, need to run a container _outside_ the Kura framework but still want the safety guarantees provided by pinning the images to a specific digest.
- **Managed containers**: Kura also allows the user, for containers ran by Kura itself, to provide a trust anchor to be used for the signature verification process. This allows the user to use a _mutable_ tag when specifying the container image, without giving up the required authenticity checks. Onche the image is verified its digest is stored within the Kura allowlist allowing it to be ran both from Kura and the CLI without the need for an internet connection. When a new image is published the user can simply trigger the pull and the verification process will update the digest automatically.

Note that the user can still directly provide an image digest when running a Kura-managed container, bypassing the signature verification process. This is meant to be used when the user is sure about the image authenticity and does not want to go through the verification process or the image was not signed.

## Unmanaged containers

WIP

## Managed containers and Container Signature verification

As explained in the previous section, Managed Containers (i.e. `org.eclipse.kura.container.provider.ContainerInstance`s) support Container Signature validation. To do so we introduced a new API: the [`ContainerSignatureValidationService`](https://github.com/eclipse/kura/blob/develop/kura/org.eclipse.kura.api/src/main/java/org/eclipse/kura/container/signature/ContainerSignatureValidationService.java).

Currently there's three main mechanism for container signature verification:

- [Docker Content Trust (DCT)](https://docs.docker.com/engine/security/trust/) a.k.a. Notary V1
- [Notation](https://notaryproject.dev/) a.k.a. Notary V2
- [Sigstore's Cosign](https://docs.sigstore.dev/signing/quickstart/)

This newly introduced service allows Kura to have multiple different Services implementing this interface and thus support all major signature mechanisms. A reference implementation for the `ContainerSignatureValidationService` is the `DummyContainerSignatureValidationService` available [in Kura `examples` folder](https://github.com/eclipse/kura/blob/develop/kura/examples/org.eclipse.kura.example.container.signature.validation/src/org/eclipse/kura/example/container/signature/validation/DummyContainerSignatureValidationService.java) for anyone wanting to implement an alternative `ContainerSignatureValidationService`.

When a `ContainerSignatureValidationService` is installed on Kura, it gets automatically registered among the available validators and will be used to perform the signature validation. Under the hood, the `ContainerInstance` has a list of the available `ContainerSignatureValidationService` providers that, upon receiving a configuration update, it interrogates to check whether the requested container image is authentic using the provided **Trust Anchor**.

- If even _only one_ service reports the signature as valid, the image signature is considered valid.
- If none of the available services reports the signature as valid, the image signature is considered _not valid_.

This means that Kura doesn't need to prompt the user for selecting the correct `ContainerSignatureValidationService` when performing the check.

Once the image signature is validated, the image digest is stored in the Kura snapshot and added to the `ContainerOrchestratorService` allowlist (see section above). A container whose image digest is available in the allowlist won't be validated again, therefore after the initial check, the internet connection is no longer required for it work.
