FROM local-registry:5000/openstack/openstack-base:latest

RUN eatmydata apt-get install -y --no-install-recommends cinder-api cinder-scheduler \
	cinder-volume cifs-utils nfs-common \
	&& rm -f /var/lib/cinder/cinder.sqlite

RUN mkdir -p /etc/service/cinder-api /etc/service/cinder-volume /etc/service/cinder-scheduler

COPY cinder-api.sh /etc/service/cinder-api/run
COPY cinder-scheduler.sh /etc/service/cinder-scheduler/run
COPY cinder-volume.sh /etc/service/cinder-volume/run
