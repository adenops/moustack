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

# in case this is restart, ensure ovs-setup also restarts
killall nc 2>/dev/null || true

wait_for syslog

mkdir -p /var/run/openvswitch/

if [ ! -e /etc/openvswitch/conf.db ]; then
	echo "creating Open vSwitch database"
	ovsdb-tool create /etc/openvswitch/conf.db /usr/share/openvswitch/vswitch.ovsschema
fi

exec ovsdb-server /etc/openvswitch/conf.db -vconsole:info \
	--remote=punix:/var/run/openvswitch/db.sock \
	--private-key=db:Open_vSwitch,SSL,private_key \
	--certificate=db:Open_vSwitch,SSL,certificate \
	--bootstrap-ca-cert=db:Open_vSwitch,SSL,ca_cert \
	--no-chdir \
	--pidfile=/var/run/openvswitch/ovsdb-server.pid
