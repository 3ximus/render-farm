#!/bin/sh

#XXX save git credentials on remote server's cache with
# `git config credential.helper store` and run git pull once
#XXX To be easier this does not update files not tracked in git
#XXX ~/render-farm must be the repo

#XXX change here if needed or give PUB_KEY_FILE as 1st argument and host as 2nd to this script
PUB_KEY_FILE="CNV-sigma.pem" #
HOST="52.89.150.173"

# -----------------------------

echo -e "\e[1;34m>>>\e[0m Running with PUB_KEY_FILE \"${1:-$PUB_KEY_FILE}\" and HOST \"${2:-$HOST}\""
# pulls and copies
ssh -i ${1:-$PUB_KEY_FILE} ec2-user@${2:-$HOST} 'cd render-farm && git stash && git stash clear && git pull'

if [ $(git status | grep modified | wc -l) -ge "1" ] ; then
	echo -e "\e[1;34m>>>\e[0m Copying modified files..."
	scp -i ${1:-$PUB_KEY_FILE} -r $(git status | grep modified | awk -F':' '{print $2}') ec2-user@${2:-$HOST}:~/render-farm
fi

echo -e "\e[1;34m>>>\e[0m Running make..."
ssh -i ${1:-$PUB_KEY_FILE} ec2-user@${2:-$HOST} 'cd render-farm && make'

if [ ! -z "$3" ] ; then # add a third argument to also launch the load balancer
	echo -e "\e[1;34m>>>\e[0m Launching Load Balancer..."
	ssh -i ${1:-$PUB_KEY_FILE} ec2-user@${2:-$HOST} 'sudo java8 -classpath /home/ec2-user/render-farm/aws-java-sdk-1.11.115/lib/aws-java-sdk-1.11.115.jar:/home/ec2-user/render-farm/aws-java-sdk-1.11.115/third-party/lib/*:/home/ec2-user/render-farm LoadBalancer'
fi

