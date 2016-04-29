FROM local-registry:5000/openstack/openstack-base:latest

# install and configure packages
COPY build-image /tmp/
RUN /tmp/build-image

# add etc overlay
COPY etc /etc/
