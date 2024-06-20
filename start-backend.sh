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

kubectl apply -f ./emailservice/manifests/

kubectl apply -f ./groupservice/manifests/

kubectl apply -f ./betservice/manifests/

sudo kubectl apply -f ./gatewayservice/manifests/ingress.yaml

sudo minikube tunnel



