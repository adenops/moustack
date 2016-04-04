FROM local-registry:5000/openstack/openstack-base:latest

RUN curl -o td-agent.deb http://packages.treasuredata.com.s3.amazonaws.com/2/ubuntu/trusty/pool/contrib/t/td-agent/td-agent_2.3.0-0_amd64.deb \
	&& eatmydata dpkg -i td-agent.deb \
	&& rm -f td-agent.deb

RUN eatmydata apt-get install -y --no-install-recommends ruby-dev make \
	&& td-agent-gem install fluent-plugin-forest \
	&& apt-get remove -y ruby-dev make
COPY fluent.conf /etc/td-agent/td-agent.conf

RUN eatmydata apt-get install -y --no-install-recommends npm nodejs nodejs-legacy make g++
RUN npm install -g log.io
RUN mkdir -p /home/td-agent/.log.io/
COPY web_server.conf /home/td-agent/.log.io/
COPY log_server.conf /home/td-agent/.log.io/
RUN curl -o /etc/td-agent/plugin/out_logio.rb https://raw.githubusercontent.com/black-roland/fluent-plugin-logio/master/lib/fluent/plugin/out_logio.rb

RUN eatmydata apt-get install -y --no-install-recommends apache2 libapache2-mod-php5 git-core
RUN rm -f /var/www/html/index.html
RUN curl -L https://github.com/potsky/PimpMyLog/archive/v1.7.10.tar.gz | tar xvz --strip-components=1 -C /var/www/html/
COPY config.user.php /var/www/html/

RUN mkdir -p /etc/service/fluentd /etc/service/logio /etc/service/apache2

COPY fluentd.sh /etc/service/fluentd/run
COPY logio.sh /etc/service/logio/run
COPY apache2.sh /etc/service/apache2/run
