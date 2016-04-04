FROM local-registry:5000/openstack/openstack-base:latest

RUN eatmydata apt-get install -y --no-install-recommends apache2 libapache2-mod-wsgi openstack-dashboard

COPY wsgi-dashboard.conf /etc/apache2/sites-available/
RUN a2dissite 000-default ; a2disconf openstack-dashboard ; a2ensite wsgi-dashboard

RUN mkdir -p /etc/service/apache2
COPY apache2.sh /etc/service/apache2/run
