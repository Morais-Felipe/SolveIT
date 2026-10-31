# Imagem da API em duas etapas (plano 07-01 D-7). A primeira tem JDK e Maven e só
# compila; a segunda recebe apenas o .jar e roda com o JRE, sem ferramentas de build.

FROM eclipse-temurin:17.0.20_8-jdk-noble AS build
WORKDIR /build

# Dependências primeiro: enquanto o pom.xml não mudar, o Docker reaproveita esta camada
# e não baixa tudo de novo a cada alteração no código.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Num clone no Windows o mvnw e o .properties do wrapper podem vir com fim de linha CRLF:
# o Linux não executa o script, e o hash SHA-256 lido do .properties ganha um "\r" que
# nunca confere com o download (o wrapper recusa a distribuição). Normalizamos os dois.
RUN sed -i 's/\r$//' mvnw .mvn/wrapper/maven-wrapper.properties \
    && chmod +x mvnw \
    && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
# Os testes rodam no CI e localmente com Testcontainers; aqui só empacotamos.
RUN ./mvnw -B -ntp -DskipTests package && cp target/solveit-api-*.jar /build/app.jar


FROM eclipse-temurin:17.0.20_8-jre-noble
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home solveit
COPY --from=build /build/app.jar app.jar
USER solveit

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
