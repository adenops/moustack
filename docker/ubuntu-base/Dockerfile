FROM ubuntu:xenial-20170510

# install and configure packages
COPY build-image bashrc /tmp/
RUN /tmp/build-image

# add runit libraries
COPY lib /usr/lib/

# add etc overlay
COPY etc /etc/

# add entrypoints
COPY entrypoint* /usr/sbin/

ENTRYPOINT ["/usr/sbin/entrypoint"]
