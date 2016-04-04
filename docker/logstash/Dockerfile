FROM local-registry:5000/openstack/ubuntu-base:latest

RUN http_proxy="" apt-key adv --keyserver ha.pool.sks-keyservers.net --recv-keys 46095ACC8548582C1A2699A9D27D666CD88E42B4 \
	&& echo "deb http://packages.elastic.co/logstash/2.1/debian stable main" > /etc/apt/sources.list.d/logstash.list \
	&& eatmydata apt-get update \
	&& eatmydata apt-get install -y --no-install-recommends \
		openjdk-7-jre-headless logstash apache2 libapache2-mod-php5 git-core \
	&& rm -f /var/www/html/index.html \
	&& curl -L https://github.com/potsky/PimpMyLog/archive/v1.7.10.tar.gz | tar xvz --strip-components=1 -C /var/www/html/ \
	&& eatmydata apt-get clean

COPY pimpmylog.conf /etc/apache2/sites-available/
COPY ports.conf /etc/apache2/
RUN a2dissite 000-default ; a2ensite pimpmylog

RUN mkdir -p /etc/service/logstash /etc/service/apache2

COPY logstash.sh /etc/service/logstash/run
COPY apache2.sh /etc/service/apache2/run
