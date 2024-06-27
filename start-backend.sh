#!/bin/bash

# This script starts the backend service
# Run from project root directory

minikube delete

minikube start

kubectl create secret tls better2-tls --cert=tls.crt --key=tls.key

minikube addons enable ingress

kubectl apply -f ./rabbit/manifests/

kubectl apply -f ./authservice/manifests/

kubectl delete statefulset rate-limiter

kubectl delete pvc -l app=rate-limiter

kubectl delete service rate-limiter

kubectl delete configmap rate-limiter-config

kubectl apply -f ./ratelimiter/manifests/

sleep 5

# Loop until pod IPs are successfully fetched with exactly 6 IPs
while true; do
    # Fetch pod IPs
    pod_ips=$(kubectl get pods -o wide -l app=rate-limiter --no-headers | awk '{print $6}')

    # Count the number of IPs
    ip_count=$(echo "$pod_ips" | wc -w)

    # Check if the count of IPs is exactly 6
    if [ "$ip_count" -eq 6 ]; then
        echo "Successfully fetched 6 pod IPs"
        break
    else
        echo "Fetched $ip_count IPs, retrying in 5 seconds..."

        sleep 5

    fi
done

for ip in $pod_ips; do
    echo $ip
done

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
kubectl exec -it rate-limiter-0 -- sh -c "$redis_cli_command"

kubectl apply -f ./jwtcache/manifests/

kubectl apply -f ./jwtcache/twemproxy/manifests/

kubectl apply -f ./gatewayservice/manifests/configmap.yaml

kubectl apply -f ./gatewayservice/manifests/gateway-deploy.yaml

kubectl apply -f ./gatewayservice/manifests/secret.yaml

kubectl apply -f ./gatewayservice/manifests/service.yaml

kubectl apply -f ./cleanpods/manifests/

kubectl apply -f ./emailservice/manifests/

kubectl apply -f ./groupservice/manifests/

kubectl apply -f ./betservice/manifests/

kubectl delete job schedule-game-start-job

kubectl apply -f ./updateservice/manifests/

sudo kubectl apply -f ./gatewayservice/manifests/ingress.yaml

sudo minikube tunnel



