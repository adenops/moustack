FROM local-registry:5000/openstack/openstack-base:latest

RUN eatmydata apt-get install -y --no-install-recommends heat-api heat-api-cfn \
	heat-engine \
	&& rm -f /var/lib/heat/heat.sqlite

RUN mkdir -p /etc/service/heat-api /etc/service/heat-api-cfn /etc/service/heat-engine

COPY heat-api.sh /etc/service/heat-api/run
COPY heat-api-cfn.sh /etc/service/heat-api-cfn/run
COPY heat-engine.sh /etc/service/heat-engine/run
