#!/bin/bash

# This script starts the backend service
# Run from project root directory


minikube start

kubectl create secret tls better2-tls --cert=tls.crt --key=tls.key

minikube addons enable ingress

kubectl apply -f ./rabbit/manifests/

kubectl apply -f ./authservice/manifests/

kubectl delete statefulset redis1
kubectl delete pvc -l app=redis1
kubectl delete service redis1
kubectl delete configmap redis1-config
kubectl apply -f ./ratelimiter/manifests/
./load_rl.sh

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

kubectl apply -f ./updateservice/manifests/

sudo kubectl apply -f ./gatewayservice/manifests/ingress.yaml

sudo minikube tunnel



