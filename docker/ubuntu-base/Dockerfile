FROM phusion/baseimage:latest

ENV DEBIAN_FRONTEND=noninteractive TERM=xterm

RUN curl -o /tmp/eatmydata.deb http://archive.ubuntu.com/ubuntu/pool/main/libe/libeatmydata/eatmydata_26-2_amd64.deb \
        && dpkg -i /tmp/eatmydata.deb \
        && rm -f /tmp/eatmydata.deb

RUN echo force-unsafe-io > /etc/dpkg/dpkg.cfg.d/disable-fsync \
	&& sed -i 's/^deb-src /# deb-src /' /etc/apt/sources.list \
        && eatmydata apt-get update \
        && eatmydata apt-get dist-upgrade -y --no-install-recommends \
        && eatmydata apt-get install -y --no-install-recommends patch vim strace tcpdump net-tools

RUN curl -o /usr/local/bin/gosu -SL "https://github.com/tianon/gosu/releases/download/1.7/gosu-amd64" \
	&& chmod +x /usr/local/bin/gosu

COPY cleanup_tmp_folders.sh /etc/my_init.d/01_cleanup_tmp_folders.sh
COPY syslog-forwarder.sh /etc/service/syslog-forwarder/run
COPY custom-syslog.conf /etc/syslog-ng/conf.d/
RUN touch /var/log/syslog-docker

RUN mkdir -p /usr/lib/runit/wait_for
COPY lib/runit/wait_for /usr/lib/runit/wait_for/
COPY lib/runit/common /usr/lib/runit/
