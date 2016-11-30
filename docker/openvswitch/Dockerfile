FROM moustack/openstack-base:liberty

# install and configure packages
COPY build-image *.patch /tmp/
RUN /tmp/build-image

# add etc overlay
COPY etc /etc/
