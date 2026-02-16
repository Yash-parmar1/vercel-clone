# Deployment Workflow

This document explains the credential management across Docker Compose and Kubernetes deployments.

---

## **Single Source of Truth: `.env` File**

All credentials are stored in **ONE place**: the `.env` file

```
.env (Your Machine - PRIVATE)
  ├── R2_ENDPOINT
  ├── R2_ACCESS_KEY
  ├── R2_SECRET_KEY
  ├── R2_BUCKET
  ├── POSTGRES_PASSWORD
  ├── JWT_SECRET
  └── DOCKERHUB_REGISTRY
```

---

## **Local Development: Docker Compose**

```bash
# 1. Create .env
cp .env.example .env
# Edit .env with your credentials

# 2. Run
docker-compose -f docker-compose-local.yml up

# Docker Compose reads .env and runs all services
```

The `docker-compose-local.yml` file references `.env`:
```yaml
environment:
  R2_ENDPOINT: ${R2_ENDPOINT}          # Read from .env
  POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
  JWT_SECRET: ${JWT_SECRET}
```

---

## **Kubernetes Deployment**

```bash
# 1. Create .env
cp .env.example .env
# Edit .env with your credentials

# 2. Run deployment script
./deploy-k8s.sh  (Linux/Mac)
# OR
.\deploy-k8s.bat  (Windows)

# Script reads .env and creates K8s secrets automatically
```

### **What the Script Does**

```bash
source .env  # Load all environment variables

# Create K8s secrets from .env values
kubectl create secret generic r2-credentials \
  --from-literal=endpoint="$R2_ENDPOINT" \
  --from-literal=access-key="$R2_ACCESS_KEY" \
  ...

# Deploy all manifests
kubectl apply -f k8s/
```

---

## **Benefits of This Approach**

✅ **No Duplication**: Credentials entered once in `.env`  
✅ **No Hardcoding**: All values externalized  
✅ **Safe**: `.env` is in `.gitignore` (never pushed)  
✅ **Flexible**: Works locally (Docker) and in production (K8s)  
✅ **Developer-Friendly**: Just fill in `.env` and run script  

---

## **File Reference**

| File | Purpose | Pushed? | Content |
|------|---------|---------|---------|
| `.env.example` | Template | ✅ YES | Shows what needs to be filled |
| `.env` | Actual credentials | ❌ NO (in .gitignore) | Your real values |
| `docker-compose-local.yml` | Local deployment | ✅ YES | References `.env` |
| `k8s/secrets.yaml` | K8s secret template | ✅ YES | Now ignored - script creates it |
| `deploy-k8s.sh` / `deploy-k8s.bat` | Deployment script | ✅ YES | Reads `.env` and deploys |

---

## **Quick Reference**

### **To run locally:**
```bash
cp .env.example .env
# Edit .env
docker-compose -f docker-compose-local.yml up
```

### **To deploy to K8s:**
```bash
cp .env.example .env
# Edit .env
./deploy-k8s.sh  # Reads .env and deploys
```

### **What developers see in GitHub:**
```
✓ .env.example - template they copy
✓ docker-compose-local.yml - references .env
✓ deploy-k8s.sh/bat - automates K8s deployment
✓ k8s/ - manifests (no hardcoded secrets)
✗ .env - private, in .gitignore
```

---

## **For Other Developers**

1. Clone the repo
2. Copy `.env.example` to `.env`
3. Fill in their own credentials
4. Run either:
   - `docker-compose -f docker-compose-local.yml up` (local dev)
   - `./deploy-k8s.sh` (production K8s)

No manual secret creation needed! 🎯
