# ==========================================
# STAGE 1: BUILDER (Si prende la RAM, ma poi viene buttato)
# ==========================================
FROM eclipse-temurin:21-jdk AS builder

# 1. Installiamo SBT (identico al tuo script originale)
ENV SBT_VERSION=1.9.9
RUN apt-get update && \
    apt-get install -y curl && \
    curl -L -o sbt-$SBT_VERSION.deb https://repo.scala-sbt.org/scalasbt/debian/sbt-$SBT_VERSION.deb && \
    dpkg -i sbt-$SBT_VERSION.deb && \
    rm sbt-$SBT_VERSION.deb && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 2. OTTIMIZZAZIONE CACHE:
# Copiamo solo i file di configurazione prima.
# Se questi non cambiano, Docker non riscarica le librerie (Internet risparmiato + Build veloce)
COPY build.sbt .
COPY project/ project/

# Scarica le dipendenze e prepara il compilatore
RUN sbt update

# 3. Ora copiamo il codice sorgente vero e proprio
COPY . .

# 4. Creiamo il Fat JAR.
# Questo creerà un file grosso in /app/target/scala-3.3.7/drone-tracking-service-assembly-0.1.0-SNAPSHOT.jar
RUN sbt assembly

# ==========================================
# STAGE 2: RUNNER (Leggerissimo e Veloce)
# ==========================================
# Usiamo JRE: pesa molto meno del JDK perché non ha il compilatore
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copiamo DAL builder solo il file .jar finale.
# Il resto (sbt, sorgenti, cache) viene eliminato.
COPY --from=builder /app/target/scala-*/*-assembly-*.jar app.jar

# Configurazione RAM: Http4s è leggero. 
# 256MB sono sufficienti per partire, max 512MB per sicurezza.
ENV JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8080

# Avvio diretto: niente 'sbt run'. Parte in < 2 secondi.
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]