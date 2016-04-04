FROM local-registry:5000/openstack/openstack-base:latest

RUN eatmydata apt-get install -y --no-install-recommends neutron-plugin-openvswitch-agent conntrack

RUN mkdir -p /etc/service/neutron-openvswitch-agent

COPY neutron-openvswitch-agent.sh /etc/service/neutron-openvswitch-agent/run

#COPY *.patch /patches/

# bugfix
# seems to not be needed now
#RUN patch -p1 -d / < /patches/0001-fix-segmentation_id-bug.patch

# performance fixes (have to be properly evaluated)
#RUN patch -p1 -d / < /patches/0003-consume-ovs-monitor-events.patch \
#	&& patch -p1 -d / < /patches/0002-reduce-close-syscalls.patch \
#	&& patch -p1 -d / < /patches/0004-reduce-proc-lookups.patch
