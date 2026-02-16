# Vercel Clone - Complete Setup & Execution Guide

A microservices-based Vercel clone built with Spring Boot, deployed on Docker or Kubernetes.

---

## 📋 Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Prerequisites](#prerequisites)
3. [Quick Start (Docker Compose)](#quick-start-docker-compose)
4. [Production Deployment (Kubernetes)](#production-deployment-kubernetes)
5. [Project Structure](#project-structure)
6. [Testing the API](#testing-the-api)
7. [Troubleshooting](#troubleshooting)

---

## 🏗️ Architecture Overview

```
Users
  ↓
Upload Service (8081)      → Project Upload & Deployment Trigger
  ↓
Build Service (8082)       → Compile & Build Applications
  ↓
Request Handler (8083)     → Route Custom Domains & Serve Apps
  ↓
(Shared Infrastructure)
  ├── PostgreSQL           → Project/User/Deployment Data
  ├── Redis                → Deployment Queue
  └── Cloudflare R2        → Build Artifacts Storage
```

---

## 📦 Prerequisites

### Required
- **Java 21+** (for local development)
- **Maven 3.8+** (for building)
- **Docker & Docker Compose** (for local testing)

### For Kubernetes Deployment
- **Kubernetes Cluster** (Minikube, EKS, GKE, AKS)
- **kubectl CLI** configured
- **Docker Hub Account** (for pushing images)

### Credentials Needed
- **Cloudflare R2**: Endpoint, Access Key, Secret Key, Bucket
- **PostgreSQL Password**: For database access
- **JWT Secret**: For token generation (min 32 characters)
- **Docker Hub Username**: For image registry

---

## 🚀 Quick Start (Docker Compose)

### Step 1: Clone & Setup Environment

```bash
# Clone repository
git clone https://github.com/YOUR_USERNAME/vercel-clone-main.git
cd vercel-clone-main

# Create .env from template
cp .env.example .env
```

### Step 2: Fill in Credentials

Edit `.env` with your actual values:

```bash
# .env
R2_ENDPOINT=https://YOUR_ACCOUNT_ID.r2.cloudflarestorage.com
R2_ACCESS_KEY=your_actual_key
R2_SECRET_KEY=your_actual_secret
R2_BUCKET=vercel-clone-storage

POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_secure_password
POSTGRES_DB=vercel_clone

JWT_SECRET=your_very_long_secret_key_at_least_32_characters
JWT_EXPIRATION=86400000

DOCKERHUB_REGISTRY=your_dockerhub_username
```

### Step 3: Build the Project

```bash
# Compile all modules
mvn clean package

# This creates JAR files in:
# - upload-service/target/upload-service-*.jar
# - build-service/target/build-service-*.jar
# - request-handler/target/request-handler-*.jar
```

### Step 4: Build Docker Images (Optional - docker-compose can do this)

If you want to build images manually:

```bash
# Build each image
docker build -t upload-service:v1 ./upload-service
docker build -t build-service:v1 ./build-service
docker build -t request-handler:v1 ./request-handler
```

### Step 5: Start Everything with Docker Compose

```bash
# Start all services
docker-compose -f docker-compose-local.yml up

# Services start on:
# - Upload Service: http://localhost:8081
# - Build Service: http://localhost:8082 (internal)
# - Request Handler: http://localhost:8083
# - PostgreSQL: localhost:5432
# - Redis: localhost:6379
```

✅ **Done!** All 5 containers running (Redis, Postgres, Upload, Build, Request Handler)

**To stop:**
```bash
docker-compose -f docker-compose-local.yml down
```

---

## ☸️ Production Deployment (Kubernetes)

### Step 1: Build & Push Docker Images to Registry

```bash
# Build images
docker build -t your_dockerhub/upload-service:v1 ./upload-service
docker build -t your_dockerhub/build-service:v1 ./build-service
docker build -t your_dockerhub/request-handler:v1 ./request-handler

# Login to Docker Hub
docker login

# Push images
docker push your_dockerhub/upload-service:v1
docker push your_dockerhub/build-service:v1
docker push your_dockerhub/request-handler:v1
```

### Step 2: Create `.env` File

```bash
cp .env.example .env
# Fill in your actual credentials (same as Docker Compose)
```

### Step 3: Update K8s Configuration

Edit `k8s/configmap.yaml` and change placeholder:
```yaml
data:
  dockerhub-registry: "your_actual_dockerhub_username"
```

### Step 4: Deploy to Kubernetes

**On Linux/Mac:**
```bash
chmod +x deploy-k8s.sh
./deploy-k8s.sh
```

**On Windows:**
```powershell
.\deploy-k8s.bat
```

The script will:
1. ✓ Read credentials from `.env`
2. ✓ Create K8s namespace
3. ✓ Create secrets (R2, Postgres, JWT)
4. ✓ Deploy all services
5. ✓ Configure autoscaling

### Step 5: Verify Deployment

```bash
# Check pods
kubectl get pods -n vercel-clone

# Check services
kubectl get svc -n vercel-clone

# View logs
kubectl logs -n vercel-clone deployment/upload-service
```

### Step 6: Access Services

```bash
# Port forward to local machine
kubectl port-forward -n vercel-clone svc/upload-service 8081:80
kubectl port-forward -n vercel-clone svc/request-handler 8083:80

# Now accessible at:
# http://localhost:8081 (Upload Service)
# http://localhost:8083 (Request Handler)
```

---

## 📁 Project Structure

```
vercel-clone-main/
├── common-lib/                          # Shared code (entities, repos, security)
│   ├── src/main/java/org/parent/
│   │   ├── entity/                      # JPA Entities (User, Project, Deployment)
│   │   ├── repository/                  # Database repositories
│   │   ├── service/                     # Business logic
│   │   └── security/                    # JWT & Auth
│   └── pom.xml
│
├── upload-service/                      # Handles project uploads
│   ├── src/main/java/org/parent/
│   │   └── controller/DeployController.java
│   ├── Dockerfile
│   └── pom.xml
│
├── build-service/                       # Handles builds
│   ├── src/main/java/org/parent/
│   │   └── worker/BuildWorker.java
│   ├── Dockerfile
│   └── pom.xml
│
├── request-handler/                     # Routes requests
│   ├── src/main/java/org/parent/
│   │   └── handler/RequestHandler.java
│   ├── Dockerfile
│   └── pom.xml
│
├── k8s/                                 # Kubernetes manifests
│   ├── namespace.yaml
│   ├── secrets.yaml (template)
│   ├── configmap.yaml
│   ├── redis-deployment.yaml
│   ├── postgres-deployment.yaml
│   ├── upload-service-deployment.yaml
│   ├── build-service-deployment.yaml
│   ├── request-handler-deployment.yaml
│   ├── hpa.yaml (autoscaling)
│   └── README.md
│
├── .env.example                         # Template for local config
├── .env                                 # PRIVATE - your actual credentials
├── .gitignore                           # Excludes .env from git
├── docker-compose-local.yml             # Local development stack
├── deploy-k8s.sh / deploy-k8s.bat       # K8s deployment script
├── DEPLOYMENT_WORKFLOW.md               # Credential management guide
├── pom.xml                              # Parent Maven config
└── README.md                            # This file
```

---

## 🧪 Testing the API

### 1. Sign Up (Create User Account)

```bash
curl -X POST http://localhost:8081/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123"
  }'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "test@example.com",
  "username": "testuser"
}
```

### 2. Create Project

```bash
curl -X POST http://localhost:8081/api/projects \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "name": "My React App",
    "description": "A test React application",
    "repositoryUrl": "https://github.com/user/repo.git",
    "framework": "react",
    "isPublic": true
  }'
```

**Response:**
```json
{
  "id": "project-uuid-here",
  "name": "My React App",
  "owner": "testuser",
  "deployments": []
}
```

### 3. Deploy Project

```bash
curl -X POST http://localhost:8081/api/deploy \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "projectId": "project-uuid-from-step-2",
    "repoUrl": "https://github.com/user/repo.git"
  }'
```

**Response:**
```json
{
  "deploymentId": "deploy-12345-xyz",
  "status": "QUEUED",
  "deploymentUrl": "https://deploy-12345-xyz.vercel-clone.com"
}
```

### 4. Check Deployment Status

```bash
curl http://localhost:8083/api/deployments/deploy-12345-xyz \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

---

## 🐛 Troubleshooting

### "Port already in use"
```bash
# Find process using port
lsof -i :8081  # macOS/Linux
netstat -ano | findstr :8081  # Windows

# Kill process and retry
```

### "Connection refused to PostgreSQL"
```bash
# Ensure containers are running
docker ps

# Check Postgres logs
docker logs vercel-clone-main-postgres-1

# Verify connection
docker exec -it vercel-clone-main-postgres-1 psql -U postgres -d vercel_clone
```

### "Database schema not created"
```bash
# Manually trigger schema creation
docker-compose -f docker-compose-local.yml restart
# Wait 30 seconds for JPA to auto-create tables
```

### "No such file or directory: .env"
```bash
# Create .env from template
cp .env.example .env
```

### "Invalid JWT token"
```bash
# Token might be expired or malformed
# Make sure JWT_SECRET in .env matches what's in database
# Get a new token by signing in again
```

### Kubernetes image pull errors
```bash
# Make sure images are pushed to Docker Hub
docker push your_dockerhub/upload-service:v1

# Check secret credentials
kubectl get secrets -n vercel-clone
```

---

## 📊 Execution Flow Summary

```
┌─────────────────────────────────────────────────────┐
│           LOCAL DEVELOPMENT (Docker)                │
├─────────────────────────────────────────────────────┤
│ 1. mvn clean package                                 │
│ 2. docker-compose -f docker-compose-local.yml up    │
│ 3. All 5 containers start                           │
│ ✅ RUNNING                                           │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│        PRODUCTION (Kubernetes)                      │
├─────────────────────────────────────────────────────┤
│ 1. mvn clean package                                 │
│ 2. docker build & docker push (to Docker Hub)       │
│ 3. Fill .env with credentials                       │
│ 4. ./deploy-k8s.sh or deploy-k8s.bat               │
│ 5. 10 pods deploy across cluster                    │
│ ✅ RUNNING & AUTO-SCALING                          │
└─────────────────────────────────────────────────────┘
```

---

## 🔄 Full Execution Checklist

**Development Setup:**
- [ ] Clone repository
- [ ] Java 21+ installed
- [ ] Maven 3.8+ installed
- [ ] Docker & Docker Compose installed
- [ ] Copy `.env.example` to `.env`
- [ ] Fill `.env` with credentials

**Run Locally:**
- [ ] `mvn clean package`
- [ ] `docker-compose -f docker-compose-local.yml up`
- [ ] Test endpoints with curl/Postman

**Deploy to K8s:**
- [ ] `mvn clean package`
- [ ] Build Docker images
- [ ] Push to Docker Hub
- [ ] Update `k8s/configmap.yaml` with registry
- [ ] `./deploy-k8s.sh` (or deploy-k8s.bat)
- [ ] Verify with `kubectl get pods -n vercel-clone`

---

## 📖 Additional Resources

- [Kubernetes Guide](./k8s/README.md) - Detailed K8s deployment instructions
- [Deployment Workflow](./DEPLOYMENT_WORKFLOW.md) - Credential management guide
- [.env.example](.env.example) - Environment variables template

---

## 📝 License

This project is open source. Feel free to use it for learning and development.

---

**Questions?** Check the troubleshooting section or review the logs:
```bash
# Docker
docker-compose -f docker-compose-local.yml logs -f

# Kubernetes
kubectl logs -n vercel-clone -f deployment/upload-service
```

Happy deploying! 🚀
