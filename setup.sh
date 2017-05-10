#!/bin/sh

# This script gets all dependencies needed to run both the load balancer and the code instrumenter
# Namely:
# 		- aws-java-sdk-1.*
# 		- bit

set -e

# XXX give any argument to force deletion of previous versions
[[ ! -z $1 ]] && rm -r aws-java-sdk* BIT*

if test ! -d aws-java-sdk-* ; then
	echo "Downloading aws-java-sdk.zip..."
	curl "http://sdk-for-java.amazonwebservices.com/latest/aws-java-sdk.zip" -o aws-java-sdk.zip -#
	echo "Updating Makefile version to $(unzip -l aws-java-sdk.zip | awk 'NR==4 {print substr($4,1,length($4)-1)}' | cut -d/ -f1)..."
	sed -i "s/aws-java-sdk-1\.[0-9]\+\.[0-9]\+/$(unzip -l aws-java-sdk.zip | awk 'NR==4 {print substr($4,1,length($4)-1)}')/g" Makefile update_and_compile_on_remote_server.sh
	echo "Extracting aws-java-sdk.zip..."
	unzip -q aws-java-sdk.zip
	rm aws-java-sdk.zip
fi

if [[ ! -d BIT ]] ; then
	echo "Downloading BIT.zip..."
	curl "http://grupos.tecnico.ulisboa.pt/~meic-cnv.daemon/labs/labs-bit/BIT.zip" -o BIT.zip -#
	echo "Extracting BIT.zip..."
	unzip -q BIT.zip
	rm -r BIT.zip BIT/{docs,examples,samples}
	echo "Compiling BIT files..."
	make bit
fi
echo -e "\e[1;32mSetup Done\e[0m"

