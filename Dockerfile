FROM nginx:alpine

# Copy the static content to Nginx's default serving directory
COPY index.html /usr/share/nginx/html/

# Expose port 3000 to match our configuration
EXPOSE 3000

# Configure nginx to listen on port 3000
RUN echo 'server { \
    listen 3000; \
    location / { \
        root /usr/share/nginx/html; \
        index index.html; \
    } \
    location /health { \
        access_log off; \
        add_header Content-Type text/plain; \
        return 200 "OK"; \
    } \
}' > /etc/nginx/conf.d/default.conf
