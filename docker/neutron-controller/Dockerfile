FROM local-registry:5000/openstack/openstack-base:latest

RUN eatmydata apt-get install -y --no-install-recommends neutron-server \
	neutron-plugin-ml2 neutron-l3-agent neutron-dhcp-agent neutron-metadata-agent \
	conntrack openvswitch-switch \
	&& rm -f /var/lib/neutron/neutron.sqlite

RUN for i in lock run log lib; do mkdir -p /var/$i/neutron; chown neutron /var/$i/neutron; done

RUN mkdir -p /etc/service/neutron-server /etc/service/neutron-dhcp-agent \
	/etc/service/neutron-l3-agent /etc/service/neutron-metadata-agent

COPY neutron-server.sh /etc/service/neutron-server/run
COPY neutron-dhcp-agent.sh /etc/service/neutron-dhcp-agent/run
COPY neutron-l3-agent.sh /etc/service/neutron-l3-agent/run
COPY neutron-metadata-agent.sh /etc/service/neutron-metadata-agent/run
