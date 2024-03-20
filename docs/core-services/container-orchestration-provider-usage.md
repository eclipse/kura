# Container Orchestration Provider Usage



## Before Starting

For this bundle to function appropriately, the gateway must have a supported container engine installed and running. Currently, the only officially supported engine is Docker.



## Starting the Service

To use this service select the **ContainerOrchestrationService** option located in the **Services** area. The ContainerOrchestrationService provides the following parameters:

- **Enabled**--activates the service when set to true
- **Container Engine Host URL**--provides a string that tells the service where to find the container engine (best left to the default value).
- **Allowlist Enforcement Enabled**--activates the container enforcement of the service, which let only the allowed containers to run
- **Container Image Allowlist Content**--the comma-separated list of conainer's digests allowed to be run

![Container Orchestration Provider](./images/container-orchestration-provider.png)



## Creating your first container.

To create a container, select the `+` icon (Create a new component) under **services**. A popup dialogue box will appear. In the field **Factory** select **org.eclipse.kura.container.provider.ContainerInstance** from the drop-down. Then, use the **Name** field to specify a name for the container.

!!! note
    The name specified in the 'Name' field will also be the name of the container when it is spun up by the orchestrator.

After pressing submit, a new component will be added under the **services** tab, with the name that was selected in the dialogue. Select this component to finish configuring the container.



## Configuring the container

To begin configuring the container, look under **Services** and select the item which has the name set in the previous step. Containers may be configured using the following fields:

- **Enabled** - When true, the service will create the defined container. When false the API will not create the container or will destroy the container if already running.
  
- **Image Name** - Describes the image that will be used to create the container. Remember to ensure that the selected image supports the architecture of the host machine, or else the container will not be able to start.
  
- **Image Tag** - Describes the version of the container image that will be used to create the container.

- **Authentication Registry URL** - URL for an alternative registry to pull images from. (If the field is left blank, credentials will be applied to Docker-Hub). Please see the [Authenticated Registries](./container-orchestration-provider-authenticated-registries.md) document for more information about connecting to different popular registries.

- **Authentication Username** - Describes the username to access the container registry entered above.

- **Password** - Describes the password to access the alternative container registry.

- **Image Download Retries** - Describes the number of retries the framework will attempt to pull the image before giving up.

- **Image Download Retry Interval** - Describes the amount of time the framework will wait before attempting to pull the image again.

- **Image Download Timeout** - Describes the amount of time the framework will let the image download before timeout.
  
- **Internal Ports** - This field accepts a comma-separated list of ports that will be internally exposed on the spun-up container. In this field, you can also specify which protocol to run at the port by appending a port with a colon and typing in the name of the network protocol. Example: `80, 443:tcp, 8080:udp`.
  
- **External Ports** - This field accepts a comma-separated list of ports that will be externally exposed on the host machine.
  
- **Privileged Mode** - This flag if enabled will give the container root capabilities to all devices on the host system. Please be aware that setting this flag can be dangerous, and must only be used in exceptional situations.
  
- **Environment Variables (optional)** - This field accepts a comma-separated list of environment variables, which will be set inside the container when spun up.
  
- **Entrypoint Override (optional)** - This field accepts a comma-separated list which is used to override the command used to start a container. Example: ```./test.sh,-v,-d,--human-readable```.

- **Memory (optional)** - This field allows the configuration of the maximum amount of memory the container can use in bytes. The value is a positive integer, optionally followed by a suffix of b, k, m, g, to indicate bytes, kilobytes, megabytes, or gigabytes. The minimum and default values depends by the native container orchestrator. If left empty, the memory assigned to the container will be set to a default value.

- **CPUs (optional)** - This value specifies how many CPUs a container can use. Decimal values are allowed, so if set to 1.5, the container will use at most one and a half cpu resource.

- **GPUs (optional)** - This field configures how many Nvidia GPUs a container can use. Allowed values are `all` or an integer number. If there's no Nvidia GPU installed, leave it empty. The Nvidia Container Toolkit must be installed on the system to correctly configure the service, otherwise the container will not start. If the Nvidia Container Runtime is used, leave the field empty.

- **Volume Mount (optional)** - This field accepts a comma-separated list of system-to-container file mounts. This allows for the container to access files on the host machine.
  
- **Peripheral Device (optional)** - This field accepts a comma-separated list of device paths. This parameter allows devices to be passed through from the host to the container.

- **Runtime (optional)**: Specifies the fully qualified name of an alternate OCI-compatible runtime, which is used to run commands specified by the 'run' instruction. Example: `nvidia` corresponds to `--runtime=nvidia`. Note:  when using the Nvidia Container Runtime, leave the **GPUs** field empty. The GPUs available on the system will be accessible from the container by default.

- **Networking Mode (optional)** - Use this field to specify what networking mode the container will use. Possible Drivers include: bridge, none, container:{container id}, host. Please note that this field is case-sensitive. This field can also be used to connect to any of the networks listed by the cli command ```docker network ls```.

- **Logger Type** - This field provides a drop-down selection of supported container logging drivers.

