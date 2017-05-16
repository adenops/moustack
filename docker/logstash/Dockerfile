FROM moustack/ubuntu-base:ocata

# install and configure packages
COPY build-image /tmp/
RUN /tmp/build-image

# add etc overlay
COPY etc /etc/
