$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$utf8 = New-Object System.Text.UTF8Encoding $false
function Write-ProjectFile($path, $content) {
  $full = Join-Path $root $path
  [IO.Directory]::CreateDirectory((Split-Path $full -Parent)) | Out-Null
  [IO.File]::WriteAllText($full, $content, $utf8)
}
$services = @('match-generator','betting-api','match-simulator','results-engine','dashboard-backend','email-worker')
foreach ($service in $services) {
  $package = $service.Replace('-','')
  $extra = if ($service -eq 'dashboard-backend') { '<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-websocket</artifactId></dependency>' } else { '' }
  $resources = if ($service -eq 'dashboard-backend') { '<resources><resource><directory>src/main/resources</directory></resource><resource><directory>../frontend</directory><targetPath>static</targetPath></resource></resources>' } else { '' }
  Write-ProjectFile "$service/pom.xml" @"
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><parent><groupId>edu.arena</groupId><artifactId>live-bet-arena</artifactId><version>1.0.0</version></parent><artifactId>$service</artifactId><dependencies><dependency><groupId>edu.arena</groupId><artifactId>arena-common</artifactId><version>1.0.0</version></dependency>$extra<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency></dependencies><build>$resources<plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build></project>
"@
  Write-ProjectFile "$service/src/main/java/edu/arena/$package/Application.java" @"
package edu.arena.$package;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication(scanBasePackages = {"edu.arena.common", "edu.arena.$package"})
@EntityScan("edu.arena.common.persistence")
@EnableScheduling
public class Application {
    public static void main(String[] args) { SpringApplication.run(Application.class, args); }
}
"@
  Write-ProjectFile "$service/src/main/resources/application.yml" @"
spring:
  application:
    name: $service
  config:
    import: classpath:arena.yml
"@
  Write-ProjectFile "$service/Dockerfile" @'
FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY . .
RUN --mount=type=cache,id=arena-maven,target=/root/.m2,sharing=locked mvn -B package
ARG SERVICE
RUN cp ${SERVICE}/target/${SERVICE}-1.0.0.jar /app.jar
FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system arena && useradd --system --gid arena arena && apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder --chown=arena:arena /app.jar app.jar
USER arena
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=90s --retries=5 CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java","-jar","app.jar"]
'@
}
