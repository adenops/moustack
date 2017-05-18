FROM moustack/openstack-base:ocata

# install and configure packages
COPY build-image /tmp/
RUN /tmp/build-image

# add etc overlay
COPY etc /etc/

# add bootstrap script
COPY bootstrap-image /usr/bin/