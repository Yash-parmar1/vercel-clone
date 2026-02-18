minikube version

minikube start --driver=docker

minikube status

kubectl cluster-info

kubectl get-node

minikube docker-env
 & minikube -p minikube docker-env --shell powershell | Invoke-Expression

docker images

cd C:\App\vercel-clone-main

docker build -t build-service:1.0 -f build-service/Dockerfile .

docker build -t request-handler:1.0 -f request-handler/Dockerfile .

docker build -t upload-service:1.0 -f upload-service/Dockerfile .

docker build -t frontend:1.0 -f frontend/Dockerfile .

docker tag build-service:1.0 filoraxx/build-service:1.0

docker push filoraxx/build-service:1.0

docker tag upload-service:1.0 filoraxx/upload-service:1.0
docker push filoraxx/upload-service:1.0

docker tag request-handler:1.0 filoraxx/request-handler:1.0
docker push filoraxx/request-handler:1.0

docker tag frontend:1.0 filoraxx/frontend:1.0
docker push filoraxx/frontend:1.0


.\deploy-k8s.bat


