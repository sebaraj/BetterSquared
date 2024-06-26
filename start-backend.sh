#!/bin/bash

# This script starts the backend service
# Run from project root directory


minikube start

minikube addons enable ingress

kubectl apply -f ./rabbit/manifests/

kubectl apply -f ./authservice/manifests/

kubectl apply -f ./gatewayservice/manifests/configmap.yaml
kubectl apply -f ./gatewayservice/manifests/gateway-deploy.yaml
kubectl apply -f ./gatewayservice/manifests/secret.yaml
kubectl apply -f ./gatewayservice/manifests/service.yaml

kubectl apply -f ./jwtcache/manifests/
kubectl apply -f ./jwtcache/twemproxy/manifests/

kubectl apply -f ./cleanpods/manifests/

kubectl apply -f ./emailservice/manifests/

kubectl apply -f ./groupservice/manifests/

kubectl apply -f ./betservice/manifests/

kubectl apply -f ./updateservice/manifests/

sudo kubectl apply -f ./gatewayservice/manifests/ingress.yaml

sudo minikube tunnel



