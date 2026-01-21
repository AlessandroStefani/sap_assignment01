# === STAGE 1: Build & Cache (Pesante) ===
FROM sbtscala/scala-sbt:eclipse-temurin-jammy-17.0.10_7_1.9.8_3.3.1
WORKDIR /app

# 1. Copiamo SOLO i file di definizione delle dipendenze
# Questo crea un layer cachato. Se build.sbt non cambia, questo step è istantaneo.
COPY build.sbt .
COPY project/ project/

# 2. Scarichiamo le dipendenze (Internet)
# Anche questo viene cachato se i file sopra non cambiano.
RUN sbt update

# 3. Copiamo il resto del codice sorgente
# Questo è l'unico layer che viene invalidato spesso.
COPY . .

# 4. Compilazione
RUN sbt compile

# === STAGE 2: Runtime ===
# Aggiungiamo l'EXPOSE per documentazione e correttezza
EXPOSE 9067

CMD ["sbt", "run"]