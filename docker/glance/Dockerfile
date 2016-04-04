FROM local-registry:5000/openstack/openstack-base:latest

RUN eatmydata apt-get install -y --no-install-recommends glance python-swiftclient \
	&& rm -f /var/lib/glance/glance.sqlite

RUN mkdir -p /etc/service/glance-api /etc/service/glance-registry

COPY glance-api.sh /etc/service/glance-api/run
COPY glance-registry.sh /etc/service/glance-registry/run
