# 🚀 Vercel Clone Architecture

A fully functional, microservice-based clone of Vercel for deploying React and Node.js applications. It takes a GitHub repository URL, dynamically provisions isolated Docker containers to build the code, and serves the static assets via a custom Kubernetes-routed subdomain.

## 🏗️ Architecture Overview

The system is broken down into three core Java Spring Boot microservices, orchestrated by Kubernetes, with a React frontend.

1. **Upload Service (`upload-service`)**: 
   - Accepts GitHub repository URLs via a React dashboard.
   - Uses `JGit` with `ReentrantLocks` to safely clone or pull repositories concurrently.
   - Pushes raw source code to **Cloudflare R2** and enqueues a build job in **Redis**.

2. **Build Service (`build-service`)**:
   - A background worker pulling from the Redis queue.
   - Uses **Docker-in-Docker (DinD)** by mounting `/var/run/docker.sock` to dynamically spin up ephemeral, isolated Node.js containers.
   - Runs `npm ci` and `npm run build`, then pushes the compiled production assets back to R2.

3. **Request Handler (`request-handler`)**:
   - Acts as a dynamic reverse proxy.
   - Intercepts wildcard DNS requests (e.g., `http://[id].vercel-clone.com`).
   - Streams the requested static assets (HTML/CSS/JS) directly from Cloudflare R2 to the browser with the correct MIME types.

---

## 🛠️ Tech Stack & Infrastructure

- **Backend:** Java, Spring Boot, Spring Security (JWT)
- **Frontend:** React, TypeScript, Nginx (Dockerized)
- **Database:** PostgreSQL (State management & deployment tracking)
- **Message Broker:** Redis (Decoupling services & build queues)
- **Storage:** Cloudflare R2 (S3-compatible, zero egress fees)
- **Orchestration:** Kubernetes (Minikube), Docker

## 🚀 How to Run Locally

### 1. Prerequisites
- Docker & Minikube installed
- Windows OS (for `hosts` file DNS routing)
- Cloudflare R2 Credentials in `k8s/secrets.yaml`

### 2. Build the Docker Images
```bash
docker build -t build-service:1.0 -f build-service/Dockerfile .
docker build -t request-handler:1.0 -f request-handler/Dockerfile .
docker build -t upload-service:1.0 -f upload-service/Dockerfile .
docker build -t frontend:1.0 -f frontend/Dockerfile .
```

### 3. Deploy to Kubernetes
```bash
# This applies all namespaces, secrets, Postgres, Redis, and Services
.\deploy-k8s.bat
```

### 4. Setup Local DNS Routing
To test the wildcard subdomains locally, open Notepad as Administrator and edit `C:\Windows\System32\drivers\etc\hosts`:
```text
127.0.0.1  "your-generated-id".vercel-clone.com
```

### 5. Port Forwarding
Keep these running in separate terminals to access the APIs and UI:
```bash
kubectl port-forward -n vercel-clone svc/upload-service 8081:8081
kubectl port-forward -n vercel-clone svc/frontend 3000:80
```
Then visit `http://localhost:3000` to start deploying!

---
*Built to deeply understand distributed systems, multi-threading locks, and container orchestration.*
