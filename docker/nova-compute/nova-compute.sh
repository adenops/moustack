#!/bin/sh

# Copyright (C) 2016 Adenops Consultants Informatique Inc.
#
# This file is part of the Moustack project, see http://www.moustack.org for
# more information.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
set -e

. /usr/lib/runit/common

# TODO: check how to fix properly
if [ -c /dev/kvm ]; then
	chmod 777 /dev/kvm
fi

# ensure instances folder is created (nova may fail otherwise)
service_mkdir nova /var/lib/nova/instances

wait_for syslog
wait_for openvswitch
wait_for rabbitmq
wait_for neutron-server
wait_for libvirt

start_daemon nova /usr/bin/nova-compute --config-file=/etc/nova/nova.conf
