# Image của backend Spring Boot.
# Build ở gốc repo (compose.yaml đặt context: .) vì image cần cả backend/ và db/migrations/.

# Bước 1: build file jar bằng Maven Wrapper, trong image có đủ JDK 21.
FROM eclipse-temurin:21.0.12_8-jdk-alpine AS build
WORKDIR /build
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
COPY backend/src src
# File chép từ Windows có thể mất quyền chạy, nên cấp lại cho mvnw.
RUN chmod +x mvnw
# Cache thư mục /root/.m2 giữa các lần build để Maven không tải lại thư viện.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

# Bước 2: image để chạy, chỉ cần JRE nên nhẹ hơn.
FROM eclipse-temurin:21.0.12_8-jre-alpine
# Giữ bố cục thư mục giống trong repo: app chạy trong backend/, script Flyway ở ../db/migrations.
# Nhờ vậy đường dẫn tương đối trong application.yml dùng được cả trên máy lẫn trong Docker.
WORKDIR /app/backend
COPY --from=build /build/target/*.jar app.jar
COPY db/migrations /app/db/migrations
# Dữ liệu mẫu giả, chỉ được nạp khi chạy profile demo (compose.yaml bật profile này cho container backend).
COPY db/seeds /app/db/seeds
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]