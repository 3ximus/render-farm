#!/bin/sh

# This script gets all dependencies needed to run both the load balancer and the code instrumenter
# Namely:
# 		- aws-java-sdk-1.11.125
# 		- bit

set -e

# XXX give any argument to force deletion of previous versions
[[ ! -z $1 ]] && rm -r aws-java-sdk* BIT*

if test ! -d aws-java-sdk-* ; then
	echo "Downloading aws-java-sdk.zip..."
	curl "http://sdk-for-java.amazonwebservices.com/latest/aws-java-sdk.zip" -o aws-java-sdk.zip -#
	echo "Extracting aws-java-sdk.zip..."
	unzip -q aws-java-sdk.zip
	rm aws-java-sdk.zip
fi

if [[ ! -d BIT ]] ; then
	echo "Downloading aws-java-sdk.zip..."
	curl "http://grupos.tecnico.ulisboa.pt/~meic-cnv.daemon/labs/labs-bit/BIT.zip" -o BIT.zip -#
	echo "Extracting BIT.zip..."
	unzip -q BIT.zip
	rm -r BIT.zip BIT/docs BIT/examples
	mv BIT/samples BIT/tools
	make bit
fi

