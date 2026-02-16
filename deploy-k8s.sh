#!/bin/bash

# Script to deploy Vercel Clone to Kubernetes using credentials from .env

set -e

echo "=========================================="
echo "Vercel Clone - Kubernetes Deployment"
echo "=========================================="

# Check if .env exists
if [ ! -f .env ]; then
    echo "ERROR: .env file not found!"
    echo "Please create .env by copying .env.example and filling in your credentials"
    exit 1
fi

# Source .env file
source .env

# Check required variables
required_vars=("R2_ENDPOINT" "R2_ACCESS_KEY" "R2_SECRET_KEY" "R2_BUCKET" "POSTGRES_PASSWORD" "JWT_SECRET" "DOCKERHUB_REGISTRY")

for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        echo "ERROR: $var is not set in .env"
        exit 1
    fi
done

echo ""
echo "✓ .env file loaded successfully"
echo ""

# Create namespace
echo "Creating namespace..."
kubectl apply -f k8s/namespace.yaml

# Create ConfigMap
echo "Creating ConfigMap with Docker Hub registry..."
kubectl apply -f k8s/configmap.yaml

# Create R2 Secret from .env variables
echo "Creating R2 credentials secret from .env..."
kubectl delete secret r2-credentials -n vercel-clone 2>/dev/null || true
kubectl create secret generic r2-credentials \
  --from-literal=endpoint="$R2_ENDPOINT" \
  --from-literal=access-key="$R2_ACCESS_KEY" \
  --from-literal=secret-key="$R2_SECRET_KEY" \
  --from-literal=bucket="$R2_BUCKET" \
  -n vercel-clone

# Create Postgres Secret from .env variables
echo "Creating Postgres credentials secret from .env..."
kubectl delete secret postgres-credentials -n vercel-clone 2>/dev/null || true
kubectl create secret generic postgres-credentials \
  --from-literal=username=postgres \
  --from-literal=password="$POSTGRES_PASSWORD" \
  --from-literal=database=vercel_clone \
  -n vercel-clone

# Create JWT Secret
echo "Creating JWT secret from .env..."
kubectl delete secret jwt-secrets -n vercel-clone 2>/dev/null || true
kubectl create secret generic jwt-secrets \
  --from-literal=secret="$JWT_SECRET" \
  --from-literal=expiration=86400000 \
  -n vercel-clone

# Deploy all services
echo ""
echo "Deploying services..."
kubectl apply -f k8s/redis-deployment.yaml
kubectl apply -f k8s/postgres-deployment.yaml
kubectl apply -f k8s/upload-service-deployment.yaml
kubectl apply -f k8s/build-service-deployment.yaml
kubectl apply -f k8s/request-handler-deployment.yaml
kubectl apply -f k8s/hpa.yaml

echo ""
echo "=========================================="
echo "✓ Deployment Complete!"
echo "=========================================="
echo ""
echo "Checking pod status..."
kubectl get pods -n vercel-clone

echo ""
echo "Services:"
kubectl get svc -n vercel-clone

echo ""
echo "To view logs:"
echo "  kubectl logs -n vercel-clone deployment/upload-service"
echo "  kubectl logs -n vercel-clone deployment/build-service"
echo "  kubectl logs -n vercel-clone deployment/request-handler"
echo ""
echo "To access services:"
echo "  kubectl port-forward -n vercel-clone svc/upload-service 8081:80"
echo "  kubectl port-forward -n vercel-clone svc/request-handler 8083:80"
