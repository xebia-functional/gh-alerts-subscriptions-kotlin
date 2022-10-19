# github-alerts-kotlin

This repository contains an example implementation of the subscription microservice (Ktor flavour) described in 'GitHub Notification Service for Slack
'. Please see that document for the specification and requirements.

### 📘 Quick Start

#### Local Development

To run the project locally the easiest is to:
  1. Run Postgres and Kafka through `docker-compose`
  2. Start the application through Gradle

```shell
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d
./gradlew run
```

NOTE: Gradle does not respect propagating SIGTERM to the running application, so graceful shutdown is not respecting when running this way.

##### Running the application in Docker

Publish the docker image locally

```shell
./gradlew publishImageToLocalRegistry
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

When you're done, stop the services.

```shell
docker-compose down --remove-orphans
```

### 📖 Design decisions

- [Env / Configuration](EnviromentConfiguration.MD)
- [Dependency Injection](DependencyInjection.MD)
