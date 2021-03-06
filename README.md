# Moustack
> A [KISS](https://en.wikipedia.org/wiki/KISS_principle) deployment framework for [Openstack](http://www.openstack.org) clouds

Moustack is a collection of tools and Docker images providing a simple way of deploying and maintaining an **Openstack** installation.

---

 - [Features](#features)
 - [Getting started](#getting-started)
 - [Architecture](#archirecture)
   - [Moustack components](#moustack-components)
   - [Openstack components](#openstack-components)
 - [Usage](#usage)
   - [Prerequisites](#prerequisites)
   - [Installation](#installation)
   - [Profiles](#profiles)
   - [Deployment](#deployment)
   - [Operating](#operating)
 - [Development](#development)
   - [Building](#building)
   - [Publishing](#publishing)
 - [FAQ](#faq)
 - [Contributing](#contributing)
 - [License](#license)

## Features

### Moustack components

#### Server

The server is a lightweight Java web server that runs on a separate node. It provides stack global configuration for the agents, stores reports and can trigger agents deployment. Everything is exposed as REST endpoints so that you can drive it with simple `curl` calls, but a web interface is also available.

*Note*: if you don't have enough machines, the Moustack Server can be run on the controller (you will have to add a firewall rule to access the REST API though).

#### Agent

The agent is a small Java application that runs on Openstack nodes and manages them. It is responsible to pull the configuration, deploy it and post reports to the server. By default, once started the agent will only connect to the server and wait for commands.

#### Profiles

The profiles are the only part of Moustack that you will have to really work with as it holds the definition of modules along with their configuration. Profiles are explained more in details in the architecture section.

### Openstack components

Moustack is able to deploy any Openstack project (even out of the Openstack's scope), as soon as you have a valid configuration in your Moustack's profiles, and an associated docker images.

However, Moustack only provides configuration templates and Docker images for the following projects :

 - [Ceilometer](http://docs.openstack.org/developer/ceilometer/)
 - [Cinder](http://docs.openstack.org/developer/cinder/)
 - [Designate](http://docs.openstack.org/developer/designate/)
 - [Glance](http://docs.openstack.org/developer/glance/)
 - [Heat](http://docs.openstack.org/developer/heat/)
 - [Horizon](http://docs.openstack.org/developer/horizon/)
 - [Keystone](http://docs.openstack.org/developer/keystone/)
 - [Neutron](http://docs.openstack.org/developer/neutron/)
 - [Nova](http://docs.openstack.org/developer/nova/)

### Other components

Moustack also provides configuration templates and Docker images for the following projects, as they are Openstack's dependencies, or nice-to-have services :

 - [ISC Bind](https://www.isc.org/downloads/bind/)
 - [Logstash](https://www.elastic.co/products/logstash)
 - [MariaDB](https://mariadb.org/)
 - [MongoDB](https://www.mongodb.com)
 - [RabbitMQ](https://www.rabbitmq.com/)
 - [Samba](https://www.samba.org/)

## Getting started

### Quick start

#### Assumptions

As networking is the most important part in Openstack deployment, we will make the following assumptions :

| network | variable | value |
| ------- | -------- | ----- |
| management | interface | `eth0` |
|  | address | `192.168.29.204` |
|  | netmask | `255.255.255.0` |
|  | gateway | `192.168.29.1` |
|  | DNS | `192.168.29.1` |
| public | interface | `eth1` |
|  | address | `172.18.0.10` |
|  | netmask | `255.255.252.0` |
|  | gateway | `172.18.0.1` |
| tenant | interface | `eth2` |

In this example, a fresh `Ubuntu 16.04` is used.

If you want to try Moustack in a virtual machine, you may look at our [Packer images](#packer-images). An all-in-one installation on a fresh system requires at least 7GB of disk space, so be carefull with the allocated size when building packer images (see `disk_size` packer parameter). To be able to run virtual machines within the Moustack VM, you'll need to enable the `nested` parameter to your `KVM` kernel module.

#### Configure networking

If not already done, you'll need to configure management network interface:

```bash
# configure network management interface
cat >/etc/network/interfaces.d/management.conf <<EOF
auto eth0
iface eth0 inet static
    address         192.168.29.204
    broadcast       192.168.29.255
    netmask         255.255.255.0
    gateway         192.168.29.1
    dns-nameservers 192.168.29.1
EOF

# reboot
reboot
```

#### Configure apt ####

If you have an HTTP proxy, you can configure it using:

```bash
echo 'Acquire::http::Proxy "http://${YOUR_HTTP_PROXY_HOST}:${YOUR_HTTP_PROXY_PORT}";' >/etc/apt/apt.conf.d/99proxy
```

#### Install dependencies ####

Install Java JRE:

```bash
apt-get update
apt-get install -y --no-install-recommends default-jre-headless
```

Optional: to speedup docker layers handling, you may want to install AUFS filesystem kernel module:

```bash
apt-get install -y --no-install-recommends linux-image-extra-$(uname -r)
```

#### Install moustack's server ####

Download the latest server package from https://github.com/adenops/moustack/releases, then install it:

```bash
wget --output-document /tmp/moustack-server.deb https://github.com/adenops/moustack/releases/download/RELEASE_SERVER_DEB
dpkg -i /tmp/moustack-server.deb
```

#### Configure moustack's server ####

Create the `/etc/moustack-server` configuration file with the following content:

```
# Server authentication
server.user=moustack
server.password=mypassword

# Server port
server.port=8080

# Profiles repository URL
git.repo.uri=https://github.com/adenops/moustack-profiles.git

# Database type, mysql or hsql
database.type=hsql

# Override Docker registry url, example: myregistry.local:5000
docker.registry.url=

# Override Docker tag for Moustack images
docker.moustack.tag=liberty

# Log level
log.level=DEBUG
```

#### Start moustack's server ####

```bash
systemctl enable moustack-server
systemctl start moustack-server
```

#### Install moustack's agent ####

Download the latest agent package from https://github.com/adenops/moustack/releases, then install it:

```bash
wget --output-document /tmp/moustack-server.deb https://github.com/adenops/moustack/releases/download/RELEASE_AGENT_DEB
dpkg -i /tmp/moustack-agent.deb
```

#### Configure moustack's agent ####

Create the `/etc/moustack-agent` configuration file with the following content:

```
# Server authentication
server.user=moustack
server.password=mypassword

# Server address
server.url=http://127.0.0.1:8080

# Stack directory (where GIT will maintain stack configuration)
stack.dir=/var/lib/moustack/stack

# Stack profile to apply
stack.profile=liberty-standard

# Node ID (correspond to "node" in selected stack profile)
hostname=allinone

# Log level
log.level=DEBUG
```

#### Start moustack installation ####

As this is "Quick Start" instructions we will start moustack's agent interactively, because we need to override some properties in the `liberty-standard` profile from the github repository.

*Note*: be carefull with the network addresses (mentioned in the assumptions):

```bash
moustack-agent \
    -Dkeystone_admin_password=myadminpassword \
    -Dcontroller_management_ip=192.168.29.204 \
    -Dcontroller_public_ip=172.18.0.10 \
    -Dsyslog_host=192.168.29.204 \
    -Dmanagement_ip=192.168.29.204 \
    -Dmanagement_netmask=255.255.255.0 \
    -Dmanagement_iface=eth0 \
    -Dpublic_ip=172.18.0.10 \
    -Dpublic_netmask=255.255.252.0 \
    -Dpublic_iface=eth1 \
    -Drole=allinone-ubuntu-16.04 \
    -Dtenant_iface=eth2 \
    --run-once
```

#### Login ####

Once installed, you can login into the Horizon dashboard:
 - URL: http://192.168.29.204/horizon/
 - User: `admin`
 - Password: `myadminpassword`

You can also use CLI tools by sourcing the generated environment file:

```
root@moustack-allinone:~# source ~/keystonerc
[root@moustack-allinone ~(keystone_admin)]# nova list
+----+------+--------+------------+-------------+----------+
| ID | Name | Status | Task State | Power State | Networks |
+----+------+--------+------------+-------------+----------+
+----+------+--------+------------+-------------+----------+
[root@moustack-allinone ~(keystone_admin)]# openstack hypervisor list
+----+----------------------------------+
| ID | Hypervisor Hostname              |
+----+----------------------------------+
|  1 | moustack-allinone.moustack.local |
+----+----------------------------------+
```

### Configure your profile

#### Profile structure

TODO: snapshot of standard profile directory structure with some comments.

#### Files of interest

TODO: which values in which files to modify to adjust to your environment.

## Architecture

Moustack relies on a classical server-agent architecture to manage the Openstack configuration and deployments.

### Design principle

KISS (Keep it simple, stupid) is the design decision that drives our development of Moustack. Of course installing Openstack brings its challenges, can be complex to orchestrate and forces us to introduce a bit of complexity, but we try very hard to stick to the KISS principle as much as we can.

The area we focused particularly is the configuration management. Our goal is to let the user manage its configuration files directly, without having to rely on a templating mechanism. The idea is that we shorten the path between you finding which configuration option you want to change in a specific configuration file, and this modification to be applied to the system.

### History

Moustack started as an Openstack deployment tool for RedHat distributions. In an effort to support more distributions, we moved from distribution packages to Docker images for the Openstack services, so we can more easily control what version of Openstack and facilitate QA. This evolution also explains why Moustack has a full support to manage packages and service on the host (which we try to avoid with Docker).

### Profiles/Modules/Roles

*Let's start with profiles.*

Because we don't use templating, the configuration files are not generated dynamically. Profiles are used to define different deployment topologies that have major differences. For example we have a profile name "standard" that can deploys a simple Openstack all-in-one or multi-node topology. But if we want to deploy a stack with Ceph and Swift for example, this would probably involve modifying multiple service configurations, making everything interdependant. The concept of profiles has been introduced for this pupose. You shouldn't worry to much about this concept anyway, we only have one profile right now!

Example of profile: standard.

*Modules, the building bricks.*

The modules are the smallest unit in Moustack, they usually define a single service and its configuration. Two types of modules are currently supported: container (Docker) and system (OS packages and services). System module name are prefixed by `host-` by convention to make them easily identifiable.

Examples of modules: glance, neutron-controller. host-firewall, host-packages-ubuntu-16.04

*And finally the roles.*

Roles are the most natural concept, they define a global set of feature by including modules. Right now, a node has exactly one role, for example controller or compute and that's pretty much everything it need to define it (along with a bit of network configuration).

Examples of roles: allinone-ubuntu-16.04, controller-centos-7

So basically, modules define services (host or container), roles include modules, and profiles include modules and services.

### Deployment workflow overview

 - The agent connects to the server and waits.
 - The user triggers the agent via the web interface or a REST call.
 - The agent retrieves the stack configuration from the server (role, Git URL for configuration, ...).
 - The agent starts the deployment and posts its status to the server.
 - The agent goes back in waiting mode.

## Usage

### Prerequisites

The following components are required:

 - A place to run the Moustack deployment server.
 - A git repository to host Moustack's deployment profiles.
 - At least one GNU/Linux server to host Openstack services.

*Note*: the following distributions are supported (other may work, as soon as package managers are **apt** or **yum**)

 - Ubuntu 16.04 64bits
 - RHEL7/Centos7

### Installation

TODO: do more detailed instructions than QuickStart

### Building

#### Docker images

We are using [make](https://www.gnu.org/software/make/) to orchestrate moustack's build system. On build, `make` is "touching" files (triggers) to avoid `make` to attempt a new build when running it twice (this may be useless now, as `docker build` better handles layers since 1.9.

Some parameters can be passed to `make` to customize it:

| parameter | description | default value |
| --------- | ----------- | ------------- |
| `DOCKER_TAG` | docker image tag | mandatory |
| `DOCKER_REGISTRY` | docker registry to push image to | `local-registry:5000` |
| `DOCKER_BUILD_ARGS` | parameters to append to `docker build` command, like `--build-arg http_proxy=http://172.17.0.1:18080` | |

Here is a description of main `make` targets:

| target | description |
| ------ | ----------- |
| `distclean` | delete local images (`docker rmi`) and remove `make` triggers |
| `clean`     | remove `make` triggers |
| `build`     | build and tag images as `DOCKER_TAG` |
| `release`   | push images to `DOCKER_REGISTRY` repository (`local-registry:5000` by default) |

Using that, all docker images can be built (and/or pushed) using a single command line from the root of this repository:

```sh
# build all images (re-using already used docker layers)
make clean build DOCKER_TAG=liberty

# delete, build, and push all images (flushing already existing docker layers)
make distclean build release DOCKER_TAG=liberty

# build and push all images to a specific repository (instead of the default `local-registry:5000`)
make clean build release DOCKER_TAG=liberty DOCKER_REGISTRY=registry.foo.bar
```



#### Packer images

To try Moustack without affecting your system, you can use a virtual machine (don't forget to enable `nested` parameter in KVM). You can use QCOW images which are built using [packer](https://www.packer.io/). The following distributions are supported:

 - Ubuntu 16.04
 - Centos 7

Some packer parameters can be overriden at runtime from the command line:

| parameter | default value |
| --------- | ------------- |
| `disk_size` | `"2520"` |
| `iso_url` | `"http://releases.ubuntu.com/16.04/ubuntu-16.04-server-amd64.iso"` |
| `iso_checksum` | `"23e97cd5d4145d4105fbf29878534049"` |
| `iso_checksum_type` | `"md5"` |
| `hostname` | `"moustack-ubuntu-1604"` |
| `domain` | `"cloud.local"` |
| `retry_timeout` | `"1m"` |
| `ssh_username` | `"ubuntu"` |
| `ssh_password` | `"ubuntu"` |
| `output_directory` | `"packer/output"` |
| `http_proxy` | `""` |
| `enable_cloudinit` | `false` |
| `headless` | `"true"` |

To build the Ubuntu 16.04 image, you can run this command from the root of this repository:

```sh
make -C packer build-ubuntu-1604
```

If successfull, the resulting QCOW image will be located at `packer/output/moustack-ubuntu-1604`.

You can customize parameters, for example:

```sh
make -C packer build-ubuntu-1604 PACKER_PARAMS="-var http_proxy=http://192.168.1.1:18080 -var output_directory=/dev/shm/packer"
```

With these parameters, the resulting QCOW image will be located at `/dev/shm/packer/moustack-ubuntu-1604`


## Licensing

Moustack is licensed under the Apache License, Version 2.0. See [LICENSE](https://github.com/adenops/moustack/blob/master/LICENSE) for the full license text.
