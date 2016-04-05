FROM local-registry:5000/openstack/ubuntu-base:latest

# install and configure packages
COPY build-image *.patch /tmp/
RUN /tmp/build-image
