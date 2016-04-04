FROM local-registry:5000/openstack/ubuntu-base:latest

RUN eatmydata apt-get install -y --no-install-recommends samba

COPY smb.conf /etc/samba/
RUN mkdir /cinder

RUN mkdir -p /etc/service/samba
COPY samba.sh /etc/service/samba/run
