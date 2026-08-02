
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copia apenas o pom.xml primeiro para aproveitar o cache de camadas do Docker
COPY pom.xml .
RUN mvn dependency:go-offline

# Copia o código fonte e compila o .jar (ignorando testes para ser mais rápido)
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# Estágio 2: Runtime (Execução)
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Define o fuso horário da aplicação (opcional, mas recomendado para logs)
ENV TZ=America/Sao_Paulo

# Copia o .jar gerado no estágio 1 para a imagem final
COPY --from=build /app/target/bot-repasse-0.0.1-SNAPSHOT.jar app.jar

# Expõe a porta do Spring Boot (útil para debug futuro)
EXPOSE 8080

# Comando de inicialização
ENTRYPOINT ["java", "-jar", "app.jar"]