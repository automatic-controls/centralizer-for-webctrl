# Centralizer for WebCTRL

WebCTRL is a trademark of Automated Logic Corporation.  Any other trademarks mentioned herein are the property of their respective owners.

## WORK IN PROGRESS

## Contents

- [Overview](#overview)
- [Database Installation](#database-installation)
- [Add-On Configuration](#add-on-configuration)
- [Operator Management](#operator-management)
- [Server Management](#server-management)
- [File Synchronization](#file-synchronization)
- [Data Collection](#data-collection)

## Overview

Includes a database application that runs as a *Windows* service and a WebCTRL add-on that communicates with the database. The primary function is to synchronize operator credentials and/or files across connected WebCTRL servers. This add-on is intended to be useful for *Automated Logic* dealer branches that maintain hundreds of WebCTRL servers on behalf of clients.

Whenever an employee joins or leaves an *Automated Logic* dealer, the IT guy typically needs to connect to each WebCTRL server individually to update operator lists. Since it could take awhile for the IT guy to get around to every server, there is a security vulnerability that terminated employees could cause problems before their credentials are deleted. It can also be a struggle to change compromised operator passwords or out-dated contact information on every server. There is more tedious work when new versions of add-ons are released and need to be updated on each server.

Centralizer for WebCTRL eliminates all the problems mentioned in the previous paragraph. Operator credentials and contact information can be updated in one place, and specific add-on files can be synchronized across any subset of WebCTRL servers connected to the database. Add-ons will automatically restart on each server when updates are required.

Local operators specific to client WebCTRL servers are unaffected by database synchronization protocols. *Local operators* are defined to be those operators that are managed by WebCTRL in the typical manner. The operators managed by the Centralizer database will be referred to as *central operators*. **The Centralizer add-on does not interfere with local operators.**

Since *Automated Logic* dealer branch employees are assumed to be responsible, all central operators are given local administrative privileges on each WebCTRL server. There is a separate set of [global database privileges](#TODO) that may be altered among central operators.

Before proceeding, there are some network requirements that must be met. The host computer for the database must be able to accept incoming connections from the IP address of each WebCTRL server. Similarly, each WebCTRL server must be permitted to establish out-bound connections to the database computer's IP address.

All network traffic between the database and each WebCTRL server is securely encrypted using a form of XOR stream cipher with symmetric keys established through RSA encryption. [Forward secrecy](https://en.wikipedia.org/wiki/Forward_secrecy) of each symmetric key is ensured.

## Database Installation

1. Identify a Windows machine you control with nearly 100% uptime. Choose a port to use for network communication (the default is 1978). Ensure port-forwarding is setup on your network firewall to allow inbound connections from all WebCTRL server IPs.

1. Download the latest release of the [database installation files](https://github.com/automatic-controls/centralizer-for-webctrl/releases/latest/download/Database.zip).

1. Unzip and place the installation files into an empty folder in a secure location on your Windows machine.

1. Run *./install.vbs* to install and start the database Windows service on your computer.

1. Run *./stop.vbs* or stop the database service using alternate means. At this point, the application should have generated files under the *./data* directory.

1. Make necessary changes to *./data/config.txt* and restart the database service. During this step, you may change the port to which the database binds. Note that all such manual modifications should take place while the database is inactive (or risk having your changes overwritten). You will want to record the values of *PublicKeyHash* and *ConnectionKey* from this configuration file, since they will be used for configuring the add-on component. To decrease network traffic, you may wish to increase the *PingInterval* to a few minutes.

   | Name | Description |
   | - | - |
   | *PublicKeyHash* | This value should be compared to the hash shown on each add-on's main page. If the hashes do not match, you may be the victim of a [man-in-the-middle cyber attack](https://en.wikipedia.org/wiki/Man-in-the-middle_attack). |
   | *Version* | Specifies the version of the application which generated the data files. On initialization, this value is used to determine compatibility. |
   | *Port* | Specifies the port bound to the database for listening to incoming connections. |
   | *ConnectionKey* | You need this value to connect new WebCTRL servers to the database. |
   | *PacketCapture* | Specifies whether to record encrypted data packets sent over the network in a file *./data/packet_capture.txt*. This should not be enabled for log peroids of time. The primary purpose is debugging.  |
   | *BackupTime* | Specifies the time of day to backup data in RAM to the hard-drive *./data* folder. |
   | *BackLog* | Specifies the maximum number of pending network connections for processing. All incoming connections will be rejected after this limit is surpassed. |
   | *Timeout* | Specifies how long to wait (in milliseconds) for connected WebCTRL servers to respond before assuming the connection has been lost. |
   | *OperatorTimeout* | Specifies how long (in milliseconds) it takes for an operator to be automatically logged off the database due to inactivity. |
   | *PingInterval* | Specifies how often (in milliseconds) that connected WebCTRL servers should attempt to ping the database. |
   | *DeleteLogAfter* | Specifies how long (in milliseconds) to keep historical log records. |
   | *LoginAttempts* | Operators are allowed a maximum of *LoginAttempts* failed logins during any period of *LoginTimePeriod*. If exceeded, an operator lockout of *LoginLockoutTime* is incurred. |
   | *LoginTimePeriod* | Specified in milliseconds. See the description for *LoginAttempts*. |
   | *LoginLockoutTime* | Specified in milliseconds. See the description for *LoginAttempts*. |