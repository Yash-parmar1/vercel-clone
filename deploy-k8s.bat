@echo off
REM Script to deploy Vercel Clone to Kubernetes using credentials from .env (Windows)

setlocal enabledelayedexpansion

echo.
echo ==========================================
echo Vercel Clone - Kubernetes Deployment
echo ==========================================
echo.

REM Check if .env exists
if not exist .env (
    echo ERROR: .env file not found!
    echo Please create .env by copying .env.example and filling in your credentials
    exit /b 1
)

REM Parse .env file
for /f "tokens=1,2 delims==" %%a in (.env) do (
    set "%%a=%%b"
)

REM Check required variables
if "!R2_ENDPOINT!"=="" (
    echo ERROR: R2_ENDPOINT is not set in .env
    exit /b 1
)
if "!R2_ACCESS_KEY!"=="" (
    echo ERROR: R2_ACCESS_KEY is not set in .env
    exit /b 1
)
if "!R2_SECRET_KEY!"=="" (
    echo ERROR: R2_SECRET_KEY is not set in .env
    exit /b 1
)
if "!POSTGRES_PASSWORD!"=="" (
    echo ERROR: POSTGRES_PASSWORD is not set in .env
    exit /b 1
)
if "!JWT_SECRET!"=="" (
    echo ERROR: JWT_SECRET is not set in .env
    exit /b 1
)

echo ✓ .env file loaded successfully
echo.

REM Create namespace
echo Creating namespace...
kubectl apply -f k8s/namespace.yaml

REM Create ConfigMap
echo Creating ConfigMap with Docker Hub registry...
kubectl apply -f k8s/configmap.yaml

REM Create R2 Secret from .env variables
echo Creating R2 credentials secret from .env...
kubectl delete secret r2-credentials -n vercel-clone 2>nul
kubectl create secret generic r2-credentials ^
  --from-literal=endpoint="%R2_ENDPOINT%" ^
  --from-literal=access-key="%R2_ACCESS_KEY%" ^
  --from-literal=secret-key="%R2_SECRET_KEY%" ^
  --from-literal=bucket="%R2_BUCKET%" ^
  -n vercel-clone

REM Create Postgres Secret from .env variables
echo Creating Postgres credentials secret from .env...
kubectl delete secret postgres-credentials -n vercel-clone 2>nul
kubectl create secret generic postgres-credentials ^
  --from-literal=username=postgres ^
  --from-literal=password="%POSTGRES_PASSWORD%" ^
  --from-literal=database=vercel_clone ^
  -n vercel-clone

REM Create JWT Secret
echo Creating JWT secret from .env...
kubectl delete secret jwt-secrets -n vercel-clone 2>nul
kubectl create secret generic jwt-secrets ^
  --from-literal=secret="%JWT_SECRET%" ^
  --from-literal=expiration=86400000 ^
  -n vercel-clone

REM Deploy all services
echo.
echo Deploying services...
kubectl apply -f k8s/redis-deployment.yaml
kubectl apply -f k8s/postgres-deployment.yaml
kubectl apply -f k8s/upload-service-deployment.yaml
kubectl apply -f k8s/build-service-deployment.yaml
kubectl apply -f k8s/request-handler-deployment.yaml
kubectl apply -f k8s/hpa.yaml

echo.
echo ==========================================
echo ✓ Deployment Complete!
echo ==========================================
echo.
echo Checking pod status...
kubectl get pods -n vercel-clone

echo.
echo Services:
kubectl get svc -n vercel-clone

echo.
echo To view logs:
echo   kubectl logs -n vercel-clone deployment/upload-service
echo   kubectl logs -n vercel-clone deployment/build-service
echo   kubectl logs -n vercel-clone deployment/request-handler
echo.
echo To access services:
echo   kubectl port-forward -n vercel-clone svc/upload-service 8081:80
echo   kubectl port-forward -n vercel-clone svc/request-handler 8083:80
echo.

endlocal
