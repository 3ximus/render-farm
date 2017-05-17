#!/bin/sh

# This script gets all dependencies needed to run both the load balancer and the code instrumenter
# Namely:
# 		- aws-java-sdk-1.*
# 		- bit


# XXX give any argument to force deletion of previous versions
[[ ! -z $1 ]] && rm -r aws-java-sdk* BIT*

set -e

echo -e "\e[31mThis script makes changes to /etc/rc.local ... You have been warned.\e[0m"

if test ! -d aws-java-sdk-* ; then
	echo "Downloading aws-java-sdk.zip..."
	curl "http://sdk-for-java.amazonwebservices.com/latest/aws-java-sdk.zip" -o aws-java-sdk.zip -#
	echo "Updating all references of aws-java-sdk to $(unzip -l aws-java-sdk.zip | awk 'NR==4 {print substr($4,1,length($4)-1)}' | cut -d/ -f1)..."
	# XXX this command is dangerous, dont run this loacally
	sudo sed -i "s/aws-java-sdk-1\.[0-9]\+\.[0-9]\+/$(unzip -l aws-java-sdk.zip | awk 'NR==4 {print substr($4,1,length($4)-1)}')/g" Makefile update_and_compile_on_remote_server.sh raytracer/Makefile web-server/WebServer.java /etc/rc.local 
	echo "Extracting aws-java-sdk.zip..."
	unzip -q aws-java-sdk.zip '*/lib/*' '*/third-party/*'
	rm aws-java-sdk.zip
fi

if [[ ! -d BIT ]] ; then
	echo "Downloading BIT.zip..."
	curl "http://grupos.tecnico.ulisboa.pt/~meic-cnv.daemon/labs/labs-bit/BIT.zip" -o BIT.zip -#
	echo "Extracting BIT.zip..."
	unzip -q BIT.zip '*/BIT/*' '*/java-config.sh'
	rm BIT.zip
fi
echo -e "\e[1;32mSetup Done.\e[0m"

