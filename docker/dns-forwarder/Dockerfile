FROM local-registry:5000/openstack/ubuntu-base:latest

# install and configure packages
COPY build-image /tmp/
RUN /tmp/build-image

# add entrypoint
COPY entrypoint-* /usr/sbin/

# add etc overlay
COPY etc /etc/
