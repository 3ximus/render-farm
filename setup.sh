#!/bin/sh

#
# This script gets all dependencies needed to run both the load balancer and the code instrumenter
# Namely:
# 		- aws-java-sdk-1.11.125
# 		- bit

if [[ -z "$1" || "$1" == "aws" ]]; then
	wget "http://sdk-for-java.amazonwebservices.com/latest/aws-java-sdk.zip" && \
		echo "Extracting aws-java-sdk.zip..." && \
		unzip -q aws-java-sdk.zip && \
		rm aws-java-sdk.zip
elif [[ "$1" == "bit" ]]; then
	wget "http://grupos.tecnico.ulisboa.pt/~meic-cnv.daemon/labs/labs-bit/BIT.zip" && \
		echo "Extracting BIT.zip..." && \
		unzip -q BIT.zip && \
		rm -r BIT.zip BIT/docs BIT/examples && \
		mv BIT/samples BIT/tools
fi


