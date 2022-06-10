# github-alerts-kotlin

This repository contains an example implementation of the subscription microservice (Ktor flavour) described in 'GitHub Notification Service for Slack
'. Please see that document for the specification and requirements.

### 📘 Quick Start

Generate an assembly of the application

```shell
./gradlew assemble
```

Publish the docker image locally

```shell
./gradlew jibDockerBuild
```

Start up services (Postgres, Kafka, Schema Registry, etc.) with the app

```shell
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose-app.yml up -d
```

You can ping the service with

```shell
curl --request GET 'http://localhost:8080/ping'
```
```text
pong
```

When you're done

```shell
docker-compose down --remove-orphans
```

to shut down the services.