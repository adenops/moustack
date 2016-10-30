/**
 * Copyright (C) 2016 Adenops Consultants Informatique Inc.
 *
 * This file is part of the Moustack project, see http://www.moustack.org for
 * more information.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.adenops.moustack.agent.config;

public enum StackProperty {
	CONTAINERS_ROOT, SELINUX_POLICY, MYSQL_ROOT_PASSWORD, KS_CEILOMETER_USER, KS_GLANCE_USER, KS_HEAT_USER,
	KS_NEUTRON_USER, KS_NOVA_USER, KS_CEILOMETER_PASSWORD, KS_GLANCE_PASSWORD, KS_HEAT_PASSWORD, KS_NEUTRON_PASSWORD,
	KS_NOVA_PASSWORD, DB_CINDER_DATABASE, DB_CINDER_USER, DB_CINDER_PASSWORD, DB_GLANCE_DATABASE, DB_GLANCE_USER,
	DB_GLANCE_PASSWORD, DB_HEAT_DATABASE, DB_HEAT_USER, DB_HEAT_PASSWORD, DB_KEYSTONE_DATABASE, DB_KEYSTONE_USER,
	DB_KEYSTONE_PASSWORD, DB_NEUTRON_DATABASE, DB_NEUTRON_USER, DB_NEUTRON_PASSWORD, DB_NOVA_DATABASE, DB_NOVA_USER,
	DB_NOVA_PASSWORD, KEYSTONE_ADMIN_TOKEN, KEYSTONE_ADMIN_PASSWORD, KEYSTONE_ADMIN_USER, SERVICES_PUBLIC_IP, REGION,
	SERVICES_INTERNAL_IP, SERVICES_ADMIN_IP, KS_CINDER_PASSWORD, HEAT_DOMAIN, HEAT_DOMAIN_ADMIN,
	HEAT_DOMAIN_ADMIN_PASSWORD, HEAT_DELEGATED_ROLE, DB_CEILOMETER_DATABASE, DB_CEILOMETER_USER, DB_CEILOMETER_PASSWORD,
	HOSTNAME, ROLE, KEYSTONE_ADMIN_PROJECT, KEYSTONE_SERVICES_PROJECT, KEYSTONE_ADMIN_ROLE, KS_CINDER_USER,
	HEAT_STACK_USER_ROLE, KS_DESIGNATE_USER, KS_DESIGNATE_PASSWORD, DB_DESIGNATE_PASSWORD, DESIGNATE_SERVER_NAME,
	DHCP_DOMAIN, DESIGNATE_DEFAULT_DOMAIN_ID, SYSLOG_HOST, SYSLOG_PORT;

	public String getName() {
		return this.name().toLowerCase();
	}
}