- **Logger Parameters (optional)** - This field accepts a comma-separated list of logging parameters. More information can be found in the container-engine logger documentation, for instance [here](https://docs.docker.com/config/containers/logging/configure/).

- **Restart Container On Failure** - A boolean that tells the container engine to automatically restart the container when it has failed or shut down.

- **Container Image Enforcement Digest** - A string representing the image digest allowed to be run from this container instances. It is used in the [Container Enforcement](#container-enforcement) service provided by the Container Orchestration Service.

After specifying container parameters, ensure to set **Enabled** to **true** and press **Apply**. The container engine will then pull the respective image, spin up and start the container. If the gateway or the framework is power cycled, and the container and Container Orchestration Service are set to **enabled**, the framework will automatically start the container again upon startup.

![Container Orchestration Provider Container Configuration](./images/container-orchestration-provider-container-configuration.png)



## Stopping the container

!!! warning
    Stopping a container will delete it in an irreversible way. Please be sure to only use stateless containers and/or save their data in external volumes.

To stop the container without deleting the component, set the **Enabled** field to **false**, and then press **Apply**. This will delete the running container, but leave this component available for running the container again in the future. If you want to completely remove the container and component, press the **Delete** button to the top right of the screen, and press **Yes** on the confirmation dialogue.



## Container Management Dashboard

The Container Orchestration service also provides the user with an intuitive container dashboard. This dashboard shows all containers running on a gateway, including containers created with the framework and those created manually through the command-line interface. To utilize this dashboard the `org.eclipse.container.orchestration.provider` (ContainerOrchestrationService) must be enabled, and the dashboard can be opened by navigating to Device > Containers.

## Container Enforcement

The Container Orchestration service also provides a security feature which dictates the containers allowed to run on the system. This feature can be enabled or disabled through the `Allowlist Enforcement Enabled` option.

This mechanism leverages the concept of _digest_, which is a unique and immutable identifier for a container image: it is therefore an excellent mean to identify the image from which a container was created.

Given the above, Kura allows the user to provide a list of container image digests in the `Container Image Allowlist` field, in the form of newline-separated strings: when a container is started on the host system (whether it is launched by Kura or by a terminal CLI), Kura retrieves the image from which the container was created, extracts the digest and verifies that it is present in the allowlist provided during service configuration.

If the image digest is present, the container is allowed to proceed without interference, otherwise it is immediately stopped and deleted from the host system.

A similar behaviour is performed at Kura startup or when the enforcement is enabled at runtime: if there are already containers on the device (whether they are started or stopped) and the enforcement is activated, Kura extracts the digests from the containers and compares them with those in the allowlist. Again, containers that have been created from images whose digests are in the allowlist will be left intact, otherwise they will be stopped and deleted from the descriptors list.

The verification is performed by intersecting the list of digests extracted by the containers and the one provided in the configuration: in this way, an empty intersection will result in a failing verification, while a non-empty one means that the image digests is equal to one of the allowlist entries.

![Enforcement Flow](./images/container-orchestration-provider-enforcement-flow.png)

### Example scenario

A user wants to leverage the container enforcement in order to let only docker containers started from an image named `foo_image` to be run on the device. To do this, they should enable the Container Enforcement by setting the `Allowlist Enforcement Enabled` to `true`, and fill the `Container Image Allowlist` field with the digest of the `foo_image` docker image (i.e.`sha256:0000000000000000000000000000000000000000000000000000000000000000` in the example below).

![Container Orchestration Provider Enfrocement](./images/container-orchestration-provider-enforcement.png)

#### Startup of the Enforcement Feature

Let's suppose that on the device there are already two containers running, one created from the `foo_image` an one from an image named `unwanted_image` with digest `sha256:9999999999999999999999999999999999999999999999999999999999999999`. Once the enforcement starts with the configuration previously described, it extracts the digests of both containers and checks if they're included in the provided allowlist: in this case, the container originating from the `foo_image` will be allowed to run, while the one created from the `unwanted_image` will be stopped and deleted, because its digest is not included in the allowlist.

The same happens also in case the containers are stopped: if the digests is verified, they'll be left in the descriptors list in the Stopped state, otherwise they'll be deleted.

#### Running Container After Enforcement Feature Startup

After the starting phase just described, Kura will continuously monitor the activity of the docker engine. Whenever a container is started on the device, it will check the image digest from which the container was created, comparing it to the ones inside the allowlist: if the container is created from the `foo_image` docker image, it will be allowed to start, otherwise it will be stopped and deleted. If the user wants to add more than one allowed image, for example one named `wanted_image` with digest `sha256:1111111111111111111111111111111111111111111111111111111111111111`), it just needs to add it to the newline-separated list.

![Container Orchestration Provider Enfrocement Multiple Digests](./images/container-orchestration-provider-enforcement-multiple-digests.png)


## Container Instance Enforcement

In the [Container Instance configuration](#configuring-the-container) is included an option that allows to add a specific digest to the enforcement allowlist: this option was designed to allow the authorization of a specific image, but only when a Container Instance run by Kura is using it.

Everytime the enforcement feature performs a check on its startup or when a new container is started, it will consider as authorized all the digests included in the Container Orchestration Allowlist and those provided by the enabled Container Instances in Kura through the *Container Image Enforcement Digest* option.


So the authorization schemas presented in the [previous section](#container-enforcement) are updated like:
![Enforcement Flow With Instances](./images/diagramAllowlistWithInstances.png)

As you can see, the *ContainerInstance2* digest is not included in the final allowlist, because it is disabled: so, if a container with digest *DIGEST Y* is started under the image condition, it will then be stopped and deleted.

Finally, everytime a ContainerInstance is disabled, deleted or updated, the enforcement feature will perform a check on all the running containers. This is done because the enforcement allowlist may no longer include the previously provided digest or the latter may have been replaced with a new one: there may therefore be containers that were previously allowed, But now they are no longer and must be stopped and deleted.

!!! warning

    The digests provided through Container Instances allow running also container started by the CLI (only if respecting the digest match just described). Keep in mind that if the ContainerInstance is disabled (or its digest option changed) the enforcement feature will stop and delete also the *cli-based* containers that are no longer matching the provided digest.

    Be careful, then, to rely only on the digests set in the ContainerInstances options. If you think you need to run containers from the CLI, it is preferable to use the allowlist of the Container Orchestration Service.