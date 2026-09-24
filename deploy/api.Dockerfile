# Code en Clair Academy — image de l'API Spring Boot (Render, offre gratuite : 512 Mo de RAM, 0,1 CPU)
# Construction depuis la racine du dépôt : docker build -f deploy/api.Dockerfile -t cda-api .

# ---- Étape 1 : construction du jar avec Maven ----
FROM eclipse-temurin:21-jdk AS construction
WORKDIR /build
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src src
# Les tests tournent avant le déploiement (en local et dans la CI), pas pendant la construction de l'image.
RUN ./mvnw -B -q package -DskipTests && mv target/*.jar app.jar

# ---- Étape 2 : image d'exécution légère ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app && mkdir /app && chown app:app /app
WORKDIR /app
USER app
COPY --from=construction --chown=app:app /build/app.jar /tmp/app.jar

# Mémoire : 512 Mo au total. Tas limité, ramasse-miettes économe, compilation JIT simplifiée
# pour démarrer plus vite sur un petit processeur. Les mêmes options servent à l'entraînement CDS ci-dessous.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"

# Démarrage accéléré (CDS, Class Data Sharing) : on décompose le jar, puis une exécution
# « d'entraînement » sans base de données enregistre les classes chargées dans application.jsa.
# Les démarrages suivants les relisent au lieu de les recharger une à une.
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app/application && rm /tmp/app.jar \
 && java -XX:ArchiveClassesAtExit=/app/application.jsa -Dspring.context.exit=onRefresh \
      -Dspring.profiles.active=prod \
      -DDB_URL=jdbc:postgresql://127.0.0.1:1/entrainement -DDB_USERNAME=x -DDB_PASSWORD=x \
      -DJWT_SECRET=entrainement-cds-sans-valeur-secrete-entrainement-cds-sans-valeur-secrete \
      -DCORS_ALLOWED_ORIGINS=http://localhost \
      -Dspring.flyway.enabled=false -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -jar /app/application/app.jar

# Contenus pédagogiques (parcours, examens) importés au démarrage
COPY --chown=app:app database/content content

ENV SPRING_PROFILES_ACTIVE=prod \
    CONTENT_DIR=/app/content \
    PORT=8090

EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --start-period=300s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${PORT}/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:SharedArchiveFile=/app/application.jsa", "-Xshare:auto", "-jar", "/app/application/app.jar"]
