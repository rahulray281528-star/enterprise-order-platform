# Running on Kubernetes

Kubernetes is **not required** to run this project. `docker compose up --build` is the
supported local path. These manifests exist to show how the platform would be deployed
and to be reviewable as part of the design.

## Prerequisites

- Minikube or Kind
- `kubectl`
- 8 GB of RAM available to the cluster

## Local deployment (Minikube)

```bash
minikube start --cpus=4 --memory=8192
minikube addons enable ingress

# Build images inside Minikube's Docker daemon so no registry is needed
eval $(minikube docker-env)
for svc in service-discovery config-server api-gateway auth-service \
           product-service inventory-service order-service \
           payment-service notification-service; do
  docker build -f $svc/Dockerfile -t enterprise/$svc:latest .
done

kubectl apply -f kubernetes/00-namespace.yaml

# Create the real secret rather than applying the example file
kubectl -n enterprise-platform create secret generic platform-secrets \
  --from-literal=POSTGRES_USER=postgres \
  --from-literal=POSTGRES_PASSWORD="$(openssl rand -base64 24)" \
  --from-literal=JWT_SECRET="$(openssl rand -base64 48)"

kubectl apply -f kubernetes/01-configmap.yaml
kubectl apply -f kubernetes/05-infrastructure.yaml
kubectl -n enterprise-platform rollout status deploy/postgres

kubectl apply -f kubernetes/03-deployments.yaml
kubectl apply -f kubernetes/04-ingress.yaml
```

## Verify

```bash
kubectl -n enterprise-platform get pods -w
kubectl -n enterprise-platform get svc

echo "$(minikube ip) platform.local" | sudo tee -a /etc/hosts
curl http://platform.local/actuator/health
```

## Notes on these manifests

- **Only `api-gateway` is exposed.** Every other service is `ClusterIP`, reachable only
  from inside the cluster. That mirrors the Compose topology and the security model:
  one front door.
- **Liveness and readiness probes are different endpoints on purpose.** Readiness takes
  a pod out of the Service while it waits for PostgreSQL or Kafka; liveness restarts a
  pod that is genuinely wedged. Using one probe for both causes restart loops during
  slow dependency startup.
- **`initialDelaySeconds` is generous** (90 s liveness) because a Spring Boot service
  with JPA, Liquibase and Kafka takes 30–60 s to become ready on a laptop.
- **PostgreSQL, Redis and Kafka run as single-replica Deployments** here. That is
  acceptable for Minikube and wrong for production, where they are managed services —
  see [`../docs/aws-architecture.md`](../docs/aws-architecture.md).
- **`02-secrets.example.yaml` is an example.** Real clusters should source secrets from
  AWS Secrets Manager or Vault through the External Secrets Operator, so secrets never
  exist as a manifest at all.

## Tear down

```bash
kubectl delete namespace enterprise-platform
minikube stop
```
