#!/bin/bash
set -eu

PARALLEL=true
CACHE=true

BASE_TAG="local-registry:5000/openstack"
DOCKER_BUILD_OPTS="--disable-content-trust=true --pull=true --tag=test --ulimit=nofile=1024:1024"
$CACHE || DOCKER_BUILD_OPTS="$DOCKER_BUILD_OPTS --no-cache=true"
DOCKER_BUILD_OPTS="$DOCKER_BUILD_OPTS --build-arg=http_proxy=http://172.17.0.1:3142"
PARALLEL_OPTS="--jobs 0 --no-notice --bar --halt 2 --verbose"

build() {
	cmd="$1"
	shift
	if $PARALLEL; then
		parallel $PARALLEL_OPTS $cmd ::: $@
	else
		for image in $@; do
			$cmd $image
		done
	fi
}

exit_() {
	ret=$?
	end_time=$(date +%s)
	echo -e "\e[0;33mexecution time: $(($end_time - $start_time))s\e[0m"
	[ "$ret" -eq 0 ] &&
		echo -e "\e[1;32mSUCCESS\e[0m" ||
		echo -e "\e[1;31mFAILURE\e[0m"
}
trap exit_ EXIT

start_time=$(date +%s)

########################
# generated subscripts #
########################

cat > /tmp/stackdeploy-build.sh << EOF
#/bin/bash
set -eu

pushd $PWD/\$1
docker build $DOCKER_BUILD_OPTS -t $BASE_TAG/\$1:latest .
docker push $BASE_TAG/\$1:latest

popd
EOF
chmod +x /tmp/stackdeploy-build.sh

cat > /tmp/stackdeploy-upstream.sh << EOF
#/bin/bash
set -eu

image="\${1%:*}"
tag="\${1#*:}"

docker pull \$image:\$tag
docker tag -f \$image:\$tag $BASE_TAG/\$image:latest
docker push $BASE_TAG/\$image:latest
EOF
chmod +x /tmp/stackdeploy-upstream.sh

##########################
# upstream docker images #
##########################

build /tmp/stackdeploy-upstream.sh \
	mariadb:10.1.11 \
	rabbitmq:3.6.1-management \
	memcached:1.4.25 \
	mongo:3.2.3

###############
# base images #
###############

for image in \
	ubuntu-base \
	openstack-base
do
	/tmp/stackdeploy-build.sh $image
done

#################
# custom images #
#################

build /tmp/stackdeploy-build.sh \
	samba \
	logstash \
	openvswitch \
	keystone \
	glance \
	cinder \
	heat \
	dashboard \
	nova-controller \
	nova-compute \
	neutron-controller \
	neutron-openvswitch
