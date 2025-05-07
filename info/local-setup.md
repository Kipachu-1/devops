
# Kubernetes Local Development

## Overview

This guide provides step-by-step instructions for setting up a local Kubernetes development environment for the project using **Minikube** and **Istio**.
---

## Prerequisites

* Docker or a compatible container runtime
* Kubectl
* Minikube
* Sufficient system resources (recommended: 8GB RAM, 4 CPUs)

---

## 1. Minikube Installation and Setup

### Install Minikube

Minikube is a tool that allows you to run Kubernetes locally. Installation methods vary by operating system:

**MacOS:**

```bash
brew install minikube
```

**Windows:**

```bash
choco install minikube
```

**Linux:**

```bash
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube
```

### Start Minikube

```bash
minikube start --memory=8192mb --cpus=4
```

This starts Minikube with recommended resources for Istio and complex applications.

---

## 2. Istio Setup

### Enable Istio Addons

```bash
minikube addons enable istio-provisioner
minikube addons enable istio
```

### Verify Istio Installation

```bash
kubectl get po -n istio-system
```

### Configure Istio Injection

```bash
kubectl label namespace default istio-injection=enabled
```

---

## 3. General Configuration

### Clone the Ops-Config Project

Use the certain branch of the project:
```bash
just checkout the branch `{{branch-name}}`
```


### Apply Environment Variables

```bash
kubectl apply -f ./kuber/environments/dev/configs/vars.yaml
```

> **Note:** These are environment variables used by applications.

---

## 4. Database Setup for PostgreSQL

### 1. Deploy PostgreSQL

```bash
kubectl apply -f ./base/database/postgres/
```

### 2. Verify Database Deployment

```bash
kubectl get pods # Ensure PostgreSQL pod is running
kubectl get svc # Verify postgres-service
kubectl get pv # Ensure PersistentVolume is Bound
```

The output should show the `STATUS` as **Bound** for the PersistentVolume.

### 3. Expose and Connect to the Database

Expose the service using Minikube:

```bash
minikube service postgres-service --url
```

> **Note:** The provided URL will be used to connect to PostgreSQL.

### 4. Access Database Using a Client (pgAdmin)

In **pgAdmin** (or another PostgreSQL client):

* **Host:** Use the Minikube service URL (without the port number)
* **Port:** Use the port from the Minikube URL
* **Username & Password:** Retrieve from `./base/database/postgres/configmap.yaml`

Create the following databases: 

# if needed
* `some-db`

### 5. Confirm Database Creation

In **pgAdmin**, refresh the database list to verify that `local-core-db` and `local-content-db` were successfully created.

---

## 5. Application Deployment

### Build Docker Images

| Repository                | Docker Image Name    |
| ------------------------- | -------------------- |
| frontend                  |  frontend-image      |
| backend                   |  backend-image       |

For each repository:

1. Clone the repository.
2. In the root folder, run:

```bash
docker build . -t {docker-image-name}
```

### Load Images to Minikube

```bash
minikube image load frontend-image 
minikube image load backend-image 
# Repeat for all images
```

### Deploy Applications

```bash
kubectl apply -f ./kuber/environments/dev/apps/
```

### Verify Application Status

```bash
kubectl get pods
# Expect status: Running, Ready 2/2
```

### Expose Services via Istio

```bash
kubectl apply -f ./kuber/environments/dev/istio/**.yaml
```

---

## 6. Local Development Workflow

### Updating Deployed Images

1. **Build new image:**

```bash
docker build . -t {docker-image-name}
```

2. **Load image to Minikube:**

```bash
minikube image load {docker-image-name}
```

3. **Delete existing pod to trigger redeployment:**

```bash
kubectl delete pod {pod-name}
# Example:
kubectl delete pod frontend-deployment-84d458588b-8h7hh
```

> **Tip:** Command to get pods:

```bash
kubectl get pods
```

---

## 7. Host Configuration

Add these entries to your `/etc/hosts` (or `C:\Windows\System32\drivers\etc\hosts` on Windows):

```
127.0.0.1       backend.com
127.0.0.1       frontend.com
```

---

## 8. Final Step

Run:

```bash
minikube tunnel
```

---

## Accessing Applications

* [http://frontend.com](http://frontend.com)
* [http://backend.com](http://backend.com)

---

## Troubleshooting

* Ensure all prerequisites are installed
* Check Minikube and Kubernetes version compatibility
* Verify sufficient system resources
* Review Kubernetes and Istio logs for detailed error information

---