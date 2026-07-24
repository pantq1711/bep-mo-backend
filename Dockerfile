# ── Build stage ───────────────────────────────────────────────────────────────
# Dùng image có sẵn Maven + JDK 17, tận dụng Docker layer cache: copy pom.xml
# trước để cache dependency, chỉ re-download khi pom.xml đổi (không phải mỗi lần sửa code)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN mvn -B dependency:go-offline

COPY src/ src/
RUN mvn -B clean package -DskipTests

# ── Run stage ─────────────────────────────────────────────────────────────────
# JRE thôi, không cần JDK — image nhỏ hơn nhiều so với dùng chung image build
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Chạy bằng user riêng, không chạy bằng root — giảm rủi ro nếu container bị chiếm quyền
RUN addgroup -S bepmo && adduser -S bepmo -G bepmo
USER bepmo

COPY --from=build /app/target/bep-mo-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
