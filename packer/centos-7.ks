firewall --disabled

install
cdrom

lang en_US.UTF-8
keyboard us
timezone --utc America/Toronto

network --bootproto=dhcp --hostname=moustack-ci-centos-7
authconfig --enableshadow --passalgo=sha512

rootpw centos
user --name=centos --password=centos --plaintext --groups=wheel

selinux --disabled
bootloader --location=mbr
text
skipx

logging --level=info
zerombr

clearpart --all --initlabel
part / --fstype ext4 --size=1 --grow

auth  --useshadow  --enablemd5
firstboot --disabled
reboot

%packages --ignoremissing
@core
@base
%end

%post
# disable TTY requirement with sudo
sed -i "s/^.*requiretty/#Defaults requiretty/" /etc/sudoers

# remove NetworkManager
yum remove -y NetworkManager
%end
