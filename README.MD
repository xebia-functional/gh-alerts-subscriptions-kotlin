# github-alerts-kotlin

This repository contains an example implementation of the subscription microservice (Ktor flavour) described in 'GitHub Notification Service for Slack
'. Please see that document for the specification and requirements.

### 📘 Quick Start

Generate an assembly of the application

```
./gradlew assemble
```

Publish the docker image locally

```
./gradlew jibDockerBuild
```

Start up services (Postgres, Kafka, Schema Registry, etc.) with the app

```
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose-app.yml up -d
```

When you're done

```
docker-compose down --remove-orphans
```

to shut down the services.