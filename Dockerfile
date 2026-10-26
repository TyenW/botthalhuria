# ---------- build ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
COPY server-template.json .
RUN mvn -q -B package

# ---------- runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
# Fontes para o cartão de boas-vindas (Java2D precisa de fontes instaladas na imagem)
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 1001 bot && mkdir -p data backups templates && chown -R bot /app
COPY --from=build /app/target/botthalhuria-1.0.0.jar bot.jar
COPY server-template.json server-template.schema.json ./
USER bot

ENV TEMPLATE_PATH=server-template.json \
    DATABASE_PATH=data/server_map.db \
    TEMPLATES_DIR=templates \
    BACKUPS_DIR=backups \
    JAVA_OPTS="-Xms128m -Xmx512m -Djava.awt.headless=true"

VOLUME ["/app/data", "/app/backups", "/app/templates"]
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar bot.jar"]
