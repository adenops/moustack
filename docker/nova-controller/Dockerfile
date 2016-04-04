FROM local-registry:5000/openstack/openstack-base:latest

RUN eatmydata apt-get install -y --no-install-recommends nova-api nova-cert \
	nova-conductor nova-consoleauth nova-novncproxy nova-scheduler \
	&& rm -f /var/lib/nova/nova.sqlite

COPY nova-dbcleanup /etc/cron.daily/

RUN mkdir -p /etc/service/nova-api /etc/service/nova-cert /etc/service/nova-conductor \
	/etc/service/nova-consoleauth /etc/service/nova-novncproxy /etc/service/nova-scheduler
COPY nova-api.sh /etc/service/nova-api/run
COPY nova-cert.sh /etc/service/nova-cert/run
COPY nova-conductor.sh /etc/service/nova-conductor/run
COPY nova-consoleauth.sh /etc/service/nova-consoleauth/run
COPY nova-novncproxy.sh /etc/service/nova-novncproxy/run
COPY nova-scheduler.sh /etc/service/nova-scheduler/run
