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
                    sh "docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} ."
                }
            }
        }

        stage('Test') {
            steps {
                script {
                    sh "docker run --rm ${DOCKER_IMAGE}:${DOCKER_TAG} npm test"
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

                    // Stop and remove existing containers
                    sh '''
                        containers=$(docker ps -q --filter name=portal-app-)
                        if [ ! -z "$containers" ]; then
                            docker stop $containers || true
                            docker rm $containers || true
                        fi
                    '''

                    // Deploy new containers
                    sh """
                        docker run -d \
                            --name portal-app-1 \
                            --network cicd_network \
                            -e NODE_ENV=production \
                            -p 3001:3000 \
                            ${DOCKER_IMAGE}:${DOCKER_TAG}

                        docker run -d \
                            --name portal-app-2 \
                            --network cicd_network \
                            -e NODE_ENV=production \
                            -p 3002:3000 \
                            ${DOCKER_IMAGE}:${DOCKER_TAG}
                    """
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
        failure {
            // Rollback on failure
            script {
                sh '''
                    echo "Deployment failed, rolling back..."
                    containers=$(docker ps -q --filter name=portal-app-)
                    if [ ! -z "$containers" ]; then
                        docker stop $containers || true
                        docker rm $containers || true
                    fi
                    docker images ${DOCKER_IMAGE}:${DOCKER_TAG} -q | xargs -r docker rmi -f
                '''
            }
        }
        always {
            // Clean workspace
            cleanWs()
        }
    }
}