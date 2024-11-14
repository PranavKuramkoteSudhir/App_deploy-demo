pipeline {
    agent any

    environment {
        DOCKER_IMAGE = 'portal-app'
        DOCKER_TAG = "${BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', 
                    credentialsId: 'github_access_token',
                    url: 'https://github.com/PranavKuramkoteSudhir/App_deploy-demo.git'
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    sh """
                        docker build \
                            --build-arg NODE_ENV=production \
                            -t ${DOCKER_IMAGE}:${DOCKER_TAG} \
                            -t ${DOCKER_IMAGE}:latest .
                    """
                }
            }
        }

        stage('Test') {
            steps {
                script {
                    try {
                        sh "docker run --rm ${DOCKER_IMAGE}:${DOCKER_TAG} npm test"
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error "Tests failed: ${e.message}"
                    }
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    // Create network if it doesn't exist
                    sh '''
                        docker network inspect cicd_network >/dev/null 2>&1 || \
                        docker network create cicd_network
                    '''

                    // Stop and remove existing containers with error handling
                    sh '''
                        containers=$(docker ps -q --filter name=portal-app-)
                        if [ ! -z "$containers" ]; then
                            docker stop $containers || true
                            docker rm $containers || true
                        fi
                    '''

                    // Deploy new containers with health check
                    sh """
                        docker run -d \
                            --name portal-app-1 \
                            --network cicd_network \
                            -e NODE_ENV=production \
                            -p 3001:3000 \
                            --restart unless-stopped \
                            --health-cmd="curl -f http://localhost:3000/health || exit 1" \
                            --health-interval=30s \
                            --health-timeout=10s \
                            --health-retries=3 \
                            ${DOCKER_IMAGE}:${DOCKER_TAG}

                        docker run -d \
                            --name portal-app-2 \
                            --network cicd_network \
                            -e NODE_ENV=production \
                            -p 3002:3000 \
                            --restart unless-stopped \
                            --health-cmd="curl -f http://localhost:3000/health || exit 1" \
                            --health-interval=30s \
                            --health-timeout=10s \
                            --health-retries=3 \
                            ${DOCKER_IMAGE}:${DOCKER_TAG}
                    """

                    // Wait for containers to be healthy
                    sh '''
                        timeout=60
                        while [ $timeout -gt 0 ]; do
                            if docker ps --filter name=portal-app-1 --filter health=healthy -q && \
                               docker ps --filter name=portal-app-2 --filter health=healthy -q; then
                                echo "All containers are healthy"
                                exit 0
                            fi
                            sleep 5
                            timeout=$((timeout - 5))
                        done
                        echo "Timeout waiting for containers to be healthy"
                        exit 1
                    '''
                }
            }
        }

        stage('Update Nginx') {
            steps {
                script {
                    // Ensure nginx config directory exists
                    sh 'mkdir -p /etc/nginx/conf.d'

                    // Update Nginx configuration for load balancing
                    writeFile file: '/etc/nginx/conf.d/default.conf', text: '''
                        upstream portal_backend {
                            server portal-app-1:3000;
                            server portal-app-2:3000;
                            keepalive 32;
                        }

                        server {
                            listen 80;
                            server_name localhost;

                            # Health check endpoint
                            location /health {
                                access_log off;
                                return 200 'OK';
                                add_header Content-Type text/plain;
                            }

                            location / {
                                proxy_pass http://portal_backend;
                                proxy_http_version 1.1;
                                proxy_set_header Upgrade $http_upgrade;
                                proxy_set_header Connection 'upgrade';
                                proxy_set_header Host $host;
                                proxy_set_header X-Real-IP $remote_addr;
                                proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                                proxy_cache_bypass $http_upgrade;
                                proxy_buffering off;
                                
                                # Timeouts
                                proxy_connect_timeout 60s;
                                proxy_send_timeout 60s;
                                proxy_read_timeout 60s;
                                
                                # Health check
                                proxy_next_upstream error timeout http_500 http_502 http_503 http_504;
                                proxy_next_upstream_tries 3;
                            }
                        }
                    '''

                    // Test and reload Nginx configuration
                    sh '''
                        docker exec nginx nginx -t && \
                        docker exec nginx nginx -s reload
                    '''
                }
            }
        }
    }

    post {
        success {
            script {
                // Clean up old images
                sh '''
                    docker image prune -f
                    # Keep only last 3 versions of our image
                    docker images ${DOCKER_IMAGE} -q | tail -n +4 | xargs -r docker rmi -f || true
                '''
            }
        }
        failure {
            script {
                echo "Deployment failed, rolling back..."
                sh '''
                    containers=$(docker ps -q --filter name=portal-app-)
                    if [ ! -z "$containers" ]; then
                        docker stop $containers || true
                        docker rm $containers || true
                    fi
                    
                    # Try to restore previous version if it exists
                    if docker image inspect ${DOCKER_IMAGE}:latest >/dev/null 2>&1; then
                        docker run -d \
                            --name portal-app-1 \
                            --network cicd_network \
                            --restart unless-stopped \
                            -e NODE_ENV=production \
                            -p 3001:3000 \
                            ${DOCKER_IMAGE}:latest

                        docker run -d \
                            --name portal-app-2 \
                            --network cicd_network \
                            --restart unless-stopped \
                            -e NODE_ENV=production \
                            -p 3002:3000 \
                            ${DOCKER_IMAGE}:latest
                    fi

                    # Clean up failed build
                    docker images ${DOCKER_IMAGE}:${DOCKER_TAG} -q | xargs -r docker rmi -f || true
                '''
            }
        }
        always {
            // Clean workspace
            cleanWs()
        }
    }
}