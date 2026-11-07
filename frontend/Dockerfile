# Image của web: Nginx trả bản build React và chuyển các đường dẫn của backend sang backend.
# Build ở gốc repo (compose.yaml đặt context: .) vì image cần cả frontend/ và docker/nginx/.
# Image gốc ghim dạng tag@sha256:digest, lý do ghi ở đầu compose.yaml.

# Bước 1: build React bằng Node 24.
FROM node:24.22.1-alpine@sha256:06d7adf3e0ef61a13f1d28682726366f97a15aa1888c934d3ecec86c33918b53 AS build
WORKDIR /build
# Chép package*.json và cài thư viện trước khi chép code: lần sau chỉ sửa code thì Docker
# dùng lại lớp đã cài thư viện, không chạy lại npm ci.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Bước 2: chỉ giữ file tĩnh đã build, chạy bằng Nginx.
FROM nginx:1.30.6-alpine@sha256:583b7f2fce1cdb8da5ad5c39ebd3c33b98b87d493190e548bdee1fa8c619c311
COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80