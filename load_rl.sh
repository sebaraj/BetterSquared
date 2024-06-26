#!/bin/bash

# Fetch pod IPs
pod_ips=$(kubectl get pods -o wide -l app=redis1 --no-headers | awk '{print $6}')

# Convert IPs into the redis-cli format
redis_cli_command="redis-cli --cluster create"
first=true
for ip in $pod_ips; do
    if $first; then
        redis_cli_command="$redis_cli_command $ip:6379"
        first=false
    else
        redis_cli_command="$redis_cli_command $ip:6379"
    fi
done

# Add cluster-replicas argument
redis_cli_command="$redis_cli_command --cluster-replicas 1"

# Execute the redis-cli command
kubectl exec -it redis1-0 -- sh -c "$redis_cli_command"
