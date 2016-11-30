FROM moustack/ubuntu-base:liberty

# install and configure packages
COPY build-image /tmp/
RUN /tmp/build-image

# add etc overlay
COPY etc /etc/
