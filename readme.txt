====================================================================
🚀 VERCEL-CLONE DEPLOYMENT CHEAT SHEET (AKA "How to not break things")
====================================================================

--- STEP 1: WAKING UP THE KUBERNETES BEAST ---
minikube version
minikube start --driver=docker
minikube status
kubectl cluster-info
kubectl get-node

# Force your local terminal's Docker to talk to Minikube's internal Docker 
minikube docker-env
& minikube -p minikube docker-env --shell powershell | Invoke-Expression

--- STEP 2: THE FACTORY LINE (BUILD & PUSH IMAGES) ---
cd C:\App\vercel-clone-main

# 1. Build the images (Bake the cake)
docker build -t build-service:1.0 -f build-service/Dockerfile .
docker build -t request-handler:1.0 -f request-handler/Dockerfile .
docker build -t upload-service:1.0 -f upload-service/Dockerfile .
docker build -t frontend:1.0 -f frontend/Dockerfile .

# 2. Tag the images (Put your name on the lunchbox)
docker tag build-service:1.0 filoraxx/build-service:1.0
docker tag upload-service:1.0 filoraxx/upload-service:1.0
docker tag request-handler:1.0 filoraxx/request-handler:1.0
docker tag frontend:1.0 filoraxx/frontend:1.0

# 3. Push to DockerHub (Send it to the cloud!)
docker push filoraxx/build-service:1.0
docker push filoraxx/upload-service:1.0
docker push filoraxx/request-handler:1.0
docker push filoraxx/frontend:1.0

--- STEP 3: RELEASE THE KRAKEN (DEPLOYMENT) ---
# Run the magic bat file that sets up the entire universe (Redis, Postgres, APIs)
.\deploy-k8s.bat

--- STEP 4: OPENING THE GATES (PORT FORWARDING) ---
# Keep these running in separate terminal tabs so you can access them!
kubectl port-forward -n vercel-clone svc/upload-service 8081:8081
kubectl port-forward -n vercel-clone svc/frontend 3000:80

# If you want to tunnel the frontend to the public internet
npx localtunnel --port 3000 2>&1

--- STEP 5: THE DNS HACK (ACCESSING YOUR BUILDS) ---
# Once deployment finishes, you need to trick your PC into routing the custom domain locally:
# 1. Open Notepad as Administrator
# 2. Open: "C:\Windows\System32\drivers\etc\hosts"
# 3. Add this line at the bottom: 
127.0.0.1  "your-id".vercel-clone.com   # (e.g. 127.0.0.1 d1e7d741.vercel-clone.com)
# 4. Save! Now the auto-generated project link will actually open on your machine.

--- STEP 6: THE WATCHTOWER (MONITORING & LOGS) ---
docker images                          # What do we have locally?
kubectl get svc -n vercel-clone        # Are the services open?
kubectl get pods -n vercel-clone       # Are the pods breathing?

# Resource Usage (How hungry are the pods and the cluster?)
# Note: run 'minikube addons enable metrics-server' first if this fails 
kubectl top pods -n vercel-clone
kubectl top nodes

# Spying on the backend logs (Very useful for debugging builds!)
kubectl logs -n vercel-clone deployment/upload-service
kubectl logs -n vercel-clone deployment/build-service
kubectl logs -n vercel-clone deployment/request-handler

--- STEP 7: THE BIG RED BUTTONS (DESTRUCTION & RESTARTS) ---
# "Did you try turning it off and on again?" (Restarts pods but keeps your data/config!)
kubectl scale deployment --all --replicas=0 -n vercel-clone
kubectl scale deployment --all --replicas=1 -n vercel-clone

# The Nuke: Destroys EVERYTHING (You'll need to run deploy-k8s.bat to start over)
kubectl delete namespace vercel-clone

# The Local Sniper: Kill rogue Java processes locking up your ports, then quickly recompile
Get-Process -Name java -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "Killing Java PID $($_.Id)"; Stop-Process -Id $_.Id -Force }; Start-Sleep -Seconds 2; mvn clean package -pl build-service -am -DskipTests