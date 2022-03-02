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