FROM moustack/ubuntu-base:liberty

# install and configure packages
COPY build-image /tmp/
RUN /tmp/build-image

# add entrypoint
COPY entrypoint-* /usr/sbin/

# add etc overlay
COPY etc /etc/
