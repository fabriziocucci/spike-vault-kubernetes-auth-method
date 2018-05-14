# Spike: Vault's Kubernetes Auth Method

## Description

In this spike, we are going to see how to authenticate a Spring Boot microservice with Vault using a Kubernetes Service Account Token.

The scenario is the following:
* we have a Kubernetes cluster where our Spring Boot microservices are deployed;
* we have a Vault instance outside of the Kubernetes cluster containing secrets;
* we want our Spring Boot microservices to authenticate with Vault and access those secrets.

## Environment

OS:
* macOS High Sierra

Tools:
* VirtualBox 5.2.10r122088
* Minikube 0.25.2
* Vault 0.10.1
* Docker 18.03.1-ce

## Showtime

### Start and configure Kubernetes

Start a local kubernetes cluster:

```bash
minikube start
```

Create a Kubernetes service account:

```bash
kubectl apply -f etc/kubernetes/vault-auth-serviceaccount.yaml
```

### Start and configure Vault

Start Vault in development mode (from another terminal since we are attaching to the container):

```bash
docker run -it --rm -p 8200:8200 --cap-add=IPC_LOCK -e 'VAULT_DEV_ROOT_TOKEN_ID=00000000-0000-0000-0000-000000000000' --name=dev-vault vault:0.9.6
```

Configure the Vault client to talk the running Vault instance:

```bash
export VAULT_ADDR='http://0.0.0.0:8200'
export VAULT_TOKEN='00000000-0000-0000-0000-000000000000'
```

Enable and configure Vault's Kubernetes auth method (as explained in the [official documentation](https://www.vaultproject.io/docs/auth/kubernetes.html)):

```
export VAULT_AUTH_SA_SECRET_NAME=$(kubectl get sa vault-auth -o jsonpath="{.secrets[0]['name']}")
export VAULT_AUTH_SA_SECRET_TOKEN=$(kubectl get secret $VAULT_AUTH_SA_SECRET_NAME -o jsonpath="{.data.token}" | base64 --decode)
export VAULT_AUTH_SA_SECRET_CA_CRT=$(kubectl get secret $VAULT_AUTH_SA_SECRET_NAME -o jsonpath="{.data.ca\.crt}" | base64 --decode)
export KUBERNETES_HOST=$(minikube ip)

vault auth enable kubernetes

vault write auth/kubernetes/config \
    token_reviewer_jwt="$VAULT_AUTH_SA_SECRET_TOKEN" \
    kubernetes_host="https://$KUBERNETES_HOST:8443" \
    kubernetes_ca_cert="$VAULT_AUTH_SA_SECRET_CA_CRT"

vault policy write vault-auth-policy etc/vault/vault-auth-policy.hcl

vault write auth/kubernetes/role/my-role \
    bound_service_account_names=vault-auth \
    bound_service_account_namespaces=default \
    policies=vault-auth-policy \
    ttl=1h
```

Our Spring Boot application is supposed to have access to the `password` secret stored in Vault, so let's create it:

```bash
vault write secret/spike-vault-kubernetes-auth-method password=foo
```

### Build and run the Spring Boot microservice

The Spring Boot microservice requires the host and port of the Vault instance. Because Vault is running outside Minikube, we need to find out the address to set for the property `spring.cloud.vault.host` in the `bootstrap.yaml`. In order to do that, we need to look at the `inet` field of this command:

```bash
ifconfig vboxnet0
vboxnet0: flags=4163<UP,BROADCAST,RUNNING,MULTICAST>  mtu 1500
        inet 192.168.99.1  netmask 255.255.255.0  broadcast 192.168.99.255
        ...
```

Or, alternatively, we could filter the output just like this:

```bash
ifconfig vboxnet0 | grep 'inet ' | awk '{print $2}'
```

We need to build the Docker image of our Spring Boot microservice but we also need to make sure that the image is accessible from Minikube. One way to achieve this is to make the docker commands on our host machine execute against the Docker daemon inside Minikube (as explained [here](https://kubernetes.io/docs/getting-started-guides/minikube/#reusing-the-docker-daemon)):

```bash
eval $(minikube docker-env)
``` 

Build our microservice and tag the corresponding Docker image:

```bash
./gradlew clean build
docker build -t spike-vault-kubernetes-auth-method:v1 .
```

Run our microservice inside Minikube:

```
kubectl run spike-vault-kubernetes-auth-method --serviceaccount=vault-auth --image=spike-vault-kubernetes-auth-method:v1 --port=8080
kubectl expose deployment spike-vault-kubernetes-auth-method --type=NodePort
```

Test that our microservice can access the secret stored in Vault:

```
curl $(minikube service spike-vault-kubernetes-auth-method --url)/password
```

### Clean-up

To restore the Docker environment variables:

```bash
eval $(minikube docker-env -u)
```

To remove the Kubernetes deployment and service:

```bash
kubectl delete deploy/spike-spring-cloud-vault
kubectl delete svc/spike-spring-cloud-vault 
```

To delete the local minikube cluster:

```bash
minikube delete
```

## References

* [Kubernetes Auth Method](https://www.vaultproject.io/docs/auth/kubernetes.html)
* [Spring Cloud Vault](http://cloud.spring.io/spring-cloud-vault/1.1.0.RELEASE/single/spring-cloud-vault.html#vault.config.authentication.kubernetes)
* [Dynamic secrets on Kubernetes pods using Vault](https://medium.com/@gmaliar/dynamic-secrets-on-kubernetes-pods-using-vault-35d9094d169)
