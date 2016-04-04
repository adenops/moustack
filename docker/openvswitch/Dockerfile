FROM local-registry:5000/openstack/openstack-base:latest

RUN eatmydata apt-get install -y --no-install-recommends openvswitch-switch

COPY if-pre-up.d/openvswitch /etc/network/if-pre-up.d/openvswitch
COPY if-pre-up.d/openvswitch /etc/network/if-post-down.d/openvswitch

RUN ovsdb-tool create /etc/openvswitch/conf.db /usr/share/openvswitch/vswitch.ovsschema

RUN mkdir -p /etc/service/ovsdb-server /etc/service/ovs-vswitchd /etc/service/ovs-setup

COPY ovsdb-server.sh /etc/service/ovsdb-server/run
COPY ovs-vswitchd.sh /etc/service/ovs-vswitchd/run
COPY ovs-setup.sh /etc/service/ovs-setup/run
