---
layout: page
title:  "Clock Service"
categories: [builtin]
---

The ClockService handles the date and time management of the system. If enabled, it tries to update the system date and time using a Network Time Protocol (NTP) server. NTP can use NTS as authentication mechanism through chrony.

To manage the system date and time, select the **ClockService** option located in the **Services** area as shown in the screen capture below.

![clock_service]({{ site.baseurl }}/assets/images/builtin/clock_service.png)

The ClockService provides the following configuration parameters:

- **enabled** - sets whether or not this service is enabled or disabled. (Required field.)

- **clock.set.hwclock** - defines if the hardware clock of the gateway must be synced after the system time is set. If enabled, the service calls the Linux command "hwclock --utc --systohc".

- **clock.provider** - specifies one among Java NTP client (java-ntp), Linux chrony command (chrony), Linux ntpdate command (ntpd). (Required field.)

- **clock.ntp.host** - sets a valid NTP server host address.

- **clock.ntp.port** - sets a valid NTP port number.

- **clock.ntp.timeout** - specifies the NTP timeout in milliseconds.

- **clock.ntp.max-retry** - defines the number of retries when a sync fails (retry at every minute). Subsequently, the next retry occurs on the next refresh interval.

- **clock.ntp.retry.interval** - defines the interval in seconds between each retry when a sync fails. If the _clock.ntp.refresh-interval_ parameter is less than zero, there is no update. If the _clock.ntp.refresh-interval_ parameter is equal to zero, there is only one try at startup. (Required field.)

- **clock.ntp.refresh-interval** - defines the frequency (in seconds) at which the service tries to sync the clock. Note that at the start of ESF, when the ClockService is enabled, it tries to sync the clock every minute until it is successful. After a successful sync, this operation is performed at the frequency defined by this parameter. If the value is less than zero, there is no update. If the value is equal to zero, syncs only once at startup.

- **chrony.advanced.config** - specifies the content of the chrony configuration file. If this field is left blank, the default system configuration will be used. For further information: [chrony website](https://chrony.tuxfamily.org/doc/4.1/chrony.conf.html)

Two example configuration are shown below:

NTS Secure configuration example[^1]

```
server time.cloudflare.com iburst nts
server nts.sth1.ntp.se iburst nts
server nts.sth2.ntp.se iburst nts

sourcedir /etc/chrony/sources.d

driftfile /var/lib/chrony/chrony.drift

logdir /var/log/chrony

maxupdateskew 100.0

rtcsync

makestep 1 3

leapsectz right/UTC
```

Simple configuration example[^2]

```
# Use public NTP servers from the pool.ntp.org project.
pool pool.ntp.org iburst

# Record the rate at which the system clock gains/losses time.
driftfile /var/lib/chrony/drift

# Allow the system clock to be stepped in the first three updates
# if its offset is larger than 1 second.
makestep 1.0 3

# Enable kernel synchronization of the real-time clock (RTC).
rtcsync
```

[^1]: https://fedoramagazine.org/secure-ntp-with-nts
[^2]: https://git.tuxfamily.org/chrony/chrony.git/tree/examples/chrony.conf.example1