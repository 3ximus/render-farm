#!/bin/sh

#XXX save git credentials on remote server's cache with
# `git config credential.helper store` and run git pull once
#XXX To be easier this does not update files not tracked in git
#XXX ~/render-farm must be the repo

#XXX change here if needed or give PUB_KEY_FILE as 1st argument and host as 2nd to this script
PUB_KEY_FILE="CNV-sigma.pem" #
HOST="52.89.150.173"

# -----------------------------

echo "running with PUB_KEY_FILE \"${1:-$PUB_KEY_FILE}\" and HOST \"${2:-$HOST}\""
# only pulls and makes
[ ! $(git status | grep modified | wc -l) -ge "1" ] && ssh -i ${1:-$PUB_KEY_FILE} ec2-user@${2:-$HOST} 'cd render-farm && git stash && git stash clear && git pull && make' && exit 0

# pulls, copies and makes
ssh -i ${1:-$PUB_KEY_FILE} ec2-user@${2:-$HOST} 'cd render-farm && git stash && git stash clear && git pull'
echo "Copying modified files:"
scp -i ${1:-$PUB_KEY_FILE} -r $(git status | grep modified | awk -F':' '{print $2}') ec2-user@${2:-$HOST}:~/render-farm
ssh -i ${1:-$PUB_KEY_FILE} ec2-user@${2:-$HOST} 'cd render-farm && make'

