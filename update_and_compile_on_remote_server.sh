#!/bin/sh

#XXX save git credentials on remote server's cache with
# `git config credential.helper store` and run git pull once
#XXX To be easier this does not update files not tracked in git
#XXX ~/render-farm must be the repo

#XXX change here if needed or give HOST and PUB_KEY_FILE as 1st and 2nd argument to script
HOST="52.89.150.173"
PUB_KEY_FILE="CNV-sigma.pem" #

HST=${1:-$HOST}
PKF=${2:-$PUB_KEY_FILE}

# -----------------------------
# exit if any command fails
set -e

echo -e "\e[1;34m>>>\e[0m Running with PUB_KEY_FILE \"$PKF\" and HOST \"$HST\""
# pulls and copies
ssh -i $PKF ec2-user@$HST 'cd render-farm && git stash && git stash clear && git pull'

if [ $(git status | grep modified | wc -l) -ge "1" ] ; then
	echo -e "\e[1;34m>>>\e[0m Copying modified files..."
	scp -i $PKF -r $(git status | grep modified | awk -F':' '{print $2}' | cut -d/ -f1) ec2-user@$HST:~/render-farm/
fi

# checks if aws-java-sdk is available. ignore erros on this command due to test returning error code
echo -e "\e[1;34m>>>\e[0m Checking dependencies..."
ssh -i $PKF ec2-user@$HST 'cd render-farm && ./setup.sh'

echo -e "\e[1;34m>>>\e[0m Running make..."
ssh -i $PKF ec2-user@$HST 'cd render-farm && make'

echo -e "\e[1;32mSucessfully Compiled\e"

if [ ! -z "$3" ] ; then # add a third argument to also launch the load balancer
	echo -e "\e[1;34m>>>\e[0m Launching Load Balancer..."
	CLASSPATH=/home/ec2-user/render-farm/amazon:/home/ec2-user/render-farm/instrument-tools:/home/ec2-user/render-farm/BIT:/home/ec2-user/render-farm/aws-java-sdk-1.11.127/lib/aws-java-sdk-1.11.127.jar:/home/ec2-user/render-farm/aws-java-sdk-1.11.127/third-party/lib/*:.
	ssh -i $PKF ec2-user@$HST "sudo java8 -classpath $CLASSPATH LoadBalancer"
fi

