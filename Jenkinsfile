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
                    // Stop and remove existing containers
                    sh '''
                        docker ps -q --filter name=portal-app- | xargs -r docker stop
                        docker ps -aq --filter name=portal-app- | xargs -r docker rm
                    '''

                    // Deploy new containers
                    sh """
                        docker run -d \
                            --name portal-app-1 \
                            --network cicd_network \
                            -e NODE_ENV=production \
                            ${DOCKER_IMAGE}:${DOCKER_TAG}

                        docker run -d \
                            --name portal-app-2 \
                            --network cicd_network \
                            -e NODE_ENV=production \
                            ${DOCKER_IMAGE}:${DOCKER_TAG}
                    """
                }
            }
        }

        stage('Update Nginx') {
            steps {
                script {
                    // Update Nginx configuration for load balancing
                    writeFile file: '/etc/nginx/conf.d/default.conf', text: '''
                        upstream portal_backend {
                            server portal-app-1:3000;
                            server portal-app-2:3000;
                        }

                        server {
                            listen 80;
                            server_name localhost;

                            location / {
                                proxy_pass http://portal_backend;
                                proxy_set_header Host $host;
                                proxy_set_header X-Real-IP $remote_addr;
                                proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                            }
                        }
                    '''

                    // Reload Nginx configuration
                    sh 'docker exec nginx nginx -s reload'
                }
            }
        }
    }

    post {
        failure {
            // Rollback on failure
            script {
                sh '''
                    docker ps -q --filter name=portal-app- | xargs -r docker stop
                    docker ps -aq --filter name=portal-app- | xargs -r docker rm
                    docker images ${DOCKER_IMAGE}:${DOCKER_TAG} -q | xargs -r docker rmi -f
                '''
            }
        }
    }
}