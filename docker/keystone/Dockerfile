FROM local-registry:5000/openstack/openstack-base:latest

RUN eatmydata apt-get install -y --no-install-recommends keystone apache2 \
	libapache2-mod-wsgi memcached python-memcache \
	&& rm -f /var/lib/keystone/keystone.db

COPY wsgi-keystone.conf /etc/apache2/sites-available/
COPY ports.conf /etc/apache2/
RUN a2dissite 000-default ; a2ensite wsgi-keystone

COPY keystone-dbcleanup /etc/cron.daily/

RUN mkdir -p /etc/service/apache2
COPY apache2.sh /etc/service/apache2/run
