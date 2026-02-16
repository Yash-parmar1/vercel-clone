# Kubernetes Deployment Guide

## Quick Start

This project can be deployed to Kubernetes using the provided manifests. The deployment scripts automatically read credentials from your `.env` file.

### Prerequisites
- Kubernetes cluster (Minikube, EKS, GKE, AKS, etc.)
- `kubectl` configured to access your cluster
- `.env` file with credentials (see [.env.example](.env.example))

---

## Deployment Steps

### 1. Create `.env` File
```bash
cp .env.example .env
# Edit .env with your actual credentials
```

Fill in:
```env
R2_ENDPOINT=https://YOUR_ACCOUNT_ID.r2.cloudflarestorage.com
R2_ACCESS_KEY=your_actual_key
R2_SECRET_KEY=your_actual_secret
R2_BUCKET=vercel-clone-storage
POSTGRES_PASSWORD=your_db_password
JWT_SECRET=your_secret_key
DOCKERHUB_REGISTRY=your_dockerhub_username
```

### 2. Deploy to Kubernetes

**On Linux/Mac:**
```bash
chmod +x deploy-k8s.sh
./deploy-k8s.sh
```

**On Windows (PowerShell):**
```powershell
.\deploy-k8s.bat
```

That's it! The script will:
✓ Create K8s namespace  
✓ Read credentials from `.env`  
✓ Create secrets automatically  
✓ Deploy all services  
✓ Configure autoscaling  

---

## What Gets Deployed

| Component | Count | Type |
|-----------|-------|------|
| Redis | 1 | Pod |
| PostgreSQL | 1 | Pod |
| Upload Service | 2 | Pods (auto-scales to 10) |
| Build Service | 3 | Pods (auto-scales to 20) |
| Request Handler | 3 | Pods (auto-scales to 15) |
| **Total** | **10** | **Pods** |

---

## Accessing Services

### Port Forward
```bash
# Upload Service
kubectl port-forward -n vercel-clone svc/upload-service 8081:80

# Build Service
kubectl port-forward -n vercel-clone svc/build-service 8082:80

# Request Handler
kubectl port-forward -n vercel-clone svc/request-handler 8083:80
```

### View Logs
```bash
kubectl logs -f -n vercel-clone deployment/upload-service
kubectl logs -f -n vercel-clone deployment/build-service
kubectl logs -f -n vercel-clone deployment/request-handler
```

### Check Pod Status
```bash
kubectl get pods -n vercel-clone
kubectl get svc -n vercel-clone
kubectl get hpa -n vercel-clone
```

---

## For Local Testing (Minikube)

### Install Minikube
```bash
# Windows
choco install minikube

# Mac
brew install minikube

# Linux
curl -Lo minikube https://github.com/kubernetes/minikube/releases/download/latest/minikube-linux-amd64
```

### Start Minikube
```bash
minikube start
```

### Deploy
```bash
./deploy-k8s.sh  # or deploy-k8s.bat on Windows
```

### Access Services
```bash
minikube service -n vercel-clone upload-service
minikube service -n vercel-clone request-handler
```

---

## Cleanup

To delete everything:
```bash
kubectl delete namespace vercel-clone
```

---

## Notes

- **Credentials**: All sensitive data is read from `.env` and stored as K8s secrets
- **Persistence**: PostgreSQL data is persisted using PersistentVolumeClaim (10GB)
- **Scaling**: Horizontal Pod Autoscaler adjusts replicas based on CPU usage (70% threshold)
- **Load Balancing**: Upload and Request Handler services are LoadBalancer type (external access)
- **Internal Services**: Build Service and Redis are ClusterIP type (internal only)

---

## Troubleshooting

### Pods not starting
```bash
kubectl describe pod -n vercel-clone <pod-name>
kubectl logs -n vercel-clone <pod-name>
```

### Secrets not found
```bash
kubectl get secrets -n vercel-clone
```

### Database connection issues
```bash
kubectl exec -it -n vercel-clone postgres-<pod-id> -- psql -U postgres -d vercel_clone
```

---

## Docker Hub Images

Make sure your Docker images are pushed:
```bash
docker push your_dockerhub/upload-service:v1
docker push your_dockerhub/build-service:v1
docker push your_dockerhub/request-handler:v1
```

Update `k8s/configmap.yaml` with your Docker Hub registry.
