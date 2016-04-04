FROM magneticio/vamp-router:0.7.11

RUN apt-get update && eatmydata apt-get install -y --no-install-recommends curl
RUN curl --insecure --location -o /usr/local/bin/dumb-init https://github.com/Yelp/dumb-init/releases/download/v1.0.0/dumb-init_1.0.0_amd64 \
	&& chmod +x /usr/local/bin/dumb-init

ENTRYPOINT ["/usr/local/bin/dumb-init", "/opt/docker/vamp-router"]
