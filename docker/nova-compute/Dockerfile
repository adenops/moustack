FROM local-registry:5000/openstack/openstack-base:latest

# TODO: split this container in two, to separate libvirt and prevent
# unecessary restarts
# this should be possible with Docker 1.10 and mount propagation option
# cf: https://github.com/docker/docker/issues/14630

RUN eatmydata apt-get install -y --no-install-recommends nova-compute sysfsutils \
	cifs-utils nfs-common openvswitch-switch dmidecode pm-utils qemu-kvm \
	&& rm -f /var/lib/nova/nova.sqlite \
	&& rm -f /etc/libvirt/qemu/networks/autostart/default.xml

RUN mkdir -p /etc/service/nova-compute /etc/service/libvirtd

COPY nova-compute.sh /etc/service/nova-compute/run
COPY libvirtd.sh /etc/service/libvirtd/run
