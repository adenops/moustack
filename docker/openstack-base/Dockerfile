FROM local-registry:5000/openstack/ubuntu-base:latest

RUN echo 'deb http://ubuntu-cloud.archive.canonical.com/ubuntu trusty-updates/liberty main' > /etc/apt/sources.list.d/cloudarchive-liberty.list \
	&& apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 5EDB1B62EC4926EA \
	&& eatmydata apt-get update \
	&& eatmydata apt-get install -y --no-install-recommends python-oslo.db \
		python-oslo.messaging python-eventlet \
	&& eatmydata apt-get clean

#RUN mkdir /patches
#COPY 0001-reduce-eventlet-cpu-usage.patch /patches/
#RUN patch -p1 -d /usr/lib/python2.7/dist-packages/ < /patches/0001-reduce-eventlet-cpu-usage.patch
